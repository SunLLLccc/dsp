package com.sunlc.dsp.admin.assistant.text2api;

import org.springframework.stereotype.Component;

/**
 * Text2API prompt 构造工厂。
 * 为接口定义生成 / Text2SQL 两个阶段构造独立的 prompt。
 */
@Component
public class Text2ApiPromptFactory {

    /**
     * 构造接口定义生成 prompt。
     * 只生成接口定义，不生成 SQL/XML。不确定字段必须追问。
     */
    public String buildInterfacePrompt(String requirementText) {
        return "你是 DSP 接口设计助手。根据以下需求，生成接口定义。\n\n"
                + "需求：\n" + requirementText + "\n\n"
                + "输出要求：\n"
                + "1. 严格输出 JSON，不要任何解释文字。\n"
                + "2. JSON 结构：\n"
                + "   {\n"
                + "     \"transno\": \"大写英文+下划线，接口唯一标识\",\n"
                + "     \"name\": \"中文名称\",\n"
                + "     \"system\": \"归属系统名\",\n"
                + "     \"description\": \"接口描述\",\n"
                + "     \"inputSchema\": \"入参结构描述（字段名+类型+是否必填+说明）\",\n"
                + "     \"outputSchema\": \"JSON Schema 字符串，描述响应结构\",\n"
                + "     \"questions\": []\n"
                + "   }\n"
                + "3. 如果信息不足以确定任何字段，把追问的问题放入 questions 数组，其它字段留空。\n"
                + "4. 不要编造不确定的字段。\n"
                + "5. outputSchema 必须是合法的 JSON Schema 字符串。";
    }

    /**
     * 构造 Text2SQL prompt。
     * 基于 SchemaEvidence 生成 SQL，只能用 evidence 中的表和字段。
     */
    public String buildSqlPrompt(String requirementText, String interfaceDraftText, String schemaEvidenceText) {
        return "你是 DSP SQL 生成助手。根据接口定义和表结构依据，生成查询 SQL。\n\n"
                + "需求：\n" + requirementText + "\n\n"
                + "接口定义：\n" + interfaceDraftText + "\n\n"
                + "表结构依据（只能使用以下表和字段，不得使用未列出的表/字段）：\n"
                + schemaEvidenceText + "\n\n"
                + "输出要求：\n"
                + "1. 严格输出 JSON，不要任何解释文字。\n"
                + "2. JSON 结构：\n"
                + "   {\n"
                + "     \"sqlItems\": [\n"
                + "       {\n"
                + "         \"sqlId\": \"唯一标识\",\n"
                + "         \"sql\": \"SELECT 语句\",\n"
                + "         \"purpose\": \"该查询的目的\",\n"
                + "         \"dependsOn\": [],\n"
                + "         \"outputAlias\": \"输出别名\",\n"
                + "         \"relationDescription\": \"与其它查询的关系说明\"\n"
                + "       }\n"
                + "     ],\n"
                + "     \"questions\": []\n"
                + "   }\n"
                + "3. 只能生成 SELECT 或 WITH ... SELECT 语句，禁止 INSERT/UPDATE/DELETE/DDL。\n"
                + "4. 只能使用上述表结构依据中出现的表和字段。\n"
                + "5. 如果无法确定表/字段/关联，把追问放入 questions 数组，sqlItems 留空。\n"
                + "6. 多段 SQL 有依赖时，dependsOn 填前序 sqlId。\n"
                + "7. 不要编造表名或字段名。";
    }
}
