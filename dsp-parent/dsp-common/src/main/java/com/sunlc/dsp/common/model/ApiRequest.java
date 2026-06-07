package com.sunlc.dsp.common.model;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 统一请求报文
 */
@Data
public class ApiRequest<T> {

    @NotNull(message = "head不能为空")
    @Valid
    private RequestHead head;

    private T requestData;
}
