package com.sunlc.dsp.adminservice.interceptor;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 管理平台 JWT 鉴权拦截器
 * 从 HTTP Header 中提取 Admin-Token 进行验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Admin-Token");

        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "缺少 Admin-Token 请求头");
        }

        Map<String, Object> claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.warn("管理端JWT解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        String adminUser = (String) claims.get("appId");
        if (adminUser == null || !adminUser.startsWith("admin:")) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "无效的管理端Token");
        }
        adminUser = adminUser.substring(6);

        // 将管理员信息存入 request，供后续使用
        request.setAttribute("adminUser", adminUser);
        if (claims.get("userId") != null) {
            request.setAttribute("adminUserId", claims.get("userId"));
        }
        if (claims.get("roles") != null) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            request.setAttribute("adminRoles", roles);
        }
        if (claims.get("deptId") != null) {
            request.setAttribute("adminDeptId", claims.get("deptId"));
        }
        log.debug("管理端鉴权通过: adminUser={}", adminUser);
        return true;
    }
}
