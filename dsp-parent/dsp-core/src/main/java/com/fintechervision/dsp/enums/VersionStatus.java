package com.fintechervision.dsp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 接口版本状态枚举
 * 用于 interface_version.status
 */
@Getter
@AllArgsConstructor
public enum VersionStatus {
    DRAFT(0, "草稿"),
    PENDING(1, "待审批"),
    REJECTED(2, "已驳回"),
    PUBLISHED(3, "已发布");

    private final int code;
    private final String description;

    public static VersionStatus fromCode(int code) {
        for (VersionStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("无效的版本状态: " + code);
    }
}
