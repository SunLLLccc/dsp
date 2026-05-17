package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("approval_info")
public class ApprovalInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalNo;
    private Integer type;
    private String title;
    private Integer status;
    private String applicant;
    private String applicantName;
    private Long applicantDeptId;
    private LocalDateTime applyTime;
    private LocalDateTime withdrawTime;
    private String transno;
    private Integer versionNo;
    private Long applicantSystemId;
    private Long providerSystemId;
    private String requirementNo;
    private String requirementDesc;
    private String applyReason;
    private String downstreamInfo;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    @TableField(exist = false)
    private String applicantSystemName;
    @TableField(exist = false)
    private String providerSystemName;
    @TableField(exist = false)
    private String interfaceName;
    @TableField(exist = false)
    private Integer totalSteps;
    @TableField(exist = false)
    private Integer currentStep;
    @TableField(exist = false)
    private List<ApprovalFlow> flows;
}
