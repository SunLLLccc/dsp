package com.fintechervision.dsp.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS("0000", "成功"),
    TOKEN_MISSING("4001", "Token缺失或格式错误"),
    TOKEN_EXPIRED("4002", "Token已过期"),
    ACCESS_DENIED("4003", "无权访问该接口"),
    INTERFACE_NOT_FOUND("4004", "接口不存在"),
    TIMESTAMP_INVALID("4005", "时间戳超出允许范围"),
    SYSTEM_ERROR("5001", "系统内部错误"),
    DATASOURCE_ERROR("5002", "数据源连接异常");

    private final String code;
    private final String message;
}
