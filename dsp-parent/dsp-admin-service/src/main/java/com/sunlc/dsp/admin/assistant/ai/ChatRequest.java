package com.sunlc.dsp.admin.assistant.ai;

import java.util.List;

/**
 * AI 网关流式问答请求。
 * <p>
 * P2 只定义字段契约，不实现检索；{@link #retrievalContext} 为 P3 预留，
 * P2 阶段 gateway 会把它拼到 system prompt 之后的上下文里（可为空）。
 */
public class ChatRequest {

    /** 当前用户提问（必填）。 */
    private final String userMessage;

    /** 系统指令（可空），用于约束助手行为。 */
    private final String systemPrompt;

    /** 历史消息（按时间正序，最早在前），可空。 */
    private final List<ChatMessage> history;

    /**
     * 检索上下文（P3 产出，P2 预留）。可空。
     * 非空时 gateway 将其作为附加上下文提交给模型。
     */
    private final String retrievalContext;

    /**
     * 引用来源 JSON（P3 产出，P2 预留）。可空。
     * 语义：gateway 在流式开始/结束时，若本字段非空，则通过 {@link StreamHandler#onCitations(String)} 回调一次，
     * 把引用透传给 SSE 层。即「P3 把 citations 塞进 request，gateway 回调给 SSE」的路径。
     */
    private final String citations;

    public ChatRequest(String userMessage, String systemPrompt, List<ChatMessage> history,
                       String retrievalContext, String citations) {
        this.userMessage = userMessage;
        this.systemPrompt = systemPrompt;
        // 防御性不可变副本：避免调用方后续修改 list 影响 request
        this.history = ChatMessage.copyOf(history);
        this.retrievalContext = retrievalContext;
        this.citations = citations;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public String getRetrievalContext() {
        return retrievalContext;
    }

    public String getCitations() {
        return citations;
    }
}
