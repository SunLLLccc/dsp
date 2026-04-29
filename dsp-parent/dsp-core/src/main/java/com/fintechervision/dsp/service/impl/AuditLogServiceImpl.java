package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fintechervision.dsp.entity.AuditLog;
import com.fintechervision.dsp.mapper.AuditLogMapper;
import com.fintechervision.dsp.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志服务实现
 */
@Slf4j
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {

    @Override
    @Async
    public void log(String appId, String transno, String operation, String requestData, String responseCode, Long costTime, String ip, String operator) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAppId(appId);
        auditLog.setTransno(transno);
        auditLog.setOperation(operation);
        auditLog.setRequestData(truncate(requestData, 4000));
        auditLog.setResponseCode(responseCode);
        auditLog.setCostTime(costTime);
        auditLog.setIp(ip);
        auditLog.setOperator(operator);
        auditLog.setCreatedTime(LocalDateTime.now());
        save(auditLog);
    }

    @Override
    public Page<AuditLog> listAuditLog(Integer pageNum, Integer pageSize, String transno, String operation, String appId) {
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (transno != null && !transno.isEmpty()) {
            wrapper.like(AuditLog::getTransno, transno);
        }
        if (operation != null && !operation.isEmpty()) {
            wrapper.eq(AuditLog::getOperation, operation);
        }
        if (appId != null && !appId.isEmpty()) {
            wrapper.eq(AuditLog::getAppId, appId);
        }
        wrapper.orderByDesc(AuditLog::getCreatedTime);
        return page(page, wrapper);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
