package com.sunlc.dsp.export;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileFormat {
    XLSX("xlsx", "Excel文件"), CSV("csv", "CSV文件"), TXT("txt", "TXT文件");
    private final String extension;
    private final String description;
    public static FileFormat fromExtension(String ext) {
        for (FileFormat format : values()) { if (format.extension.equalsIgnoreCase(ext)) return format; }
        return XLSX;
    }
}
