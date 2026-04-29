package com.fintechervision.dsp.common.model;

import com.fintechervision.dsp.common.enums.ErrorCode;
import lombok.Data;

/**
 * 统一响应报文
 */
@Data
public class ApiResponse<T> {

    private ResponseHead head;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String transno, String traceId, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setHead(ResponseHead.of(transno, traceId));
        response.setCode(ErrorCode.SUCCESS.getCode());
        response.setMessage(ErrorCode.SUCCESS.getMessage());
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(String transno, String traceId, ErrorCode errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setHead(ResponseHead.of(transno, traceId));
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return response;
    }

    public static <T> ApiResponse<T> error(String transno, String traceId, ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setHead(ResponseHead.of(transno, traceId));
        response.setCode(errorCode.getCode());
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponse<T> error(String transno, String traceId, String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setHead(ResponseHead.of(transno, traceId));
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
