package com.fintechervision.dsp.engine.executor;

import cn.hutool.json.JSONUtil;
import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.engine.model.MongoQueryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MongoDB查询执行器
 * 当spring-boot-starter-data-mongodb未引入或未配置时，mongoTemplate为null，执行时会给出友好提示
 */
@Slf4j
@Component
public class MongoExecutor {

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    public List<Map<String, Object>> execute(MongoQueryConfig config, String datasource,
                                              Map<String, Object> requestData,
                                              Map<String, Object> previousResults) {
        if (mongoTemplate == null) {
            throw new BusinessException(ErrorCode.DATASOURCE_ERROR, "MongoDB未配置，请引入spring-boot-starter-data-mongodb并配置MongoDB连接");
        }

        // 解析filter，替换#{param}参数
        String filterJson = resolveParams(config.getFilter(), requestData, previousResults);
        String projectionJson = resolveParams(config.getProjection(), requestData, previousResults);
        String sortJson = resolveParams(config.getSort(), requestData, previousResults);

        log.debug("MongoDB查询: collection={}, filter={}, projection={}, sort={}, limit={}, skip={}",
                config.getCollection(), filterJson, projectionJson, sortJson, config.getLimit(), config.getSkip());

        BasicQuery query;
        if (filterJson != null && !filterJson.isEmpty()) {
            query = new BasicQuery(filterJson);
        } else {
            query = new BasicQuery("{}");
        }

        if (projectionJson != null && !projectionJson.isEmpty()) {
            query = new BasicQuery(filterJson != null ? filterJson : "{}", projectionJson);
        }

        if (sortJson != null && !sortJson.isEmpty()) {
            org.bson.Document sortDoc = org.bson.Document.parse(sortJson);
            query.getSortObject().putAll(sortDoc);
        }

        if (config.getLimit() != null && config.getLimit() > 0) {
            query.limit(config.getLimit());
        }

        if (config.getSkip() != null && config.getSkip() > 0) {
            query.skip(config.getSkip());
        }

        List<Map> results = mongoTemplate.find(query, Map.class, config.getCollection());

        // 转换为List<Map<String, Object>>
        List<Map<String, Object>> mappedResults = new ArrayList<>();
        for (Map map : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Object key : map.keySet()) {
                row.put(String.valueOf(key), map.get(key));
            }
            mappedResults.add(row);
        }

        log.debug("MongoDB查询结果: collection={}, count={}", config.getCollection(), mappedResults.size());
        return mappedResults;
    }

    /**
     * 替换JSON中的#{param}参数
     */
    private String resolveParams(String jsonTemplate, Map<String, Object> requestData,
                                  Map<String, Object> previousResults) {
        if (jsonTemplate == null || jsonTemplate.isEmpty()) {
            return jsonTemplate;
        }
        String result = jsonTemplate;
        // 替换 #{param} 格式的参数
        Map<String, Object> allParams = new HashMap<>();
        if (requestData != null) allParams.putAll(requestData);
        if (previousResults != null) allParams.putAll(previousResults);

        for (Map.Entry<String, Object> entry : allParams.entrySet()) {
            String placeholder = "#{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "null";
                // JSON值：数字/布尔不加引号，字符串加引号
                if (isNumericOrBoolean(value)) {
                    result = result.replace("\"" + placeholder + "\"", value);
                    result = result.replace(placeholder, value);
                } else {
                    result = result.replace(placeholder, value);
                }
            }
        }
        return result;
    }

    private boolean isNumericOrBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return true;
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
