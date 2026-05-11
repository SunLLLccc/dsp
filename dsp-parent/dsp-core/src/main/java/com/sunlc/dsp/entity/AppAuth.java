package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("app_auth")
public class AppAuth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String appName;
    private String appSecret;
    private String allowedTransnos;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
