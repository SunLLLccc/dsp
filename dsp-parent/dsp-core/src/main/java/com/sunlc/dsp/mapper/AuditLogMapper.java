package com.sunlc.dsp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunlc.dsp.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
