package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("interface_template_history")
public class InterfaceTemplateHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String transno;
    private String systemName;
    private String interfaceName;
    private String xmlContent;
    private Integer versionNo;
    private String changeLog;
    private String createdBy;
    private LocalDateTime createdTime;
}
