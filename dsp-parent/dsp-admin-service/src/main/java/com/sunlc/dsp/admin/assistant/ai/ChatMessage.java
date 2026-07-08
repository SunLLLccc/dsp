package com.sunlc.dsp.admin.assistant.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 历史消息条目（P2 简单结构，P4 由消息实体映射填充）。
 */
public class ChatMessage {

    /** 角色：user / assistant */
    private final String role;
    /** 正文 */
    private final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /** 便捷构造助手方法：把历史条目组装成不可变列表。 */
    public static List<ChatMessage> copyOf(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
