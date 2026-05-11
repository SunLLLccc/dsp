package com.sunlc.dsp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用/禁用状态枚举
 * 用于 datasource_config.status、app_auth.status
 */
@Getter
@AllArgsConstructor
public enum CommonStatus {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String description;

    public static CommonStatus fromCode(int code) {
        for (CommonStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("无效的状态: " + code);
    }
}
