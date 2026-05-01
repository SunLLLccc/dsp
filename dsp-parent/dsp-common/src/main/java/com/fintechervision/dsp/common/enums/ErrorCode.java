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
    BAD_REQUEST("4100", "请求参数错误"),
    APPROVAL_RECORD_NOT_FOUND("4101", "审批记录不存在"),
    APPROVAL_DUPLICATE("4102", "重复提交审批"),
    APPROVAL_ALREADY_PROCESSED("4103", "审批记录已处理"),
    VERSION_NOT_FOUND("4104", "接口版本不存在"),
    VERSION_STATUS_INVALID("4105", "版本状态不允许此操作"),
    APP_NOT_FOUND("4106", "应用不存在或已禁用"),
    DATASOURCE_BIND_DUPLICATE("4107", "数据源关联已存在"),
    DATASOURCE_NOT_CONFIGURED("4108", "数据源未配置"),
    SYSTEM_ERROR("5001", "系统内部错误"),
    DATASOURCE_ERROR("5002", "数据源连接异常"),
    EXPORT_ERROR("5003", "导出失败");

    private final String code;
    private final String message;
}
