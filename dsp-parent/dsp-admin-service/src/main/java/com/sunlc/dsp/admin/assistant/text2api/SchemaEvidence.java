package com.sunlc.dsp.admin.assistant.text2api;

import java.util.Collections;
import java.util.List;

/**
 * Text2SQL 的表结构依据。
 * <p>
 * 安全约束（design 4.1-4.2）：SQL 生成必须基于 SchemaEvidence。
 * evidence 为空时，Text2ApiService 不调用 AiGateway，返回 needs_more_info。
 * <p>
 * 来源只允许两类：
 * <ul>
 *   <li>用户输入：用户在需求/对话中提供的表结构说明</li>
 *   <li>数据源元数据：用户选择的数据源元数据读取结果（TableMeta + ColumnMeta）</li>
 * </ul>
 */
public class SchemaEvidence {

    /** 来源：USER_INPUT / DATASOURCE_METADATA / MIXED */
    private final String source;
    /** 涉及的表结构摘要（表名 + 字段列表），用于拼入 prompt。 */
    private final List<TableEvidence> tables;

    public SchemaEvidence(String source, List<TableEvidence> tables) {
        this.source = source;
        this.tables = tables == null ? Collections.emptyList() : Collections.unmodifiableList(tables);
    }

    /**
     * 是否无有效表依据。
     * <p>
     * 有效表依据至少需要：tableName 非空 + columns 中至少一个非空字段。
     * 只要有任一表满足，evidence 即视为非空（可通过门禁）。
     */
    public boolean isEmpty() {
        if (tables == null || tables.isEmpty()) {
            return true;
        }
        // 至少一张表有 tableName + 至少一个非空字段
        return tables.stream().noneMatch(t -> {
            if (t.getTableName() == null || t.getTableName().isBlank()) {
                return false;
            }
            return t.getColumns() != null
                    && t.getColumns().stream().anyMatch(c -> c != null && !c.isBlank());
        });
    }

    public String getSource() { return source; }
    public List<TableEvidence> getTables() { return tables; }

    /** 单张表的依据。 */
    public static class TableEvidence {
        private final String tableName;
        private final List<String> columns;
        private final String description;

        public TableEvidence(String tableName, List<String> columns, String description) {
            this.tableName = tableName;
            this.columns = columns == null ? Collections.emptyList() : Collections.unmodifiableList(columns);
            this.description = description;
        }

        public String getTableName() { return tableName; }
        public List<String> getColumns() { return columns; }
        public String getDescription() { return description; }
    }
}
