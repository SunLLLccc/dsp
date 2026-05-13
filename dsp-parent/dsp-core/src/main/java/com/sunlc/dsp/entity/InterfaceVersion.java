package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("interface_version")
public class InterfaceVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transno;
    private Integer versionNo;
    private String inputSchema;
    private String outputSchema;
    private String changeLog;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime publishedTime;
}
