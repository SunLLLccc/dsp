package com.sunlc.dsp.engine.model;

import lombok.Data;

import java.util.List;

/**
 * 单条查询的调试跟踪记录。
 * 仅在 debug 模式下收集，生产普通查询不创建此对象。
 */
@Data
public class DebugTrace {
    /** 查询ID，对应 XML 中 <query id="xxx"> */
    private String queryId;
    /** 查询类型：mysql / http / dubbo / mongo */
    private String type;
    /** 数据源名称 */
    private String datasource;

    /** 最终执行的 SQL（仅 SQL 类型查询有值） */
    private String sql;
    /** SQL 参数列表（仅 SQL 类型查询有值） */
    private List<Object> params;
    /** 分页模式：null / cursor / optimized */
    private String paginationMode;

    /** 结果行数 */
    private int rowCount;
    /** 内部计时起点（System.currentTimeMillis），不序列化到前端 */
    private long startTimeMs;
    /** 内部计时终点，不序列化到前端 */
    private long endTimeMs;
    /** 执行耗时（毫秒） */
    private long elapsedTimeMs;

    /** 执行状态：SUCCESS / ERROR */
    private String status;
    /** 错误信息（仅 status=ERROR 时有值） */
    private String errorMessage;
}
