package com.fintechervision.dsp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 接口-数据源关联实体
 */
@Data
@TableName("interface_datasource")
public class InterfaceDatasource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接口编码 */
    private String transno;

    /** 数据源名称 */
    private String dsName;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
