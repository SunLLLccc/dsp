package com.sunlc.dsp.admin.assistant.chat;

/**
 * 提问请求（POST /ask 的 body）。
 */
public class AskRequest {
    /** 业务会话 ID（必填） */
    private String sessionId;
    /** 用户问题（必填） */
    private String question;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
