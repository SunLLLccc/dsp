package com.fintechervision.dsp.common.model;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 统一请求报文
 */
@Data
public class ApiRequest<T> {

    @NotNull(message = "head不能为空")
    private RequestHead head;

    private T requestData;
}
