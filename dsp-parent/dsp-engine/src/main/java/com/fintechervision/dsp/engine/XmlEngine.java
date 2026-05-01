package com.fintechervision.dsp.engine;

import com.fintechervision.dsp.engine.executor.*;
import com.fintechervision.dsp.engine.model.*;
import com.fintechervision.dsp.engine.parser.XmlConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmlEngine {

    private final XmlConfigParser xmlConfigParser;
    private final SqlExecutor sqlExecutor;
    private final HttpExecutor httpExecutor;
    private final DubboExecutor dubboExecutor;
    private final MongoExecutor mongoExecutor;
    private final PaginationHandler paginationHandler;
    private final DynamicSqlHandler dynamicSqlHandler;
    private final ResultMapper resultMapper;
    private final QueryOrchestrator queryOrchestrator;

    /**
     * 数据源注册回调，由外部服务注入实现。
     * XML 中定义的内联 datasource 可通过此回调动态注册到运行时。
     */
    private DataSourceRegistrar dataSourceRegistrar;

    public void setDataSourceRegistrar(DataSourceRegistrar registrar) {
        this.dataSourceRegistrar = registrar;
    }

    public Object execute(String xmlConfig, Map<String, Object> requestData) {
        InterfaceConfig config = xmlConfigParser.parse(xmlConfig);
        return executeWithConfig(config, requestData);
    }

    /**
     * 使用已解析的 InterfaceConfig 执行查询（跳过XML解析，配合缓存使用）
     */
    public Object executeWithConfig(InterfaceConfig config, Map<String, Object> requestData) {
        log.info("XML解析完成: transno={}, queries={}", config.getTransno(), config.getQueries().size());

        validateParams(config.getRequestData(), requestData);

        // 注册 XML 中定义的内联数据源
        registerInlineDataSources(config.getDataSources());

        OrchestrationContext context = new OrchestrationContext(requestData);

        Map<String, List<Map<String, Object>>> queryResults = queryOrchestrator.orchestrate(
                config.getQueries(),
                query -> executeQueryWithContext(query, context)
        );

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        for (ResultMapConfig resultMap : config.getResultMaps()) {
            List<Map<String, Object>> queryData = queryResults.get(resultMap.getQuery());
            if (queryData != null) {
                Object mapped = resultMapper.mapResult(queryData, resultMap);
                mappedResults.put(resultMap.getId(), mapped);
            }
        }

        // 当没有定义任何 resultMap 时，将查询结果直接放入 mappedResults（key=queryId）
        if (config.getResultMaps().isEmpty()) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : queryResults.entrySet()) {
                List<Map<String, Object>> data = entry.getValue();
                if (data.size() == 1) {
                    mappedResults.put(entry.getKey(), data.get(0));
                } else {
                    mappedResults.put(entry.getKey(), data);
                }
            }
        }

        Object responseData = resultMapper.buildResponse(config.getResponseData(), mappedResults);

        log.info("查询执行完成: transno={}", config.getTransno());
        return responseData;
    }

    private void registerInlineDataSources(List<DataSourceConfig> dataSources) {
        if (dataSourceRegistrar == null || dataSources == null || dataSources.isEmpty()) {
            return;
        }
        for (DataSourceConfig ds : dataSources) {
            try {
                dataSourceRegistrar.register(ds);
                log.debug("数据源校验注册: name={}", ds.getName());
            } catch (Exception e) {
                log.warn("XML内联数据源注册失败: name={}, error={}", ds.getName(), e.getMessage());
            }
        }
    }

    private List<Map<String, Object>> executeQueryWithContext(QueryConfig query,
                                                                OrchestrationContext context) {
        Map<String, Object> previousResults = context.getPreviousResults();
        List<Map<String, Object>> result = executeQuery(query, context.getRequestData(), previousResults);
        context.putPreviousResult(query.getId(), result);
        return result;
    }

    private List<Map<String, Object>> executeQuery(QueryConfig query,
                                                    Map<String, Object> requestData,
                                                    Map<String, Object> previousResults) {
        String type = query.getType().toLowerCase();

        switch (type) {
            case "mysql":
            case "doris":
            case "sql":
            case "oracle":
            case "postgresql":
                return executeSqlQuery(query, requestData, previousResults);
            case "http":
                return executeHttpQuery(query, requestData, previousResults);
            case "dubbo":
                return executeDubboQuery(query, requestData, previousResults);
            case "mongo":
                return executeMongoQuery(query, requestData, previousResults);
            default:
                throw new RuntimeException("不支持的查询类型: " + type);
        }
    }

    private List<Map<String, Object>> executeSqlQuery(QueryConfig query,
                                                       Map<String, Object> requestData,
                                                       Map<String, Object> previousResults) {
        DynamicSqlHandler.SqlResult sqlResult = dynamicSqlHandler.process(
                query.getSql(), query.getDynamicSqls(), requestData, previousResults);

        String finalSql = sqlResult.sql;
        List<Object> finalParams = sqlResult.params;
        if (query.getPaginationConfig() != null) {
            PaginationHandler.PaginationResult pageResult = paginationHandler.rewrite(
                    sqlResult.sql, query.getPaginationConfig(), requestData, sqlResult.params);
            finalSql = pageResult.sql;
            finalParams = pageResult.params;
            if (pageResult.paginated) {
                log.debug("分页改写: mode={}, sql={}", pageResult.mode, finalSql);
            }
        }

        log.debug("最终SQL: datasource={}, sql={}, params={}", query.getDatasource(), finalSql, finalParams);

        if (finalParams.isEmpty()) {
            return sqlExecutor.query(query.getDatasource(), finalSql);
        } else {
            return sqlExecutor.query(query.getDatasource(), finalSql, finalParams.toArray());
        }
    }

    private List<Map<String, Object>> executeHttpQuery(QueryConfig query,
                                                        Map<String, Object> requestData,
                                                        Map<String, Object> previousResults) {
        if (query.getHttpConfig() == null) {
            throw new RuntimeException("HTTP查询缺少<http>配置，queryId=" + query.getId());
        }
        return httpExecutor.execute(query.getHttpConfig(), query.getDatasource(), requestData, previousResults);
    }

    private List<Map<String, Object>> executeDubboQuery(QueryConfig query,
                                                         Map<String, Object> requestData,
                                                         Map<String, Object> previousResults) {
        if (query.getDubboConfig() == null) {
            throw new RuntimeException("Dubbo查询缺少<dubbo>配置，queryId=" + query.getId());
        }
        return dubboExecutor.execute(query.getDubboConfig(), query.getDatasource(), requestData, previousResults);
    }

    private List<Map<String, Object>> executeMongoQuery(QueryConfig query,
                                                          Map<String, Object> requestData,
                                                          Map<String, Object> previousResults) {
        if (query.getMongoConfig() == null) {
            throw new RuntimeException("MongoDB查询缺少<mongo>配置，queryId=" + query.getId());
        }
        return mongoExecutor.execute(query.getMongoConfig(), query.getDatasource(), requestData, previousResults);
    }

    private void validateParams(RequestDataConfig requestDataConfig, Map<String, Object> requestData) {
        if (requestDataConfig == null || requestData == null) {
            return;
        }
        for (ParamConfig param : requestDataConfig.getParams()) {
            if (param.isRequired() && !requestData.containsKey(param.getName())) {
                if (param.getDefaultValue() != null) {
                    continue;
                }
                throw new RuntimeException("必填参数缺失: " + param.getName());
            }
        }
    }

    static class OrchestrationContext {
        private final Map<String, Object> requestData;
        private final Map<String, Object> previousResults = new ConcurrentHashMap<>();

        OrchestrationContext(Map<String, Object> requestData) {
            this.requestData = requestData;
        }

        Map<String, Object> getRequestData() {
            return requestData;
        }

        Map<String, Object> getPreviousResults() {
            return new HashMap<>(previousResults);
        }

        @SuppressWarnings("unchecked")
        void putPreviousResult(String queryId, List<Map<String, Object>> result) {
            if (result.size() == 1) {
                previousResults.put(queryId, result.get(0));
            } else {
                previousResults.put(queryId, result);
            }
        }
    }
}
