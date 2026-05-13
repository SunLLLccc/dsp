package com.sunlc.dsp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 接口/模板状态枚举
 * 用于 interface_info.status、interface_template.status
 */
@Getter
@AllArgsConstructor
public enum InterfaceStatus {
    DRAFT(0, "草稿"),
    PENDING(1, "待审批"),
    REJECTED(2, "已驳回"),
    PUBLISHED(3, "已发布"),
    OFFLINE(4, "已下线");

    private final int code;
    private final String description;

    public static InterfaceStatus fromCode(int code) {
        for (InterfaceStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("无效的接口状态: " + code);
    }
}
