package com.sunlc.dsp.admin.assistant.ai;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AiGateway} 的 AgentScope 实现。
 * <p>
 * 本类是业务层与 AgentScope 之间唯一的桥：
 * <ul>
 *   <li>唯一 import AgentScope / Reactor 类型的业务类（{@link AgentEventAdapter} 作为内部适配类同样允许）</li>
 *   <li>负责把 {@link ChatRequest} 翻译为 {@link Msg} 列表</li>
 *   <li>订阅 {@link HarnessAgent#streamEvents} 返回的 {@code Flux<AgentEvent>}，逐事件交给 {@link AgentEventAdapter}</li>
 *   <li>终止后跳过后续事件（含 delta），并在终止/取消时 dispose 上游订阅</li>
 *   <li>保证 {@link StreamHandler#onComplete} / {@link StreamHandler#onError} 互斥且恰好一次</li>
 *   <li>返回 {@link StreamHandle} 供调用方取消（P4 SSE onCompletion/onTimeout 调用）</li>
 * </ul>
 * <p>
 * AgentScope 实例由 {@link AgentSupplier} 提供，真实 HarnessAgent 构造在 {@code AssistantAiConfiguration}
 * （基于 OpenAIChatModel → ReActAgent → HarnessAgent）；本类通过 supplier 注入，便于单测注入替身。
 */
@Slf4j
public class AgentScopeAiGateway implements AiGateway {

    /** AgentScope agent 提供者。解耦 gateway 与 HarnessAgent 构造细节，便于测试替身。 */
    public interface AgentSupplier {
        HarnessAgent get();
    }

    private final AssistantProperties properties;
    private final AgentSupplier agentSupplier;
    private final AgentEventAdapter adapter;

    public AgentScopeAiGateway(AssistantProperties properties, AgentSupplier agentSupplier) {
        this.properties = properties;
        this.agentSupplier = agentSupplier;
        this.adapter = new AgentEventAdapter();
    }

    @Override
    public StreamHandle streamChat(ChatRequest request, StreamHandler handler) {
        if (request == null || handler == null) {
            throw new IllegalArgumentException("request and handler must not be null");
        }

        // 配置校验失败 → 立即 onError，返回已完成 handle
        try {
            validateConfig();
        } catch (Exception e) {
            handler.onError(e);
            return completedHandle();
        }

        // 请求带 citations → 流开始前回调一次（P3 透传路径）
        String citations = request.getCitations();
        if (citations != null && !citations.isEmpty()) {
            try {
                handler.onCitations(citations);
            } catch (Exception e) {
                log.warn("onCitations 回调异常", e);
            }
        }

        final List<Msg> msgs;
        try {
            msgs = toMessages(request);
        } catch (Exception e) {
            handler.onError(e);
            return completedHandle();
        }

        final Flux<AgentEvent> events;
        try {
            HarnessAgent agent = agentSupplier.get();
            events = agent.streamEvents(msgs, RuntimeContext.empty());
        } catch (Exception e) {
            log.warn("AgentScope streamEvents 调用失败", e);
            handler.onError(e);
            return completedHandle();
        }

        TerminationTracker tracker = new TerminationTracker(handler);
        Disposable disposable = events
                .subscribe(
                        event -> dispatch(event, tracker),
                        tracker::onError,
                        tracker::completeIfNotTerminated);
        tracker.bind(disposable);
        return tracker;
    }

    private void dispatch(AgentEvent event, TerminationTracker tracker) {
        // 已终止（含取消）→ 跳过后续事件，避免终止后再 onDelta
        if (tracker.isTerminated()) {
            return;
        }
        try {
            boolean terminated = adapter.handle(event, tracker.handler);
            if (terminated) {
                tracker.completeIfNotTerminated();
            }
        } catch (Exception e) {
            tracker.onError(e);
        }
    }

    /**
     * 把 {@link ChatRequest} 翻译为 AgentScope {@link Msg} 列表。
     * <p>
     * 注意：AgentScope 2.0 禁止在 inputMessages 中注入 SYSTEM 消息（会抛
     * "Hooks must not inject SYSTEM messages"）。因此 systemPrompt + retrievalContext
     * 合并到首条 UserMessage 的前置说明中，不输出 SystemMessage。
     */
    private List<Msg> toMessages(ChatRequest request) {
        List<Msg> msgs = new ArrayList<>();
        // systemPrompt 与 retrievalContext 作为用户问题的前置上下文
        StringBuilder prefix = new StringBuilder();
        String systemPrompt = request.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            prefix.append(systemPrompt).append("\n\n");
        }
        if (request.getRetrievalContext() != null && !request.getRetrievalContext().isEmpty()) {
            prefix.append("检索上下文：\n").append(request.getRetrievalContext()).append("\n\n");
        }
        for (ChatMessage h : request.getHistory()) {
            if ("assistant".equalsIgnoreCase(h.getRole())) {
                msgs.add(new io.agentscope.core.message.AssistantMessage(h.getContent()));
            } else {
                msgs.add(new UserMessage(h.getContent()));
            }
        }
        // 当前问题（含前置 system/retrieval 上下文）
        String userContent = prefix.length() > 0
                ? prefix + request.getUserMessage()
                : request.getUserMessage();
        msgs.add(new UserMessage(userContent));
        return msgs;
    }

    private void validateConfig() {
        if (isBlank(properties.getApiKey())) {
            throw new IllegalStateException(
                    "dsp.assistant.api-key 未配置（环境变量 DSP_ASSISTANT_API_KEY），无法调用 AI 网关");
        }
        if (isBlank(properties.getModel())) {
            throw new IllegalStateException("dsp.assistant.model 未配置，无法调用 AI 网关");
        }
        if (isBlank(properties.getBaseUrl())) {
            throw new IllegalStateException("dsp.assistant.base-url 未配置，无法调用 AI 网关");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static StreamHandle completedHandle() {
        return () -> { /* 已终止，无操作 */ };
    }

    /**
     * 终止状态追踪器：保证 onComplete/onError 互斥且恰好一次，并实现 {@link StreamHandle} 取消。
     * 同时持有 {@link Disposable}，取消/终止时 dispose 上游订阅。
     */
    static final class TerminationTracker implements StreamHandle {
        private final StreamHandler handler;
        private volatile boolean terminated = false;
        private volatile Disposable disposable;

        TerminationTracker(StreamHandler handler) {
            this.handler = handler;
        }

        void bind(Disposable disposable) {
            this.disposable = disposable;
            // bind 前若已终止（如竞态），立即 dispose
            if (terminated && disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        }

        boolean isTerminated() {
            return terminated;
        }

        synchronized void completeIfNotTerminated() {
            if (terminated) {
                return;
            }
            terminated = true;
            disposeQuietly();
            try {
                handler.onComplete();
            } catch (Exception e) {
                log.warn("onComplete 回调异常", e);
            }
        }

        synchronized void onError(Throwable error) {
            if (terminated) {
                return;
            }
            terminated = true;
            disposeQuietly();
            log.warn("AI 流式调用失败，已回调 onError", error);
            try {
                handler.onError(error);
            } catch (Exception e) {
                log.warn("onError 回调异常", e);
            }
        }

        @Override
        public synchronized void cancel() {
            if (terminated) {
                return;
            }
            terminated = true;
            disposeQuietly();
            log.debug("AI 流式调用被调用方取消");
            // 取消视为终止：回调 onError 以满足「恰好一次终止」契约
            try {
                handler.onError(new StreamCancelledException("AI 流式调用被取消"));
            } catch (Exception e) {
                log.warn("取消后 onError 回调异常", e);
            }
        }

        private void disposeQuietly() {
            if (disposable != null && !disposable.isDisposed()) {
                try {
                    disposable.dispose();
                } catch (Exception e) {
                    log.warn("dispose 上游订阅异常", e);
                }
            }
        }
    }
}
