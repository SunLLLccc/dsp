package com.fintechervision.dsp.dataservice.handler;

import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        String transno = extractTransno(request);
        String traceId = extractTraceId(request);
        log.warn("业务异常: transno={}, code={}, message={}", transno, e.getCode(), e.getMessage());
        return ApiResponse.error(transno, traceId, e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception e, HttpServletRequest request) {
        String transno = extractTransno(request);
        String traceId = extractTraceId(request);
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException) {
            FieldError fieldError = ((MethodArgumentNotValidException) e).getBindingResult().getFieldError();
            if (fieldError != null) message = fieldError.getDefaultMessage();
        } else if (e instanceof BindException) {
            FieldError fieldError = ((BindException) e).getFieldError();
            if (fieldError != null) message = fieldError.getDefaultMessage();
        }
        log.warn("参数校验异常: transno={}, message={}", transno, message);
        return ApiResponse.error(transno, traceId, ErrorCode.SYSTEM_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        String transno = extractTransno(request);
        String traceId = extractTraceId(request);
        log.error("系统异常: transno={}", transno, e);
        return ApiResponse.error(transno, traceId, ErrorCode.SYSTEM_ERROR);
    }

    private String extractTransno(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("/dsp/api/")) {
            return uri.substring(uri.lastIndexOf("/") + 1);
        }
        return "UNKNOWN";
    }

    private String extractTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        return traceId != null ? traceId.toString() : "";
    }
}
