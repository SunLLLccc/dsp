package com.sunlc.dsp.engine.model;
import lombok.Data;
@Data
public class DynamicSqlConfig {
    private DynamicType type;
    private String test;
    private String sql;
    private String collection;
    private String item;
    private String separator;
    private String open;
    private String close;
    private String foreachSql;
    public enum DynamicType {
        IF, FOREACH
    }
}
