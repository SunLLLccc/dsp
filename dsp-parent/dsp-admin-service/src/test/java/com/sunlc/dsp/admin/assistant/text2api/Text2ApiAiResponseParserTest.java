package com.sunlc.dsp.admin.assistant.text2api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text2ApiAiResponseParser 单测。
 * 覆盖：JSON 提取、接口定义解析校验、SQL 解析校验、危险关键字拦截。
 */
class Text2ApiAiResponseParserTest {

    private final Text2ApiAiResponseParser parser = new Text2ApiAiResponseParser();

    // ===== 接口定义解析 =====

    @Test
    void parseInterfaceDraft_validJson_returnsDraft() {
        String ai = "```json\n{\"transno\":\"USER_QUERY\",\"name\":\"用户查询\","
                + "\"inputSchema\":\"userId:String\",\"outputSchema\":\"{\\\"type\\\":\\\"object\\\"}\","
                + "\"questions\":[]}\n```";
        InterfaceDraft draft = parser.parseInterfaceDraft(ai);

        assertNotNull(draft);
        assertEquals("USER_QUERY", draft.getTransno());
        assertEquals("用户查询", draft.getName());
    }

    @Test
    void parseInterfaceDraft_bareJson_returnsDraft() {
        String ai = "{\"transno\":\"T1\",\"name\":\"测试\","
                + "\"inputSchema\":\"id\",\"outputSchema\":\"{}\",\"questions\":[]}";
        InterfaceDraft draft = parser.parseInterfaceDraft(ai);
        assertNotNull(draft);
    }

    @Test
    void parseInterfaceDraft_missingTransno_throws() {
        String ai = "{\"name\":\"测试\",\"inputSchema\":\"id\",\"outputSchema\":\"{}\",\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseInterfaceDraft(ai),
                "缺 transno 应抛异常");
    }

    @Test
    void parseInterfaceDraft_missingName_throws() {
        String ai = "{\"transno\":\"T1\",\"inputSchema\":\"id\",\"outputSchema\":\"{}\",\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseInterfaceDraft(ai));
    }

    @Test
    void parseInterfaceDraft_questionsNonEmpty_returnsNull() {
        String ai = "{\"transno\":\"\",\"name\":\"\","
                + "\"inputSchema\":\"\",\"outputSchema\":\"\","
                + "\"questions\":[\"表名是什么?\"]}";
        // questions 非空 → 信息不足 → null
        InterfaceDraft draft = parser.parseInterfaceDraft(ai);
        assertNull(draft);
    }

    @Test
    void parseInterfaceDraft_noJson_throws() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseInterfaceDraft("这不是JSON"));
    }

    @Test
    void parseInterfaceDraft_emptyOutput_throws() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseInterfaceDraft(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parseInterfaceDraft(""));
    }

    // ===== Text2SQL 解析 =====

    @Test
    void parseSqlDraft_validSelect_returnsDraft() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"main\","
                + "\"sql\":\"SELECT id, name FROM users\",\"purpose\":\"查询用户\","
                + "\"dependsOn\":[],\"outputAlias\":\"users\"}],\"questions\":[]}";
        SqlDraft draft = parser.parseSqlDraft(ai);

        assertNotNull(draft);
        assertEquals(1, draft.getSqlItems().size());
        assertEquals("main", draft.getSqlItems().get(0).getSqlId());
    }

    @Test
    void parseSqlDraft_withSelect_returnsDraft() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"WITH t AS (SELECT 1) SELECT * FROM t\",\"purpose\":\"CTE测试\","
                + "\"dependsOn\":[]}],\"questions\":[]}";
        SqlDraft draft = parser.parseSqlDraft(ai);
        assertNotNull(draft);
    }

    @Test
    void parseSqlDraft_emptySqlItems_throws() {
        String ai = "{\"sqlItems\":[],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai));
    }

    @Test
    void parseSqlDraft_missingSqlId_throws() {
        String ai = "{\"sqlItems\":[{\"sql\":\"SELECT 1\",\"purpose\":\"测试\",\"dependsOn\":[]}],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai),
                "缺 sqlId 应抛异常");
    }

    @Test
    void parseSqlDraft_questionsNonEmpty_returnsNull() {
        String ai = "{\"sqlItems\":[],\"questions\":[\"users 表有哪些字段?\"]}";
        SqlDraft draft = parser.parseSqlDraft(ai);
        assertNull(draft, "questions 非空 → 信息不足 → null");
    }

    // ===== SQL 危险关键字拦截 =====

    @Test
    void parseSqlDraft_insertRejected() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"INSERT INTO users VALUES(1)\",\"purpose\":\"插入\",\"dependsOn\":[]}],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai),
                "INSERT 应被拦截");
    }

    @Test
    void parseSqlDraft_deleteRejected() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"DELETE FROM users\",\"purpose\":\"删除\",\"dependsOn\":[]}],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai));
    }

    @Test
    void parseSqlDraft_dropRejected() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"SELECT * FROM users; DROP TABLE users\",\"purpose\":\"测试\",\"dependsOn\":[]}],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai),
                "DROP 应被拦截（即使在 SELECT 之后）");
    }

    @Test
    void parseSqlDraft_updateRejected() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"UPDATE users SET name='x'\",\"purpose\":\"更新\",\"dependsOn\":[]}],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai));
    }

    @Test
    void parseSqlDraft_createTableRejected() {
        String ai = "{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"CREATE TABLE x(id int)\",\"purpose\":\"建表\",\"dependsOn\":[]}],\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseSqlDraft(ai));
    }

    // ===== validateSelectOnly 直接测（static）=====

    @Test
    void validateSelectOnly_simpleSelectPasses() {
        assertDoesNotThrow(() -> Text2ApiAiResponseParser.validateSelectOnly("SELECT * FROM users"));
    }

    @Test
    void validateSelectOnly_withSelectPasses() {
        assertDoesNotThrow(() -> Text2ApiAiResponseParser.validateSelectOnly(
                "WITH t AS (SELECT 1) SELECT * FROM t"));
    }

    @Test
    void validateSelectOnly_notStartingWithSelectRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Text2ApiAiResponseParser.validateSelectOnly("SHOW TABLES"));
    }

    // ===== 必改2：词边界匹配不误杀（deleted/update_time/create_time 等）=====

    @Test
    void validateSelectOnly_deletedFieldPasses() {
        assertDoesNotThrow(() -> Text2ApiAiResponseParser.validateSelectOnly(
                "SELECT id, deleted FROM users"));
    }

    @Test
    void validateSelectOnly_updateTimeFieldPasses() {
        assertDoesNotThrow(() -> Text2ApiAiResponseParser.validateSelectOnly(
                "SELECT update_time FROM users"));
    }

    @Test
    void validateSelectOnly_userUpdateLogTablePasses() {
        assertDoesNotThrow(() -> Text2ApiAiResponseParser.validateSelectOnly(
                "SELECT * FROM user_update_log"));
    }

    @Test
    void validateSelectOnly_stringLiteralWithDropPasses() {
        // 字符串字面量 'DROP' 不应被误杀
        assertDoesNotThrow(() -> Text2ApiAiResponseParser.validateSelectOnly(
                "SELECT 'DROP' AS keyword FROM users"));
    }

    @Test
    void validateSelectOnly_multiStatementWithDropRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Text2ApiAiResponseParser.validateSelectOnly(
                        "SELECT * FROM users; DROP TABLE users"),
                "多语句含 DROP 应拒绝");
    }

    @Test
    void validateSelectOnly_withDeleteRejected() {
        // WITH 子句含 DELETE 仍拒绝
        assertThrows(IllegalArgumentException.class,
                () -> Text2ApiAiResponseParser.validateSelectOnly(
                        "WITH x AS (DELETE FROM users RETURNING *) SELECT * FROM x"));
    }

    // ===== 建议改2：outputSchema 合法 JSON 校验 =====

    @Test
    void parseInterfaceDraft_invalidOutputSchemaJson_throws() {
        String ai = "{\"transno\":\"T1\",\"name\":\"测试\","
                + "\"inputSchema\":\"id\",\"outputSchema\":\"这不是JSON\",\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parseInterfaceDraft(ai),
                "outputSchema 非合法 JSON 应拒绝");
    }

    @Test
    void parseInterfaceDraft_validOutputSchemaObjectPasses() {
        String ai = "{\"transno\":\"T1\",\"name\":\"测试\","
                + "\"inputSchema\":\"id\",\"outputSchema\":\"{\\\"type\\\":\\\"object\\\"}\",\"questions\":[]}";
        InterfaceDraft draft = parser.parseInterfaceDraft(ai);
        assertNotNull(draft);
    }
}
