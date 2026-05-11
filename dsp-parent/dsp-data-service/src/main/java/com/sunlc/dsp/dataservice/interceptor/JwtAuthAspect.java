package com.sunlc.dsp.dataservice.interceptor;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiRequest;
import com.sunlc.dsp.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class JwtAuthAspect {

    private final JwtUtil jwtUtil;
    private static final long TIMESTAMP_TOLERANCE_MINUTES = 5;

    @Before("execution(* com.sunlc.dsp.dataservice.controller.DataApiController.*(..)) && args(transno, request, ..)")
    public void authenticate(String transno, ApiRequest<?> request) {
        if (request == null || request.getHead() == null) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "请求头信息缺失");
        }

        String token = request.getHead().getToken();
        String appId = request.getHead().getAppId();
        String timestamp = request.getHead().getTimestamp();
        String traceId = request.getHead().getTraceId();

        HttpServletRequest httpRequest = getCurrentRequest();
        if (httpRequest != null) {
            httpRequest.setAttribute("traceId", traceId);
            httpRequest.setAttribute("appId", appId);
        }

        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING);
        }

        if (timestamp != null && !timestamp.isEmpty()) {
            validateTimestamp(timestamp);
        }

        Map<String, Object> claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.warn("JWT解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        String tokenAppId = (String) claims.get("appId");
        if (appId != null && !appId.equals(tokenAppId)) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "appId与Token不匹配");
        }

        @SuppressWarnings("unchecked")
        List<String> allowedTransnos = (List<String>) claims.get("allowedTransnos");
        if (allowedTransnos != null && !allowedTransnos.contains("*")) {
            if (!allowedTransnos.contains(transno)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }

        log.debug("JWT鉴权通过: transno={}, appId={}", transno, appId);
    }

    private void validateTimestamp(String timestamp) {
        try {
            LocalDateTime requestTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            long diffMinutes = java.time.Duration.between(requestTime, now).abs().toMinutes();
            if (diffMinutes > TIMESTAMP_TOLERANCE_MINUTES) {
                throw new BusinessException(ErrorCode.TIMESTAMP_INVALID);
            }
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.TIMESTAMP_INVALID, "时间戳格式错误");
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
