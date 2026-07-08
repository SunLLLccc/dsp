package com.sunlc.dsp.admin.assistant.chat;

/**
 * 创建会话请求。title 可空（后续取首问摘要）。
 */
public class CreateSessionRequest {
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
