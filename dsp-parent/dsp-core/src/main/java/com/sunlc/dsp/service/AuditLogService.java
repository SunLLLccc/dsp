package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.AuditLog;

/**
 * 审计日志服务
 */
public interface AuditLogService extends IService<AuditLog> {

    /**
     * 记录审计日志
     */
    void log(String appId, String transno, String operation, String requestData, String responseCode, Long costTime, String ip, String operator);

    /**
     * 分页查询审计日志
     */
    Page<AuditLog> listAuditLog(Integer pageNum, Integer pageSize, String transno, String operation, String appId);
}
