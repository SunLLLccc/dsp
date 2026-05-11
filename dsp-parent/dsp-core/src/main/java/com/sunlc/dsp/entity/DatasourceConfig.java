package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("datasource_config")
public class DatasourceConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dsName;
    private String dsType;
    private String jdbcUrl;
    private String username;
    private String password;
    private String extraConfig;
    private String poolConfig;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
