package com.sunlc.dsp.engine.model;
import lombok.Data;
@Data
public class PaginationConfig {
    private PaginationMode mode;
    private String orderBy;
    private String pageSizeParam = "pageSize";
    private String lastIdParam = "lastId";
    private String pageNumParam = "pageNum";
    private int defaultPageSize = 20;
    private int maxPageSize = 1000;
    public enum PaginationMode {
        CURSOR, OPTIMIZED
    }
}
