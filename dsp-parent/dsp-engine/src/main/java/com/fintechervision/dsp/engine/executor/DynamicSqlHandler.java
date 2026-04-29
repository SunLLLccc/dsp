package com.fintechervision.dsp.engine.executor;

import com.fintechervision.dsp.engine.model.DynamicSqlConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DynamicSqlHandler {

    private final ExpressionParser spelParser = new SpelExpressionParser();
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)}");

    public SqlResult process(String baseSql, List<DynamicSqlConfig> dynamicSqls,
                             Map<String, Object> requestData, Map<String, Object> previousResults) {
        StandardEvaluationContext evalContext = buildContext(requestData, previousResults);

        StringBuilder sqlBuilder = new StringBuilder(baseSql);
        List<Object> sqlParams = new ArrayList<>();

        for (DynamicSqlConfig dynamic : dynamicSqls) {
            if (dynamic.getType() == DynamicSqlConfig.DynamicType.IF) {
                String appended = processIf(dynamic, evalContext);
                if (appended != null) {
                    sqlBuilder.append(" ").append(appended);
                }
            } else if (dynamic.getType() == DynamicSqlConfig.DynamicType.FOREACH) {
                ForeachResult result = processForeach(dynamic, evalContext);
                sqlBuilder.append(" ").append(result.sql);
                sqlParams.addAll(result.params);
            }
        }

        return replaceParameters(sqlBuilder.toString(), evalContext, sqlParams);
    }

    private StandardEvaluationContext buildContext(Map<String, Object> requestData,
                                                   Map<String, Object> previousResults) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (requestData != null) {
            context.setVariable("requestData", requestData);
        }
        if (previousResults != null) {
            for (Map.Entry<String, Object> entry : previousResults.entrySet()) {
                Object value = entry.getValue();
                context.setVariable(entry.getKey(), value);
            }
        }
        return context;
    }

    private String processIf(DynamicSqlConfig config, StandardEvaluationContext evalContext) {
        try {
            String testExpr = config.getTest().replace("$", "#");
            Expression expression = spelParser.parseExpression(testExpr);
            Boolean result = expression.getValue(evalContext, Boolean.class);
            if (Boolean.TRUE.equals(result)) {
                return config.getSql();
            }
        } catch (Exception e) {
            log.warn("IF条件评估失败: test={}, error={}", config.getTest(), e.getMessage());
        }
        return null;
    }

    private ForeachResult processForeach(DynamicSqlConfig config, StandardEvaluationContext evalContext) {
        ForeachResult result = new ForeachResult();
        String collectionExpr = config.getCollection().replace("$", "#");
        Object collection;
        try {
            Expression expression = spelParser.parseExpression(collectionExpr);
            collection = expression.getValue(evalContext);
        } catch (Exception e) {
            log.warn("foreach集合解析失败: collection={}", config.getCollection());
            return result;
        }

        if (collection instanceof Collection) {
            Collection<?> items = (Collection<?>) collection;
            StringBuilder sb = new StringBuilder(config.getOpen());
            int index = 0;
            for (Object item : items) {
                if (index > 0) {
                    sb.append(config.getSeparator());
                }
                String itemSql = config.getForeachSql()
                        .replace("#{" + config.getItem() + "}", "?");
                sb.append(itemSql);
                result.params.add(item);
                index++;
            }
            sb.append(config.getClose());
            result.sql = sb.toString();
        } else {
            log.warn("foreach的collection不是集合类型: {}", config.getCollection());
        }
        return result;
    }

    private SqlResult replaceParameters(String sql, StandardEvaluationContext evalContext,
                                         List<Object> existingParams) {
        List<Object> allParams = new ArrayList<>(existingParams);
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramExpr = matcher.group(1).trim();
            String spelExpr = paramExpr.replace("$", "#");
            Object value = null;
            try {
                Expression expression = spelParser.parseExpression(spelExpr);
                value = expression.getValue(evalContext);
            } catch (Exception e) {
                log.warn("参数解析失败: {}, error={}", paramExpr, e.getMessage());
            }
            matcher.appendReplacement(sb, "?");
            allParams.add(value);
        }
        matcher.appendTail(sb);

        return new SqlResult(sb.toString(), allParams);
    }

    public static class SqlResult {
        public final String sql;
        public final List<Object> params;
        public SqlResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    private static class ForeachResult {
        String sql = "";
        List<Object> params = new ArrayList<>();
    }
}
