package com.fintechervision.dsp.export;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExportType {
    ONLINE(1, "在线导出"), OFFLINE(2, "离线导出");
    private final int code;
    private final String description;
    public static ExportType fromCode(int code) {
        for (ExportType type : values()) { if (type.code == code) return type; }
        throw new IllegalArgumentException("无效的导出类型: " + code);
    }
}
