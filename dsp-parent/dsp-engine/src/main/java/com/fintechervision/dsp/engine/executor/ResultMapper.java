package com.fintechervision.dsp.engine.executor;

import com.fintechervision.dsp.engine.function.FunctionRegistry;
import com.fintechervision.dsp.engine.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ResultMapper {

    public Object mapResult(List<Map<String, Object>> queryResult, ResultMapConfig resultMap) {
        if (queryResult == null || queryResult.isEmpty()) {
            return Collections.emptyMap();
        }

        if (resultMap.getFields().isEmpty()) {
            if (queryResult.size() == 1) {
                return queryResult.get(0);
            }
            return queryResult;
        }

        List<Map<String, Object>> mappedList = new ArrayList<>();
        for (Map<String, Object> row : queryResult) {
            Map<String, Object> mappedRow = new LinkedHashMap<>();
            for (ResultMapConfig.FieldMapping field : resultMap.getFields()) {
                Object value = row.get(field.getColumn());
                if (field.getFunction() != null && !field.getFunction().isEmpty()) {
                    value = applyFunction(field.getFunction(), value);
                }
                mappedRow.put(field.getName(), value);
            }
            mappedList.add(mappedRow);
        }

        if (mappedList.size() == 1) {
            return mappedList.get(0);
        }
        return mappedList;
    }

    @SuppressWarnings("unchecked")
    public Object buildResponse(ResponseDataConfig config, Map<String, Object> mappedResults) {
        if (config == null) {
            return mappedResults;
        }

        if (config.getFields().isEmpty() && config.getResultMap() != null) {
            Object data = mappedResults.get(config.getResultMap());
            return data != null ? data : Collections.emptyMap();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        for (ResponseFieldConfig field : config.getFields()) {
            Object value = resolveFieldValue(field, config.getResultMap(), mappedResults);
            if (field.getFunction() != null && !field.getFunction().isEmpty()) {
                value = applyFunction(field.getFunction(), value);
            }
            response.put(field.getName(), value);
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private Object resolveFieldValue(ResponseFieldConfig field, String defaultResultMap,
                                      Map<String, Object> mappedResults) {
        String resultMapId = defaultResultMap;
        Object resultMapData = resultMapId != null ? mappedResults.get(resultMapId) : null;

        if (resultMapData == null) {
            return null;
        }

        if (field.getMapTo() != null && !field.getMapTo().isEmpty()) {
            if (resultMapData instanceof Map) {
                return ((Map<String, Object>) resultMapData).get(field.getMapTo());
            } else if (resultMapData instanceof List) {
                List<Object> extracted = new ArrayList<>();
                for (Map<String, Object> item : (List<Map<String, Object>>) resultMapData) {
                    extracted.add(item.get(field.getMapTo()));
                }
                return extracted;
            }
        }

        return resultMapData;
    }

    private Object applyFunction(String function, Object value) {
        try {
            if (function.startsWith("fn:")) {
                String funcExpr = function.substring(3);
                // 解析函数名和参数，格式: fn:FUNC_NAME 或 fn:FUNC_NAME,arg1,arg2,...
                String[] parts = funcExpr.split(",");
                String funcName = parts[0].trim();

                if (!FunctionRegistry.exists(funcName)) {
                    log.warn("函数不存在: {}, 原值保留: {}", funcName, value);
                    return value;
                }

                // 组装参数：第一个参数为行数据值，后续为配置中的额外参数
                Object[] params = new Object[parts.length];
                params[0] = value;
                for (int i = 1; i < parts.length; i++) {
                    params[i] = parts[i].trim();
                }

                Object result = FunctionRegistry.invoke(funcName, params);
                log.debug("函数调用成功: {}, 参数: {}, 结果: {}", funcName, Arrays.toString(params), result);
                return result;
            }
        } catch (Exception e) {
            log.warn("函数执行失败: function={}, value={}", function, value, e);
        }
        return value;
    }
}
