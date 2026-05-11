package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审批记录实体
 */
@Data
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接口交易码 */
    private String transno;

    /** 版本号 */
    private Integer versionNo;

    /** 审批状态: 0=待审批, 1=已通过, 2=已驳回 */
    private Integer status;

    /** 申请人 */
    private String applicant;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 审批人 */
    private String approver;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 驳回原因 */
    private String rejectReason;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
