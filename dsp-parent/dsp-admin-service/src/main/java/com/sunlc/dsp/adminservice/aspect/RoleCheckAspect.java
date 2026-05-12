package com.sunlc.dsp.adminservice.aspect;

import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Aspect
@Component
public class RoleCheckAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint joinPoint, RequireRole requireRole) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无法获取请求上下文");
        }

        @SuppressWarnings("unchecked")
        List<String> userRoles = (List<String>) request.getAttribute("adminRoles");
        if (userRoles == null || userRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无操作权限");
        }

        // ADMIN 角色直接放行
        if (userRoles.contains("ADMIN")) {
            return;
        }

        String[] required = requireRole.value();
        for (String role : required) {
            if (userRoles.contains(role)) {
                return;
            }
        }

        log.warn("权限不足: userRoles={}, required={}", userRoles, Arrays.toString(required));
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "无操作权限，需要角色: " + Arrays.toString(required));
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
