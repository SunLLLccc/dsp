package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("interface_template")
public class InterfaceTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transno;
    private String systemName;
    private String systemCode;
    private String interfaceName;
    private String xmlContent;
    private Integer versionNo;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
