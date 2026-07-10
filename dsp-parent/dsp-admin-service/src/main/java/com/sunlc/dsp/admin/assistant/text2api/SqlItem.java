package com.sunlc.dsp.admin.assistant.text2api;

import java.util.List;

/**
 * 单段 SQL（结构化产物）。
 */
public class SqlItem {
    private String sqlId;
    private String sql;
    private String purpose;
    private List<String> dependsOn;
    private String outputAlias;
    private String relationDescription;

    public String getSqlId() { return sqlId; }
    public void setSqlId(String sqlId) { this.sqlId = sqlId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public String getOutputAlias() { return outputAlias; }
    public void setOutputAlias(String outputAlias) { this.outputAlias = outputAlias; }
    public String getRelationDescription() { return relationDescription; }
    public void setRelationDescription(String relationDescription) { this.relationDescription = relationDescription; }
}
