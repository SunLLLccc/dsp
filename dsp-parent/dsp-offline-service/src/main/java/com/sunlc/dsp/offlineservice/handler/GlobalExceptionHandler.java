package com.sunlc.dsp.offlineservice.handler;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());

        // 认证失败（Token缺失/过期）：返回 401
        if (ErrorCode.TOKEN_MISSING.getCode().equals(e.getCode())
                || ErrorCode.TOKEN_EXPIRED.getCode().equals(e.getCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("OFFLINE", "", e.getCode(), e.getMessage()));
        }
        // 权限不足：返回 403
        if (ErrorCode.ACCESS_DENIED.getCode().equals(e.getCode())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("OFFLINE", "", e.getCode(), e.getMessage()));
        }
        // 参数错误：返回 400
        if (ErrorCode.BAD_REQUEST.getCode().equals(e.getCode())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("OFFLINE", "", e.getCode(), e.getMessage()));
        }
        // 其他业务异常：返回 200，通过 code 区分
        return ResponseEntity.ok(ApiResponse.error("OFFLINE", "", e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleParamException(Exception e) {
        String message = "参数错误";
        if (e instanceof MethodArgumentNotValidException) {
            FieldError fe = ((MethodArgumentNotValidException) e).getBindingResult().getFieldError();
            if (fe != null) message = fe.getDefaultMessage();
        } else if (e instanceof BindException) {
            FieldError fe = ((BindException) e).getFieldError();
            if (fe != null) message = fe.getDefaultMessage();
        } else if (e instanceof MissingServletRequestParameterException) {
            message = "缺少必要参数: " + ((MissingServletRequestParameterException) e).getParameterName();
        } else if (e instanceof MethodArgumentTypeMismatchException) {
            MethodArgumentTypeMismatchException me = (MethodArgumentTypeMismatchException) e;
            message = "参数类型错误: " + me.getName();
        } else if (e instanceof HttpMessageNotReadableException) {
            message = "请求体解析失败";
        }
        log.warn("参数异常: type={}, message={}", e.getClass().getSimpleName(), message);
        return ApiResponse.error("OFFLINE", "", ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error("OFFLINE", "", ErrorCode.SYSTEM_ERROR);
    }
}
