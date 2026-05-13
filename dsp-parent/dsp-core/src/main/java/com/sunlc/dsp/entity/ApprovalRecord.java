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

    /** 类型：0-版本审批 1-接口申请 */
    private Integer type;

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

    /** 申请方部门ID */
    private Long applicantDeptId;

    /** 申请方系统ID */
    private Long applicantSystemId;

    /** 服务方系统ID */
    private Long providerSystemId;

    /** 需求编号 */
    private String requirementNo;

    /** 需求描述 */
    private String requirementDesc;

    /** 申请原因 */
    private String applyReason;

    /** 下游接口/页面 */
    private String downstreamInfo;

    /** 当前审批步骤：1-服务方部门经理 2-申请方部门经理 */
    private Integer currentStep;

    /** 第二步审批人 */
    private String approver2;

    /** 第二步审批时间 */
    private LocalDateTime approveTime2;

    /** 创建时间 */
    private LocalDateTime createdTime;

    // ========== 展示用冗余字段（不映射数据库） ==========

    @TableField(exist = false)
    private String applicantSystemName;

    @TableField(exist = false)
    private String providerSystemName;

    @TableField(exist = false)
    private String interfaceName;
}
