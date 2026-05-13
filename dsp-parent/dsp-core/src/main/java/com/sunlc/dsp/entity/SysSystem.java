package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_system")
public class SysSystem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Long deptId;
    private String description;
    private Integer status;
    private LocalDateTime createdTime;
}
