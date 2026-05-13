package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作审计日志实体
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用ID */
    private String appId;

    /** 接口编码 */
    private String transno;

    /** 操作类型：QUERY/EXPORT/PUBLISH/OFFLINE等 */
    private String operation;

    /** 请求参数 */
    private String requestData;

    /** 响应状态码 */
    private String responseCode;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 请求IP */
    private String ip;

    /** 操作人 */
    private String operator;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
