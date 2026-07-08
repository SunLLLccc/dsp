package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.entity.AiChatMessage;

import java.time.LocalDateTime;

/**
 * 消息展示对象。
 */
public class ChatMessageVO {
    private String role;
    private String content;
    private String citations;
    private Integer status;
    private LocalDateTime createdTime;

    public static ChatMessageVO from(AiChatMessage m) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.role = m.getRole();
        vo.content = m.getContent();
        vo.citations = m.getCitations();
        vo.status = m.getStatus();
        vo.createdTime = m.getCreatedTime();
        return vo;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getCitations() { return citations; }
    public Integer getStatus() { return status; }
    public LocalDateTime getCreatedTime() { return createdTime; }
}
