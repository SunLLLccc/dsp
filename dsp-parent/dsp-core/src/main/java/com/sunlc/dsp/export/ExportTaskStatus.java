package com.sunlc.dsp.export;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExportTaskStatus {
    PENDING(0, "待处理"), PROCESSING(1, "处理中"), COMPLETED(2, "已完成"), FAILED(3, "失败");
    private final int code;
    private final String description;
    public static ExportTaskStatus fromCode(int code) {
        for (ExportTaskStatus status : values()) { if (status.code == code) return status; }
        throw new IllegalArgumentException("无效的任务状态: " + code);
    }
}
