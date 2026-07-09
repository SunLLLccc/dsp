package com.sunlc.dsp.admin.assistant.text2api;

/**
 * 创建草稿请求（POST /drafts 的 body）。title 可空。
 */
public class CreateDraftRequest {
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
