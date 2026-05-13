package com.sunlc.dsp.engine.executor;

import cn.hutool.json.JSONUtil;
import com.sunlc.dsp.engine.model.DubboQueryConfig;
import com.sunlc.dsp.entity.DatasourceConfig;
import com.sunlc.dsp.service.DatasourceManagerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DubboExecutor {

    private final DatasourceManagerService datasourceManagerService;
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final Map<String, ReferenceConfig<GenericService>> referenceCache = new ConcurrentHashMap<>();
    private volatile ApplicationConfig applicationConfig;

    public DubboExecutor(DatasourceManagerService datasourceManagerService) {
        this.datasourceManagerService = datasourceManagerService;
    }

    public List<Map<String, Object>> execute(DubboQueryConfig dubboConfig, String datasourceName,
                                              Map<String, Object> requestData,
                                              Map<String, Object> previousResults) {
        RegistryConfig registryConfig = getRegistryConfig(datasourceName);
        StandardEvaluationContext context = buildContext(requestData, previousResults);

        String[] paramTypes = new String[dubboConfig.getParams().size()];
        Object[] paramValues = new Object[dubboConfig.getParams().size()];

        for (int i = 0; i < dubboConfig.getParams().size(); i++) {
            DubboQueryConfig.DubboParam param = dubboConfig.getParams().get(i);
            paramTypes[i] = param.getType();
            paramValues[i] = resolveParamValue(param.getValue(), param.getType(), context);
        }

        log.info("Dubbo泛化调用: service={}, method={}, datasource={}",
                dubboConfig.getService(), dubboConfig.getMethod(), datasourceName);

        GenericService genericService = getGenericService(dubboConfig, registryConfig);

        Object result;
        try {
            result = genericService.$invoke(dubboConfig.getMethod(), paramTypes, paramValues);
        } catch (Exception e) {
            log.error("Dubbo调用失败: service={}, method={}", dubboConfig.getService(), dubboConfig.getMethod(), e);
            throw new RuntimeException("Dubbo调用失败: " + e.getMessage(), e);
        }

        log.debug("Dubbo调用结果: {}", result);
        return normalizeToList(result);
    }

    private GenericService getGenericService(DubboQueryConfig dubboConfig, RegistryConfig registryConfig) {
        String cacheKey = dubboConfig.getService() + ":" + dubboConfig.getVersion() + ":" + dubboConfig.getGroup();

        ReferenceConfig<GenericService> reference = referenceCache.computeIfAbsent(cacheKey, key -> {
            ReferenceConfig<GenericService> ref = new ReferenceConfig<>();
            ref.setApplication(getApplicationConfig());
            ref.setRegistry(registryConfig);
            ref.setInterface(dubboConfig.getService());
            ref.setVersion(dubboConfig.getVersion());
            ref.setGeneric("true");
            ref.setTimeout(dubboConfig.getTimeout());
            if (dubboConfig.getGroup() != null && !dubboConfig.getGroup().isEmpty()) {
                ref.setGroup(dubboConfig.getGroup());
            }
            ref.setCheck(false);
            return ref;
        });

        return reference.get();
    }

    private RegistryConfig getRegistryConfig(String datasourceName) {
        DatasourceConfig dsConfig = datasourceManagerService.getByDsName(datasourceName);
        if (dsConfig == null) {
            throw new RuntimeException("Dubbo数据源配置不存在: " + datasourceName);
        }

        String extraConfig = dsConfig.getExtraConfig();
        if (extraConfig == null || extraConfig.isEmpty()) {
            throw new RuntimeException("Dubbo数据源缺少extraConfig配置: " + datasourceName);
        }

        cn.hutool.json.JSONObject json = JSONUtil.parseObj(extraConfig);
        String registry = json.getStr("registry");
        if (registry == null || registry.isEmpty()) {
            throw new RuntimeException("Dubbo数据源extraConfig中缺少registry字段: " + datasourceName);
        }

        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress(registry);
        return registryConfig;
    }

    private ApplicationConfig getApplicationConfig() {
        if (applicationConfig == null) {
            synchronized (this) {
                if (applicationConfig == null) {
                    applicationConfig = new ApplicationConfig();
                    applicationConfig.setName("dsp-data-platform");
                }
            }
        }
        return applicationConfig;
    }

    private Object resolveParamValue(String valueExpr, String type, StandardEvaluationContext context) {
        if (valueExpr == null) return null;

        if (valueExpr.contains("#{$")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#\\{\\$([^}]+)}").matcher(valueExpr);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String expr = "#" + matcher.group(1);
                try {
                    Object value = spelParser.parseExpression(expr).getValue(context);
                    matcher.appendReplacement(sb, value != null ? value.toString() : "");
                } catch (Exception e) {
                    log.warn("Dubbo参数解析失败: {}", expr);
                    matcher.appendReplacement(sb, "");
                }
            }
            matcher.appendTail(sb);
            valueExpr = sb.toString();
        }

        return convertType(valueExpr, type);
    }

    private Object convertType(String value, String type) {
        if (value == null) return null;
        try {
            switch (type) {
                case "int": case "Integer": return Integer.parseInt(value);
                case "long": case "Long": return Long.parseLong(value);
                case "double": case "Double": return Double.parseDouble(value);
                case "float": case "Float": return Float.parseFloat(value);
                case "boolean": case "Boolean": return Boolean.parseBoolean(value);
                default: return value;
            }
        } catch (NumberFormatException e) {
            log.warn("参数类型转换失败: value={}, type={}", value, type);
            return value;
        }
    }

    private StandardEvaluationContext buildContext(Map<String, Object> requestData,
                                                   Map<String, Object> previousResults) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (requestData != null) {
            context.setVariable("requestData", requestData);
        }
        if (previousResults != null) {
            previousResults.forEach(context::setVariable);
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeToList(Object data) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (data == null) return result;
        if (data instanceof List) {
            for (Object item : (List<?>) data) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                } else {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("value", item);
                    result.add(row);
                }
            }
        } else if (data instanceof Map) {
            result.add((Map<String, Object>) data);
        } else {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", data);
            result.add(row);
        }
        return result;
    }
}
