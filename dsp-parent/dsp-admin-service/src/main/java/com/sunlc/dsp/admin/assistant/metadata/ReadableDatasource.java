package com.sunlc.dsp.admin.assistant.metadata;

/**
 * 可读元数据的数据源摘要（不含连接串/密码）。
 */
public class ReadableDatasource {
    private String dsName;
    private String dsType;

    public ReadableDatasource() {}

    public ReadableDatasource(String dsName, String dsType) {
        this.dsName = dsName;
        this.dsType = dsType;
    }

    public String getDsName() { return dsName; }
    public void setDsName(String dsName) { this.dsName = dsName; }
    public String getDsType() { return dsType; }
    public void setDsType(String dsType) { this.dsType = dsType; }
}
