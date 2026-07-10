package com.sunlc.dsp.admin.assistant.text2api;

import java.util.List;

/**
 * SchemaEvidence 前端载体（Text2SQL 的表结构依据）。
 * <p>
 * 结构合法性在此层校验；是否充分由 {@link Text2ApiService} 的门禁兜底。
 */
public class SchemaEvidenceDto {
    /** 来源：USER_INPUT / DATASOURCE_METADATA / MIXED */
    private String source;
    /** 涉及的表结构摘要。 */
    private List<TableEvidenceDto> tables;

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public List<TableEvidenceDto> getTables() { return tables; }
    public void setTables(List<TableEvidenceDto> tables) { this.tables = tables; }

    public static class TableEvidenceDto {
        private String tableName;
        private List<String> columns;
        private String description;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
