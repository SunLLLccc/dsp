package com.fintechervision.dsp.adminservice.interceptor;

import cn.hutool.json.JSONUtil;
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

/**
 * 审计日志切面 — 自动记录管理平台操作
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @Around("execution(* com.fintechervision.dsp.adminservice.controller.*Controller.*(..))")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String operation = getOperation(joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
        String ip = getIp();

        // 提取请求参数
        String requestData = "";
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            try { requestData = JSONUtil.toJsonStr(args); } catch (Exception ignored) {}
        }

        // 提取transno（路径变量中的）
        String transno = "";
        for (Object arg : args) {
            if (arg instanceof String) {
                String strArg = (String) arg;
                // 路径变量中的transno
                if (strArg.startsWith("TRX") || strArg.length() > 5) {
                    transno = strArg;
                    break;
                }
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
            // 从 request 中获取已鉴权的管理员用户名
            String operator = "anonymous";
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    Object adminUser = attrs.getRequest().getAttribute("adminUser");
                    if (adminUser != null) operator = adminUser.toString();
                }
            } catch (Exception ignored) {}
            try {
                auditLogService.log("", transno, operation, requestData, responseCode, costTime, ip, operator);
            } catch (Exception e) {
                log.warn("审计日志记录失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private String getOperation(String className, String methodName) {
        // 根据Controller类型推导操作
        if (className.contains("Interface")) {
            if (methodName.contains("submit") || methodName.contains("approve") || methodName.contains("reject")) return "APPROVAL";
            if (methodName.contains("offline")) return "OFFLINE";
            if (methodName.contains("saveXml") || methodName.contains("version")) return "VERSION";
            return "INTERFACE_MGR";
        }
        if (className.contains("Datasource")) return "DATASOURCE_MGR";
        if (className.contains("AppAuth")) return "APP_AUTH_MGR";
        if (className.contains("Export")) return "EXPORT_MGR";
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
