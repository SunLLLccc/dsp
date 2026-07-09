package com.sunlc.dsp.admin.assistant.text2api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * AI 响应解析器：从 AI 文本输出中提取 JSON，解析并校验。
 * <p>
 * 解析接口定义 → {@link InterfaceDraft}；
 * 解析 Text2SQL → {@link SqlDraft}。
 * <p>
 * 不直接相信 AI 输出：解析失败/字段缺失/危险 SQL 都会拒绝。
 */
@Slf4j
@Component
public class Text2ApiAiResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 匹配 ```json ... ``` 代码块或裸 JSON。 */
    private static final Pattern JSON_BLOCK = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");
    private static final Pattern BARE_JSON = Pattern.compile("(?s)\\{.*\\}");

    // ===== 接口定义解析 =====

    /**
     * 解析接口定义 AI 输出。
     *
     * @return InterfaceDraft；null 表示 AI 返回 questions（信息不足）
     * @throws IllegalArgumentException 解析失败或字段缺失（不可落库）
     */
    public InterfaceDraft parseInterfaceDraft(String aiOutput) {
        String json = extractJson(aiOutput);
        try {
            InterfaceDraft draft = objectMapper.readValue(json, InterfaceDraft.class);
            // questions 非空 → 信息不足
            if (draft.getQuestions() != null && !draft.getQuestions().isEmpty()) {
                log.debug("接口定义 AI 返回 questions，信息不足");
                return null;
            }
            // 校验必填字段
            requireNonBlank(draft.getTransno(), "transno");
            requireNonBlank(draft.getName(), "name");
            requireNonBlank(draft.getInputSchema(), "inputSchema");
            requireNonBlank(draft.getOutputSchema(), "outputSchema");
            // outputSchema 必须是合法 JSON 字符串
            requireValidJson(draft.getOutputSchema(), "outputSchema");
            return draft;
        } catch (Exception e) {
            throw new IllegalArgumentException("接口定义 AI 输出解析失败: " + e.getMessage(), e);
        }
    }

    // ===== Text2SQL 解析 =====

    /**
     * 解析 Text2SQL AI 输出。
     *
     * @return SqlDraft；null 表示 AI 返回 questions（信息不足）
     * @throws IllegalArgumentException 解析失败/字段缺失/危险 SQL（不可落库）
     */
    public SqlDraft parseSqlDraft(String aiOutput) {
        String json = extractJson(aiOutput);
        try {
            SqlDraft draft = objectMapper.readValue(json, SqlDraft.class);
            // questions 非空 → 信息不足
            if (draft.getQuestions() != null && !draft.getQuestions().isEmpty()) {
                log.debug("Text2SQL AI 返回 questions，信息不足");
                return null;
            }
            List<SqlItem> items = draft.getSqlItems();
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("sqlItems 为空");
            }
            // 逐条校验
            for (int i = 0; i < items.size(); i++) {
                SqlItem item = items.get(i);
                requireNonBlank(item.getSqlId(), "sqlItems[" + i + "].sqlId");
                requireNonBlank(item.getSql(), "sqlItems[" + i + "].sql");
                requireNonBlank(item.getPurpose(), "sqlItems[" + i + "].purpose");
                validateSelectOnly(item.getSql());
            }
            return draft;
        } catch (Exception e) {
            throw new IllegalArgumentException("Text2SQL AI 输出解析失败: " + e.getMessage(), e);
        }
    }

    // ===== SQL 危险关键字拦截（词边界匹配，不误杀 deleted/update_time 等）=====

    /** 一期禁止的 SQL 关键字（大小写不敏感，词边界匹配）。只允许 SELECT/WITH SELECT。 */
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER", "CREATE",
            "GRANT", "REVOKE", "MERGE", "CALL", "EXEC", "EXECUTE");

    /** 词边界匹配正则：关键字前后必须是非字母数字下划线（或字符串首尾）。 */
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "(?i)(?:^|[^A-Z0-9_])(" + String.join("|", FORBIDDEN_KEYWORDS) + ")(?:[^A-Z0-9_]|$)");

    /**
     * 校验 SQL 只读：只允许 SELECT / WITH SELECT，禁止写操作和 DDL。
     * <p>
     * 用词边界匹配避免误杀（如 deleted/update_time/create_time 等字段名）。
     * 多语句（非末尾分号）也拒绝。
     *
     * @throws IllegalArgumentException 检测到危险关键字或多语句
     */
    static void validateSelectOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 为空");
        }
        // 剥离字符串字面量（'...'），避免 SELECT 'DROP' 被误杀
        String withoutLiterals = sql.replaceAll("'(?:[^']|'')*'", "''");

        // 多语句拦截：非末尾的分号
        if (withoutLiterals.indexOf(';') != withoutLiterals.lastIndexOf(';')
                || (withoutLiterals.indexOf(';') >= 0
                        && !withoutLiterals.trim().endsWith(";"))) {
            throw new IllegalArgumentException("不允许多语句（检测到非末尾分号）");
        }

        // 词边界匹配危险关键字
        if (FORBIDDEN_PATTERN.matcher(withoutLiterals).find()) {
            throw new IllegalArgumentException("SQL 包含禁止的关键字（只允许 SELECT / WITH SELECT）");
        }

        // 必须以 SELECT 或 WITH 开头（去前导空白和分号）
        String trimmed = sql.stripLeading().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH")) {
            throw new IllegalArgumentException("SQL 必须以 SELECT 或 WITH 开头");
        }
    }

    // ===== 内部工具 =====

    /** 从 AI 输出中提取 JSON（支持 ```json 代码块和裸 JSON）。 */
    String extractJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw new IllegalArgumentException("AI 输出为空");
        }
        // 先试 ```json 代码块
        Matcher blockMatcher = JSON_BLOCK.matcher(aiOutput);
        if (blockMatcher.find()) {
            return blockMatcher.group(1).trim();
        }
        // 再试裸 JSON
        Matcher bareMatcher = BARE_JSON.matcher(aiOutput);
        if (bareMatcher.find()) {
            return bareMatcher.group().trim();
        }
        throw new IllegalArgumentException("AI 输出中未找到 JSON");
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填字段: " + fieldName);
        }
    }

    private void requireValidJson(String value, String fieldName) {
        try {
            objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " 不是合法 JSON 字符串: " + e.getMessage());
        }
    }
}
