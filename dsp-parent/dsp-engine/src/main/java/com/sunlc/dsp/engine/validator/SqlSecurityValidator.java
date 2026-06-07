package com.sunlc.dsp.engine.validator;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.engine.model.DynamicSqlConfig;
import com.sunlc.dsp.engine.model.InterfaceConfig;
import com.sunlc.dsp.engine.model.QueryConfig;
import com.sunlc.dsp.engine.parser.XmlConfigParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 安全校验器 — 发布/保存 XML 前校验 SQL 只读安全性
 */
@Slf4j
@Component
public class SqlSecurityValidator {

    /** 危险 SQL 关键字（不允许出现在 SQL 中） */
    private static final Set<String> DANGEROUS_KEYWORDS = new HashSet<>(Arrays.asList(
            "UPDATE", "DELETE", "INSERT", "DROP", "ALTER", "TRUNCATE",
            "MERGE", "GRANT", "REVOKE", "CREATE", "EXEC", "EXECUTE"
    ));

    /** SQL 类型的查询（需要校验） */
    private static final Set<String> SQL_QUERY_TYPES = new HashSet<>(Arrays.asList(
            "mysql", "doris", "sql", "oracle", "postgresql"
    ));

    /** SQL 注释标记 */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(--|/\\*|\\*/)");

    /** 分号 */
    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");

    /** 危险关键字正则：单词边界匹配 */
    private static final Pattern DANGEROUS_KEYWORD_PATTERN = Pattern.compile(
            "\\b(UPDATE|DELETE|INSERT|DROP|ALTER|TRUNCATE|MERGE|GRANT|REVOKE|CREATE|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /** orderBy 合法字段名：字母/下划线开头，允许 表名.字段名，可选 ASC/DESC，逗号分隔 */
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "^[a-zA-Z_]\\w*(\\.[a-zA-Z_]\\w*)?(\\s+(ASC|DESC))?(\\s*,\\s*[a-zA-Z_]\\w*(\\.[a-zA-Z_]\\w*)?(\\s+(ASC|DESC))?)*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 校验 XML 配置中所有 SQL 的只读安全性
     *
     * @param inputSchema XML 配置内容
     * @throws BusinessException 校验失败时抛出
     */
    public void validateXmlConfig(String inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return;
        }

        XmlConfigParser parser = new XmlConfigParser();
        InterfaceConfig config;
        try {
            config = parser.parse(inputSchema);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "XML配置解析失败: " + e.getMessage());
        }

        for (QueryConfig query : config.getQueries()) {
            if (!SQL_QUERY_TYPES.contains(query.getType().toLowerCase())) {
                continue;
            }

            // 校验基础 SQL
            validateSqlContent(query.getSql(), "query[" + query.getId() + "]");

            // 校验动态 SQL 片段
            if (query.getDynamicSqls() != null) {
                for (DynamicSqlConfig dynamic : query.getDynamicSqls()) {
                    if (dynamic.getSql() != null && !dynamic.getSql().isEmpty()) {
                        validateSqlContent(dynamic.getSql(), "query[" + query.getId() + "].dynamic");
                    }
                }
            }

            // 校验 orderBy
            if (query.getPaginationConfig() != null && query.getPaginationConfig().getOrderBy() != null) {
                validateOrderBy(query.getPaginationConfig().getOrderBy(), query.getId());
            }
        }
    }

    /**
     * 校验单段 SQL 内容的安全性
     */
    void validateSqlContent(String sql, String context) {
        if (sql == null || sql.isEmpty()) {
            return;
        }

        // 1. 检查分号（多语句注入）
        if (SEMICOLON_PATTERN.matcher(sql).find()) {
            log.warn("SQL安全校验失败[{}]: 包含分号", context);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "SQL配置不合法[" + context + "]: 不允许包含分号(;)，禁止多语句执行");
        }

        // 2. 检查注释标记
        if (COMMENT_PATTERN.matcher(sql).find()) {
            log.warn("SQL安全校验失败[{}]: 包含注释标记", context);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "SQL配置不合法[" + context + "]: 不允许包含SQL注释(-- 或 /* */)");
        }

        // 3. 检查危险关键字
        java.util.regex.Matcher matcher = DANGEROUS_KEYWORD_PATTERN.matcher(sql);
        if (matcher.find()) {
            String keyword = matcher.group(1).toUpperCase();
            log.warn("SQL安全校验失败[{}]: 包含危险关键字 {}", context, keyword);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "SQL配置不合法[" + context + "]: 不允许包含写操作关键字(" + keyword + ")");
        }
    }

    /**
     * 校验 orderBy 字段，防止注入
     */
    public void validateOrderBy(String orderBy, String queryId) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        String trimmed = orderBy.trim();
        if (!ORDER_BY_PATTERN.matcher(trimmed).matches()) {
            log.warn("orderBy校验失败[query={}]: orderBy={}", queryId, orderBy);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "orderBy配置不合法: 只允许字段名(可含表名前缀)和ASC/DESC，当前值: " + orderBy);
        }
    }
}
