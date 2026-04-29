package com.fintechervision.dsp.dataservice.interceptor;

import cn.hutool.json.JSONUtil;
import com.fintechervision.dsp.common.model.ApiRequest;
import com.fintechervision.dsp.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 审计日志切面 — 自动记录数据查询和导出操作
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @Around("execution(* com.fintechervision.dsp.dataservice.controller.DataApiController.*(..))")
    @SuppressWarnings("unchecked")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String operation = getOperation(joinPoint.getSignature().getName());
        String transno = "";
        String appId = "";
        String requestData = "";
        String ip = getIp();

        // 提取参数
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            transno = args[0] instanceof String ? (String) args[0] : "";
            if (args.length > 1 && args[1] instanceof ApiRequest) {
                ApiRequest<Map<String, Object>> request = (ApiRequest<Map<String, Object>>) args[1];
                appId = request.getHead() != null && request.getHead().getAppId() != null ? request.getHead().getAppId() : "";
                try { requestData = JSONUtil.toJsonStr(request.getRequestData()); } catch (Exception ignored) {}
            } else if (args.length > 1) {
                try { requestData = JSONUtil.toJsonStr(args[1]); } catch (Exception ignored) {}
            }
        }

        String responseCode = "0000";
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            responseCode = "5000";
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            try {
                auditLogService.log(appId, transno, operation, requestData, responseCode, costTime, ip, appId);
            } catch (Exception e) {
                log.warn("审计日志记录失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private String getOperation(String methodName) {
        if (methodName.contains("query")) return "QUERY";
        if (methodName.contains("export")) return "EXPORT";
        return methodName.toUpperCase();
    }

    private String getIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "";
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) ip = request.getHeader("X-Real-IP");
            if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
            return ip;
        } catch (Exception e) {
            return "";
        }
    }
}
