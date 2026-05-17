package com.sunlc.dsp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ApprovalType {
    NEW_INTERFACE(1, "新增接口"),
    MODIFY_INTERFACE(2, "修改接口"),
    APPLY_INTERFACE(3, "申请接口");

    private final int code;
    private final String description;

    public static ApprovalType fromCode(int code) {
        for (ApprovalType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("无效的审批类型: " + code);
    }
}
