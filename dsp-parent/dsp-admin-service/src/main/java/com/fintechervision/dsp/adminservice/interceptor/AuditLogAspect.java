package com.fintechervision.dsp.adminservice.interceptor;

import cn.hutool.json.JSONUtil;
import com.fintechervision.dsp.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

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

        // 提取transno（从@PathVariable("transno")注解精确提取）
        String transno = extractPathVariable(args, joinPoint, "transno");

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

    /**
     * 从方法参数注解中精确提取指定名称的 @PathVariable 值
     */
    private String extractPathVariable(Object[] args, ProceedingJoinPoint joinPoint, String pathVarName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            PathVariable pv = parameters[i].getAnnotation(PathVariable.class);
            if (pv != null) {
                String name = pv.value().isEmpty() ? pv.name() : pv.value();
                if (name.isEmpty()) {
                    name = parameters[i].getName();
                }
                if (pathVarName.equals(name) && args[i] != null) {
                    return args[i].toString();
                }
            }
        }
        return "";
    }
}
