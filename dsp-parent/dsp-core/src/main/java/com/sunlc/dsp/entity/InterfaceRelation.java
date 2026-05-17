package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interface_relation")
public class InterfaceRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transno;
    private Long providerSystemId;
    private Long applicantSystemId;
    private Long approvalId;
    private Integer status;
    private LocalDateTime applyTime;
    private String requirementNo;
    private String applyReason;
    private LocalDateTime offlineTime;
    private String offlineReason;
    private LocalDateTime createdTime;

    @TableField(exist = false)
    private String providerSystemName;
    @TableField(exist = false)
    private String applicantSystemName;
    @TableField(exist = false)
    private String interfaceName;
}
