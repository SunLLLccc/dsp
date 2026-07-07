package com.sunlc.dsp.engine;

import com.sunlc.dsp.engine.executor.*;
import com.sunlc.dsp.engine.model.*;
import com.sunlc.dsp.engine.parser.XmlConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.sunlc.dsp.engine.model.DebugTrace;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmlEngine {

    private final XmlConfigParser xmlConfigParser;
    private final SqlExecutor sqlExecutor;
    private final HttpExecutor httpExecutor;
    private final DubboExecutor dubboExecutor;
    private final PaginationHandler paginationHandler;
    private final DynamicSqlHandler dynamicSqlHandler;
    private final ResultMapper resultMapper;
    private final QueryOrchestrator queryOrchestrator;

    @Autowired(required = false)
    private MongoExecutor mongoExecutor;

    /**
     * 数据源注册回调，由外部服务注入实现。
     * XML 中定义的内联 datasource 可通过此回调动态注册到运行时。
     */
    private DataSourceRegistrar dataSourceRegistrar;

    public void setDataSourceRegistrar(DataSourceRegistrar registrar) {
        this.dataSourceRegistrar = registrar;
    }

    public Object execute(String xmlConfig, Map<String, Object> requestData) {
        return execute(xmlConfig, requestData, null);
    }

    /**
     * 带调试跟踪的执行入口。debugContext 为 null 时等同于无跟踪的普通执行。
     */
    public Object execute(String xmlConfig, Map<String, Object> requestData, DebugContext debugContext) {
        InterfaceConfig config = xmlConfigParser.parse(xmlConfig);
        return executeWithConfig(config, requestData, debugContext);
    }

    /**
     * 使用已解析的 InterfaceConfig 执行查询（跳过XML解析，配合缓存使用）
     */
    public Object executeWithConfig(InterfaceConfig config, Map<String, Object> requestData) {
        return executeWithConfig(config, requestData, null);
    }

    /**
     * 带调试跟踪的 executeWithConfig。debugContext 为 null 时零开销。
     */
    public Object executeWithConfig(InterfaceConfig config, Map<String, Object> requestData, DebugContext debugContext) {
        log.info("XML解析完成: transno={}, queries={}", config.getTransno(), config.getQueries().size());

        if (debugContext != null) {
            debugContext.setTransno(config.getTransno());
            debugContext.setStartTimeMs(System.currentTimeMillis());
        }

        // 阶段 1: 参数校验
        recordVoidStep(debugContext, "PARAM_VALIDATE", () ->
                validateParams(config.getRequestData(), requestData));

        // 注册 XML 中定义的内联数据源
        registerInlineDataSources(config.getDataSources());

        OrchestrationContext context = new OrchestrationContext(requestData);

        // 阶段 2: 查询执行（DAG 编排）
        Map<String, List<Map<String, Object>>> queryResults = recordStep(debugContext, "QUERY_EXECUTE", () ->
                queryOrchestrator.orchestrate(config.getQueries(),
                        query -> executeQueryWithContext(query, context, debugContext)));

        // 阶段 3: 结果映射
        Map<String, Object> mappedResults = new LinkedHashMap<>();
        recordVoidStep(debugContext, "RESULT_MAP", () -> {
            for (ResultMapConfig resultMap : config.getResultMaps()) {
                List<Map<String, Object>> queryData = queryResults.get(resultMap.getQuery());
                if (queryData != null) {
                    Object mapped = resultMapper.mapResult(queryData, resultMap);
                    mappedResults.put(resultMap.getId(), mapped);
                }
            }
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
        });

        // 阶段 4: 响应构建
        Object responseData = recordStep(debugContext, "RESPONSE_BUILD", () ->
                resultMapper.buildResponse(config.getResponseData(), mappedResults));

        if (debugContext != null) {
            debugContext.setEndTimeMs(System.currentTimeMillis());
            debugContext.setTotalTimeMs(debugContext.getEndTimeMs() - debugContext.getStartTimeMs());
            debugContext.setSuccess(true);
        }

        log.info("查询执行完成: transno={}", config.getTransno());
        return responseData;
    }

    /** 记录有返回值的执行阶段 */
    private <T> T recordStep(DebugContext ctx, String name, Supplier<T> action) {
        if (ctx == null) {
            return action.get();
        }
        long start = System.currentTimeMillis();
        try {
            T result = action.get();
            ctx.addStep(DebugContext.DebugStep.success(name, System.currentTimeMillis() - start));
            return result;
        } catch (Exception e) {
            ctx.addStep(DebugContext.DebugStep.error(name, System.currentTimeMillis() - start, e.getMessage()));
            throw e;
        }
    }

    /** 记录无返回值的执行阶段 */
    private void recordVoidStep(DebugContext ctx, String name, Runnable action) {
        if (ctx == null) {
            action.run();
            return;
        }
        long start = System.currentTimeMillis();
        try {
            action.run();
            ctx.addStep(DebugContext.DebugStep.success(name, System.currentTimeMillis() - start));
        } catch (Exception e) {
            ctx.addStep(DebugContext.DebugStep.error(name, System.currentTimeMillis() - start, e.getMessage()));
            throw e;
        }
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
                                                                OrchestrationContext context,
                                                                DebugContext debugContext) {
        DebugTrace trace = null;
        if (debugContext != null) {
            trace = new DebugTrace();
            trace.setQueryId(query.getId());
            trace.setType(query.getType());
            trace.setDatasource(query.getDatasource());
            trace.setStartTimeMs(System.currentTimeMillis());
        }

        try {
            Map<String, Object> previousResults = context.getPreviousResults();
            List<Map<String, Object>> result = executeQuery(query, context.getRequestData(), previousResults, trace);
            context.putPreviousResult(query.getId(), result);
            if (trace != null) {
                trace.setRowCount(result.size());
                trace.setStatus("SUCCESS");
            }
            return result;
        } catch (Exception e) {
            if (trace != null) {
                trace.setStatus("ERROR");
                trace.setErrorMessage(e.getMessage());
            }
            throw e;
        } finally {
            if (trace != null) {
                trace.setEndTimeMs(System.currentTimeMillis());
                trace.setElapsedTimeMs(trace.getEndTimeMs() - trace.getStartTimeMs());
                debugContext.addTrace(trace);
            }
        }
    }

    private List<Map<String, Object>> executeQuery(QueryConfig query,
                                                    Map<String, Object> requestData,
                                                    Map<String, Object> previousResults,
                                                    DebugTrace trace) {
        String type = query.getType().toLowerCase();

        switch (type) {
            case "mysql":
            case "doris":
            case "sql":
            case "oracle":
            case "postgresql":
                return executeSqlQuery(query, requestData, previousResults, trace);
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
                                                       Map<String, Object> previousResults,
                                                       DebugTrace trace) {
        DynamicSqlHandler.SqlResult sqlResult = dynamicSqlHandler.process(
                query.getSql(), query.getDynamicSqls(), requestData, previousResults);

        String finalSql = sqlResult.sql;
        List<Object> finalParams = sqlResult.params;

        // 导出批次模式：在 PaginationHandler 执行前覆盖分页参数
        boolean exportMode = requestData.containsKey("_exportPageSize");
        if (exportMode && query.getPaginationConfig() != null) {
            PaginationConfig config = query.getPaginationConfig();
            int exportPageSize = Math.min(
                    Integer.parseInt(requestData.get("_exportPageSize").toString()),
                    config.getMaxPageSize());
            // 注入实际配置的 pageSizeParam（默认"pageSize"），使 PaginationHandler 读取到导出批次大小
            requestData.put(config.getPageSizeParam(), exportPageSize);
            if (config.getMode() == PaginationConfig.PaginationMode.OPTIMIZED) {
                // OPTIMIZED 模式：注入实际配置的 pageNumParam（默认"pageNum"）推进页码
                int exportPageNum = requestData.containsKey("_exportPageNum")
                        ? Integer.parseInt(requestData.get("_exportPageNum").toString()) : 1;
                requestData.put(config.getPageNumParam(), exportPageNum);
            }
            if (config.getMode() == PaginationConfig.PaginationMode.CURSOR
                    && requestData.containsKey("_exportLastId")) {
                // CURSOR 模式：将 _exportLastId 映射到实际配置的 lastIdParam（默认"lastId"）
                requestData.put(config.getLastIdParam(), requestData.get("_exportLastId"));
            }
        }

        if (query.getPaginationConfig() != null) {
            // PaginationHandler 使用上面覆盖后的参数执行分页
            PaginationHandler.PaginationResult pageResult = paginationHandler.rewrite(
                    sqlResult.sql, query.getPaginationConfig(), requestData, sqlResult.params);
            finalSql = pageResult.sql;
            finalParams = pageResult.params;
            if (pageResult.paginated) {
                log.debug("分页改写: mode={}, sql={}", pageResult.mode, finalSql);
            }
        } else if (exportMode) {
            // 无分页配置的 SQL：直接追加 LIMIT/OFFSET
            int exportPageSize = Integer.parseInt(requestData.get("_exportPageSize").toString());
            int exportPageNum = requestData.containsKey("_exportPageNum")
                    ? Integer.parseInt(requestData.get("_exportPageNum").toString()) : 1;
            int offset = (exportPageNum - 1) * exportPageSize;
            finalSql = finalSql + " LIMIT " + exportPageSize + " OFFSET " + offset;
            log.debug("导出分页追加: pageSize={}, pageNum={}", exportPageSize, exportPageNum);
        }

        log.debug("最终SQL: datasource={}, sql={}, params={}", query.getDatasource(), finalSql, finalParams);

        // 调试模式下捕获最终 SQL、参数、分页模式（不含数据源密码等敏感信息）
        if (trace != null) {
            trace.setSql(finalSql);
            trace.setParams(new ArrayList<>(finalParams));
            if (query.getPaginationConfig() != null) {
                trace.setPaginationMode(query.getPaginationConfig().getMode().name().toLowerCase());
            }
        }

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
        if (mongoExecutor == null) {
            throw new RuntimeException("MongoDB未配置，请引入spring-boot-starter-data-mongodb");
        }
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
