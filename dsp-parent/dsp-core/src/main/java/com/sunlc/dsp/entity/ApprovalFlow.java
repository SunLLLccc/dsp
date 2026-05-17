package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_flow")
public class ApprovalFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalId;
    private Integer stepNo;
    private String stepName;
    private Integer status;
    private Long deptId;
    private String approver;
    private String approverName;
    private LocalDateTime approveTime;
    private String rejectReason;
    private LocalDateTime createdTime;
}
