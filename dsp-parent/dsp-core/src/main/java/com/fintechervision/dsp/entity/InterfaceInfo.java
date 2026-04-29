package com.fintechervision.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("interface_info")
public class InterfaceInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transno;
    private String name;
    private String protocolType;
    private Integer status;
    private String description;
    private Integer currentVersion;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
