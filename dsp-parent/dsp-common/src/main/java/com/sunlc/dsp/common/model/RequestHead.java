package com.sunlc.dsp.common.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 统一请求报文头
 */
@Data
public class RequestHead {

    @NotBlank(message = "token不能为空")
    private String token;

    @NotBlank(message = "appId不能为空")
    private String appId;

    @NotBlank(message = "timestamp不能为空")
    private String timestamp;

    private String traceId;

    private String transno;
}
