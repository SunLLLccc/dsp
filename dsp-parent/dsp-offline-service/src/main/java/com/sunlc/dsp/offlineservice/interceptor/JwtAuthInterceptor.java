package com.sunlc.dsp.offlineservice.interceptor;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 离线导出服务 JWT 鉴权拦截器
 * 从 HTTP Header 中提取 token 和 appId 进行验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("X-Token");
        String appId = request.getHeader("X-App-Id");

        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "缺少 X-Token 请求头");
        }

        Map<String, Object> claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.warn("JWT解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        String tokenAppId = (String) claims.get("appId");
        if (appId != null && !appId.isEmpty() && !appId.equals(tokenAppId)) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "appId与Token不匹配");
        }

        request.setAttribute("appId", tokenAppId);

        @SuppressWarnings("unchecked")
        List<String> allowedTransnos = (List<String>) claims.get("allowedTransnos");
        request.setAttribute("allowedTransnos", allowedTransnos);

        log.debug("离线服务JWT鉴权通过: appId={}", tokenAppId);
        return true;
    }
}
