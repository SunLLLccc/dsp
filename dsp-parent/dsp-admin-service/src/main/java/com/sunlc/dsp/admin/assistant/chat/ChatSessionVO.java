package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.entity.AiChatSession;

import java.time.LocalDateTime;

/**
 * 会话展示对象。
 */
public class ChatSessionVO {
    private String sessionId;
    private String title;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public static ChatSessionVO from(AiChatSession s) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.sessionId = s.getSessionId();
        vo.title = s.getTitle();
        vo.createdTime = s.getCreatedTime();
        vo.updatedTime = s.getUpdatedTime();
        return vo;
    }

    public String getSessionId() { return sessionId; }
    public String getTitle() { return title; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
}
