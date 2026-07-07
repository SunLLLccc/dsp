package com.sunlc.dsp.common.model;

import lombok.Data;

/**
 * 导出分页信息 — 描述接口主导出查询的分页配置
 */
@Data
public class PaginationExportInfo {
    /** 分页模式: "cursor", "optimized", null(无分页) */
    private String mode;
    /** 排序字段 */
    private String orderBy;
    /** 页大小参数名 */
    private String pageSizeParam;
    /** 页码参数名 */
    private String pageNumParam;
    /** 游标ID参数名 */
    private String lastIdParam;
    /** 最大页大小 */
    private int maxPageSize;
}
