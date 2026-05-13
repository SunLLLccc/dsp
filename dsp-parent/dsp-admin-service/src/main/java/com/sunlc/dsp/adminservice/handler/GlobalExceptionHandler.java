package com.sunlc.dsp.adminservice.handler;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        // 角色权限不足：返回 403，携带错误信息让前端展示
        if (ErrorCode.ACCESS_DENIED.getCode().equals(e.getCode())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("ADMIN", "", e.getCode(), e.getMessage()));
        }
        // 认证失败（Token缺失/过期）：返回 401
        if (ErrorCode.TOKEN_MISSING.getCode().equals(e.getCode())
                || ErrorCode.TOKEN_EXPIRED.getCode().equals(e.getCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("ADMIN", "", e.getCode(), e.getMessage()));
        }
        // 其他业务异常：返回 200，通过 code 区分
        return ResponseEntity.ok(ApiResponse.error("ADMIN", "", e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error("ADMIN", "", ErrorCode.SYSTEM_ERROR);
    }
}
