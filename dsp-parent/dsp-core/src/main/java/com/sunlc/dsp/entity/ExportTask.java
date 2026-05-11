package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("export_task")
public class ExportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transno;
    private String paramsSnapshot;
    private Integer exportType;
    private String fileFormat;
    private Integer status;
    private String filePath;
    private Long totalRows;
    private Integer progress;
    private String errorMsg;
    private String applyUser;
    private LocalDateTime createdTime;
    private LocalDateTime finishedTime;
}
