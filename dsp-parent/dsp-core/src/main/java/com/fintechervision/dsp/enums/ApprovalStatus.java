package com.fintechervision.dsp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审批记录状态枚举
 * 用于 approval_record.status
 */
@Getter
@AllArgsConstructor
public enum ApprovalStatus {
    PENDING(0, "待审批"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String description;

    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("无效的审批状态: " + code);
    }
}
