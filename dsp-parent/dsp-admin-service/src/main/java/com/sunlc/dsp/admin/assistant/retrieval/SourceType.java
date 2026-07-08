package com.sunlc.dsp.admin.assistant.retrieval;

/**
 * 引用类型：文档 / 源码。
 */
public enum SourceType {
    /** 本地文档（md/html/xml/txt 等）。 */
    DOC("doc"),
    /** 源码（java/js/vue 等）。 */
    SOURCE("source");

    private final String code;

    SourceType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
