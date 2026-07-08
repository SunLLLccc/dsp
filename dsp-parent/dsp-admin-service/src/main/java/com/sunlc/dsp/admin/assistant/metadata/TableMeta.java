package com.sunlc.dsp.admin.assistant.metadata;

/**
 * 表元数据（结构信息，不含业务数据）。
 */
public class TableMeta {
    private String catalog;
    private String schema;
    private String tableName;
    private String tableComment;

    public TableMeta() {}

    public TableMeta(String catalog, String schema, String tableName, String tableComment) {
        this.catalog = catalog;
        this.schema = schema;
        this.tableName = tableName;
        this.tableComment = tableComment;
    }

    /** 白名单 key：schema/catalog + tableName，避免不同 schema 下同名表混淆。 */
    public String whitelistKey() {
        return (schema != null ? schema : "") + "." + (catalog != null ? catalog : "") + "." + tableName;
    }

    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
}
