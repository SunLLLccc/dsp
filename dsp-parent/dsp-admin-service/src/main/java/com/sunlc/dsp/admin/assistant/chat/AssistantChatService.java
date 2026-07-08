package com.sunlc.dsp.admin.assistant.chat;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.admin.assistant.ai.AiGateway;
import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import com.sunlc.dsp.admin.assistant.ai.ChatMessage;
import com.sunlc.dsp.admin.assistant.ai.ChatRequest;
import com.sunlc.dsp.admin.assistant.ai.StreamCancelledException;
import com.sunlc.dsp.admin.assistant.ai.StreamHandle;
import com.sunlc.dsp.admin.assistant.retrieval.RetrievalResult;
import com.sunlc.dsp.admin.assistant.retrieval.RetrievalService;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.AiChatMessage;
import com.sunlc.dsp.entity.AiChatSession;
import com.sunlc.dsp.service.AiChatMessageService;
import com.sunlc.dsp.service.AiChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 智能助手 chat 编排服务。
 * <p>
 * 落实 P1 契约：Controller 只注入本类，不直接注入 {@link AiChatMessageService}；
 * 本类内部先 {@code getOwnedSession} 校验会话归属，再查消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    /** 消息状态 */
    private static final int STATUS_GENERATING = 0;
    private static final int STATUS_COMPLETED = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_CANCELLED = 3;

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /** 历史消息最大条数（建议改1：限制上下文长度）。 */
    private static final int MAX_HISTORY_MESSAGES = 20;
    /** assistant 回答最大字符数（建议改2：delta buffer 上限）。 */
    private static final int MAX_RESPONSE_CHARS = 16384;
    /** 单次问题最大长度。 */
    private static final int MAX_QUESTION_LENGTH = 4000;

    private final AiChatSessionService sessionService;
    private final AiChatMessageService messageService;
    private final AiGateway aiGateway;
    private final RetrievalService retrievalService;
    private final ChatConcurrencyLimiter concurrencyLimiter;
    private final AssistantProperties assistantProperties;

    // ===== 会话管理 =====

    public ChatSessionVO createSession(Long userId, String userName, String title) {
        AiChatSession session = new AiChatSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setUserName(userName);
        session.setTitle(title == null || title.isBlank() ? "新会话" : title);
        session.setCreatedTime(LocalDateTime.now());
        session.setUpdatedTime(LocalDateTime.now());
        sessionService.save(session);
        return ChatSessionVO.from(session);
    }

    public IPage<ChatSessionVO> listSessions(Long userId, int pageNum, int pageSize) {
        Page<AiChatSession> page = new Page<>(pageNum, pageSize);
        IPage<AiChatSession> result = sessionService.lambdaQuery()
                .eq(AiChatSession::getUserId, userId)
                .orderByDesc(AiChatSession::getUpdatedTime)
                .page(page);
        return result.convert(ChatSessionVO::from);
    }

    public List<ChatMessageVO> listOwnedMessages(String sessionId, Long userId) {
        getOwnedSessionOrThrow(sessionId, userId);
        List<AiChatMessage> messages = messageService.listBySession(sessionId);
        return messages.stream().map(ChatMessageVO::from).collect(Collectors.toList());
    }

    public void deleteSession(String sessionId, Long userId) {
        AiChatSession session = getOwnedSessionOrThrow(sessionId, userId);
        sessionService.removeById(session.getId());
    }

    // ===== ask 编排 =====

    public ChatSseEmitter ask(AskRequest request, Long userId) {
        validateAskRequest(request);
        getOwnedSessionOrThrow(request.getSessionId(), userId);

        if (!concurrencyLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前会话并发数已达上限，请稍后再试");
        }

        // 落 user 消息
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(request.getSessionId());
        userMsg.setRole(ROLE_USER);
        userMsg.setContent(request.getQuestion());
        userMsg.setStatus(STATUS_COMPLETED);
        userMsg.setCreatedTime(LocalDateTime.now());
        messageService.save(userMsg);

        // 检索
        RetrievalResult retrieval = retrievalService.retrieve(request.getQuestion());
        String retrievalContext = retrieval.isProjectRelated() ? retrieval.getRetrievalContext() : "";
        String citationsJson = retrieval.isProjectRelated() ? retrieval.getCitationsJson() : "[]";

        // assistant 占位消息（status=0 生成中）
        AiChatMessage assistantMsg = new AiChatMessage();
        assistantMsg.setSessionId(request.getSessionId());
        assistantMsg.setRole(ROLE_ASSISTANT);
        assistantMsg.setContent("");
        assistantMsg.setCitations(citationsJson);
        assistantMsg.setStatus(STATUS_GENERATING);
        assistantMsg.setCreatedTime(LocalDateTime.now());
        messageService.save(assistantMsg);

        // delta 内存累积 buffer（带上限）
        StringBuilder contentBuffer = new StringBuilder();

        // 构造 AI 请求
        ChatRequest chatRequest = new ChatRequest(
                request.getQuestion(), buildSystemPrompt(),
                mapHistory(request.getSessionId(), userMsg.getId()),
                retrievalContext, citationsJson);

        // 必改2：终止回调必须在 streamChat 前绑定（streamChat 可能在返回前同步触发回调）
        ChatSseEmitter emitter = new ChatSseEmitter(
                assistantProperties.getSseTimeoutMs(),
                error -> onTerminate(error, assistantMsg, contentBuffer, userId));
        emitter.sendStart();

        // 调 AI 网关（流式）——此时 terminationCallback 已绑定，同步回调也能正确处理
        StreamHandle handle;
        try {
            handle = aiGateway.streamChat(
                    chatRequest,
                    new DeltaCollectingHandler(emitter, contentBuffer, () -> onBufferOverflow(emitter)));
        } catch (RuntimeException e) {
            // 必改2：streamChat 直接 throw（网关实现 bug / 构造异常等）
            // 走 emitter.onError → terminate → onTerminate（落 status=2 + release）
            emitter.onError(e);
            return emitter;
        }
        emitter.bindHandle(handle);

        return emitter;
    }

    // ===== 终止处理（必改3：区分完成/失败/取消）=====

    /** 终止回调：根据 Throwable 类型决定消息状态。 */
    private void onTerminate(Throwable error, AiChatMessage assistantMsg, StringBuilder contentBuffer, Long userId) {
        int status;
        if (error == null) {
            status = STATUS_COMPLETED;
        } else if (error instanceof StreamCancelledException) {
            status = STATUS_CANCELLED;
        } else {
            status = STATUS_FAILED;
        }
        finalizeAssistantMessage(assistantMsg, contentBuffer.toString(), status, userId);
    }

    /** 更新 assistant 消息状态与内容，释放并发计数。完成/失败/取消都保留已生成部分内容。 */
    private void finalizeAssistantMessage(AiChatMessage msg, String content, int status, Long userId) {
        try {
            // 完成写全部内容；失败/取消也保留已生成的部分内容，方便用户看到模型输出
            msg.setContent(content);
            msg.setStatus(status);
            messageService.updateById(msg);
            updateSessionTime(msg.getSessionId());
        } catch (Exception e) {
            log.warn("更新 assistant 消息失败", e);
        } finally {
            concurrencyLimiter.release(userId);
        }
    }

    /** delta buffer 超限：取消生成，后续 terminate 会落 status=FAILED。 */
    private void onBufferOverflow(ChatSseEmitter emitter) {
        log.warn("AI 回答超过 {} 字符上限，取消生成", MAX_RESPONSE_CHARS);
        emitter.onError(new IllegalStateException("AI 回答超过长度上限，已停止生成"));
    }

    private void updateSessionTime(String sessionId) {
        try {
            AiChatSession s = sessionService.lambdaQuery()
                    .eq(AiChatSession::getSessionId, sessionId).one();
            if (s != null) {
                s.setUpdatedTime(LocalDateTime.now());
                sessionService.updateById(s);
            }
        } catch (Exception e) {
            log.debug("更新 session updatedTime 失败", e);
        }
    }

    private void validateAskRequest(AskRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "sessionId 不能为空");
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question 不能为空");
        }
        if (request.getQuestion().length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "问题长度超过上限 " + MAX_QUESTION_LENGTH + " 字符");
        }
    }

    private String buildSystemPrompt() {
        return "你是 DSP 智能助手，专注于解答本项目相关问题。"
                + "回答应基于检索到的本地文档/源码引用（citations），"
                + "若无引用依据请谨慎回答或向用户追问，不要编造不存在的项目事实。";
    }

    /**
     * 历史映射：只取 status=1（完成）的消息，最近 N 条；排除刚保存的当前 user 消息（按 id）。
     * 避免当前问题同时通过 history 和 userMessage 重复传给模型。
     */
    private List<ChatMessage> mapHistory(String sessionId, Long excludeMsgId) {
        List<AiChatMessage> all = messageService.listBySession(sessionId);
        List<AiChatMessage> filtered = new ArrayList<>();
        // 倒序，取最近 MAX_HISTORY_MESSAGES 条且 status=1，排除当前 user 消息
        int count = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            AiChatMessage m = all.get(i);
            // 排除刚保存的当前 user 消息（避免重复）
            if (excludeMsgId != null && excludeMsgId.equals(m.getId())) {
                continue;
            }
            if (m.getStatus() != null && m.getStatus() == STATUS_COMPLETED) {
                filtered.add(m);
                count++;
                if (count >= MAX_HISTORY_MESSAGES) {
                    break;
                }
            }
        }
        // 反转为正序
        java.util.Collections.reverse(filtered);
        return filtered.stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    private AiChatSession getOwnedSessionOrThrow(String sessionId, Long userId) {
        AiChatSession session = sessionService.getOwnedSession(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "会话不存在或无权访问");
        }
        return session;
    }

    /** delta 收集 handler：emitter 推送 + buffer 累积（带上限保护）。 */
    private static final class DeltaCollectingHandler implements com.sunlc.dsp.admin.assistant.ai.StreamHandler {
        private final ChatSseEmitter emitter;
        private final StringBuilder buffer;
        private final Runnable onOverflow;
        /** 超限/终止标记：置 true 后不再 append，防止 buffer 在关闭后继续增长。 */
        private volatile boolean overflowed = false;

        DeltaCollectingHandler(ChatSseEmitter emitter, StringBuilder buffer, Runnable onOverflow) {
            this.emitter = emitter;
            this.buffer = buffer;
            this.onOverflow = onOverflow;
        }

        @Override
        public void onDelta(String text) {
            if (overflowed || text == null || text.isEmpty()) {
                return;
            }
            buffer.append(text);
            emitter.onDelta(text);
            if (buffer.length() > MAX_RESPONSE_CHARS) {
                overflowed = true;
                onOverflow.run();
            }
        }

        @Override
        public void onCitations(String citationsJson) {
            emitter.onCitations(citationsJson);
        }

        @Override
        public void onComplete() {
            emitter.onComplete();
        }

        @Override
        public void onError(Throwable error) {
            emitter.onError(error);
        }
    }
}
