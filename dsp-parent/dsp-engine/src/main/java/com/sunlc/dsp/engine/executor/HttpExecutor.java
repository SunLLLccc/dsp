package com.sunlc.dsp.engine.executor;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.sunlc.dsp.engine.model.HttpQueryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class HttpExecutor {

    private final ExpressionParser spelParser = new SpelExpressionParser();

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> execute(HttpQueryConfig httpConfig, String datasourceName,
                                              Map<String, Object> requestData,
                                              Map<String, Object> previousResults) {
        StandardEvaluationContext context = buildContext(requestData, previousResults);

        String url = replaceExpressions(httpConfig.getUrl(), context);
        String body = httpConfig.getBody() != null ? replaceExpressions(httpConfig.getBody(), context) : null;
        String headers = httpConfig.getHeaders() != null ? replaceExpressions(httpConfig.getHeaders(), context) : null;

        log.info("HTTP调用: method={}, url={}", httpConfig.getMethod(), url);

        HttpResponse httpResponse;
        String method = httpConfig.getMethod().toUpperCase();
        switch (method) {
            case "POST":
                HttpRequest postRequest = HttpRequest.post(url).timeout(10000);
                addHeaders(postRequest, headers);
                if (body != null) {
                    postRequest.body(body);
                }
                httpResponse = postRequest.execute();
                break;
            case "GET":
            default:
                HttpRequest getRequest = HttpRequest.get(url).timeout(10000);
                addHeaders(getRequest, headers);
                httpResponse = getRequest.execute();
                break;
        }

        String responseBody = httpResponse.body();
        log.debug("HTTP响应: status={}, body={}", httpResponse.getStatus(), responseBody);

        if (!httpResponse.isOk()) {
            throw new RuntimeException("HTTP调用失败: status=" + httpResponse.getStatus() + ", url=" + url);
        }

        Object extracted = extractData(responseBody, httpConfig.getResponsePath());
        return normalizeToList(extracted);
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

    private String replaceExpressions(String template, StandardEvaluationContext context) {
        if (template == null) return null;

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#\\{\\$([^}]+)}").matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expr = "#" + matcher.group(1);
            try {
                Object value = spelParser.parseExpression(expr).getValue(context);
                matcher.appendReplacement(sb, value != null ? value.toString() : "");
            } catch (Exception e) {
                log.warn("HTTP参数替换失败: {}", expr);
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        matcher = java.util.regex.Pattern.compile("\\$\\{([^}]+)}").matcher(result);
        sb = new StringBuffer();
        while (matcher.find()) {
            String expr = "#" + matcher.group(1);
            try {
                Object value = spelParser.parseExpression(expr).getValue(context);
                matcher.appendReplacement(sb, value != null ? value.toString() : "");
            } catch (Exception e) {
                log.warn("HTTP参数替换失败: {}", expr);
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private void addHeaders(HttpRequest request, String headers) {
        if (headers != null && !headers.isEmpty()) {
            try {
                Map<String, Object> headerMap = JSONUtil.toBean(headers, Map.class);
                headerMap.forEach((k, v) -> request.header(k, v != null ? v.toString() : ""));
            } catch (Exception e) {
                log.warn("请求头解析失败: {}", headers);
            }
        }
    }

    private Object extractData(String responseBody, String responsePath) {
        if (responsePath == null || responsePath.isEmpty()) {
            return JSONUtil.parse(responseBody);
        }

        try {
            cn.hutool.json.JSON json = JSONUtil.parse(responseBody);
            Object result = json.getByPath(responsePath);
            if (result == null) {
                log.warn("响应路径提取为空: path={}", responsePath);
            }
            return result;
        } catch (Exception e) {
            log.warn("响应数据提取失败: path={}, error={}", responsePath, e.getMessage());
            return JSONUtil.parse(responseBody);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeToList(Object data) {
        List<Map<String, Object>> result = new ArrayList<>();
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
