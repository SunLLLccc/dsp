package com.sunlc.dsp.admin.assistant.ai;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentScopeAiGateway 单测。
 * 通过 AgentSupplier 注入 mock HarnessAgent，用 Flux.just/error/concat/never 构造可控事件流，
 * 验证 onDelta/onComplete/onError/onCitations 语义、终止后不再 delta、取消能力。不调用真实模型。
 * <p>
 * 注：Flux.just 等「同步」Flux 在 subscribe 时会同步发射完，故 streamChat 返回后回调已就绪，可直接断言。
 */
class AgentScopeAiGatewayTest {

    private AssistantProperties properties;
    private HarnessAgent agent;
    private AgentScopeAiGateway.AgentSupplier supplier;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties();
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setBaseUrl("http://localhost");
        agent = mock(HarnessAgent.class);
        supplier = () -> agent;
    }

    @Test
    void streamChat_multipleDeltasThenComplete() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.just(
                new TextBlockDeltaEvent("r", "b", "Hello"),
                new TextBlockDeltaEvent("r", "b", " "),
                new TextBlockDeltaEvent("r", "b", "World"),
                new AgentEndEvent("r")
        ));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        StreamHandle handle = gateway.streamChat(req("你好", null, null), handler);

        assertNotNull(handle);
        assertEquals(List.of("Hello", " ", "World"), handler.deltas);
        assertTrue(handler.completed, "应回调 onComplete");
        assertFalse(handler.errorOccurred, "不应回调 onError");
    }

    @Test
    void streamChat_returnsCancelableHandle() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.<AgentEvent>never());
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        StreamHandle handle = gateway.streamChat(req("长流", null, null), handler);

        assertFalse(handler.completed);
        assertFalse(handler.errorOccurred, "取消前不应有 onError");

        handle.cancel();

        assertTrue(handler.errorOccurred, "取消应触发 onError");
        assertTrue(handler.lastError instanceof StreamCancelledException,
                "取消错误应为 StreamCancelledException，实际：" + handler.lastError);
        assertFalse(handler.completed, "取消不应触发 onComplete");
    }

    @Test
    void streamChat_cancelIsIdempotent() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.<AgentEvent>never());
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);
        RecordingHandler handler = new RecordingHandler();
        StreamHandle handle = gateway.streamChat(req("幂等取消", null, null), handler);

        handle.cancel();
        handle.cancel();

        assertTrue(handler.errorCount == 1, "重复 cancel 应只回调一次 onError，实际：" + handler.errorCount);
    }

    @Test
    void streamChat_closeEqualsCancel() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.<AgentEvent>never());
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);
        RecordingHandler handler = new RecordingHandler();
        StreamHandle handle = gateway.streamChat(req("close", null, null), handler);

        handle.close();

        assertTrue(handler.lastError instanceof StreamCancelledException);
    }

    @Test
    void streamChat_noDeltaAfterTermination() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.just(
                new TextBlockDeltaEvent("r", "b", "before"),
                new AgentEndEvent("r"),
                new TextBlockDeltaEvent("r", "b", "after-should-skip")
        ));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        gateway.streamChat(req("终止后跳过", null, null), handler);

        assertEquals(List.of("before"), handler.deltas, "AGENT_END 后的 delta 应被跳过");
        assertTrue(handler.completed);
        assertFalse(handler.errorOccurred);
    }

    @Test
    void streamChat_fluxError_triggersOnError() {
        RuntimeException failure = new RuntimeException("模型调用失败");
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.error(failure));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        gateway.streamChat(req("触发错误", null, null), handler);

        assertFalse(handler.completed, "出错时不应回调 onComplete");
        assertNotNull(handler.lastError);
        assertEquals("模型调用失败", handler.lastError.getMessage());
    }

    @Test
    void streamChat_configMissing_invokesOnError() {
        properties.setApiKey(null);
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        StreamHandle handle = gateway.streamChat(req("未配置", null, null), handler);

        assertNotNull(handle, "配置缺失也应返回 handle");
        assertNotNull(handler.lastError);
        assertTrue(handler.lastError.getMessage().contains("api-key"),
                "未配置 apiKey 应给出明确异常，实际：" + handler.lastError.getMessage());
        assertFalse(handler.completed);
    }

    @Test
    void streamChat_completeAndErrorMutuallyExclusive() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.concat(
                Flux.just(new AgentEndEvent("r")),
                Flux.error(new RuntimeException("不应到达"))
        ));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        gateway.streamChat(req("互斥", null, null), handler);

        assertTrue(handler.completed);
        assertFalse(handler.errorOccurred, "已 complete 后的 error 不应回调 onError");
    }

    @Test
    void streamChat_citationsInvokedWhenPresent() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.just(
                new TextBlockDeltaEvent("r", "b", "答"),
                new AgentEndEvent("r")
        ));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        gateway.streamChat(req("带引用", null, "{\"docs\":[]}"), handler);

        assertEquals("{\"docs\":[]}", handler.citations,
                "request 带 citations 时应通过 onCitations 透传");
    }

    @Test
    void streamChat_noCitationsWhenAbsent() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.just(new AgentEndEvent("r")));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        RecordingHandler handler = new RecordingHandler();
        gateway.streamChat(req("无引用", null, null), handler);

        assertNull(handler.citations, "citations 为空时不应回调 onCitations");
    }

    @Test
    void streamChat_onDeltaInvokedExactlyOncePerEvent() {
        when(agent.streamEvents(anyList(), any())).thenReturn(Flux.just(
                new TextBlockDeltaEvent("r", "b", "A"),
                new TextBlockDeltaEvent("r", "b", "B")
        ));
        AgentScopeAiGateway gateway = new AgentScopeAiGateway(properties, supplier);

        final AtomicInteger deltaCount = new AtomicInteger();
        StreamHandler counting = new StreamHandler() {
            @Override public void onDelta(String text) { deltaCount.incrementAndGet(); }
            @Override public void onCitations(String citationsJson) {}
            @Override public void onComplete() {}
            @Override public void onError(Throwable error) {}
        };
        gateway.streamChat(req("计数", null, null), counting);

        assertEquals(2, deltaCount.get(), "两个 delta 事件应触发两次 onDelta");
    }

    @Test
    void chatRequest_historyIsImmutableCopy() {
        List<ChatMessage> history = new ArrayList<>(Arrays.asList(ChatMessage.user("h1")));
        ChatRequest request = new ChatRequest("q", "sys", history, null, null);

        history.add(ChatMessage.user("h2-mutated"));

        assertEquals(1, request.getHistory().size(), "request.history 应是不可变副本");
    }

    private ChatRequest req(String userMessage, String retrievalContext, String citations) {
        return new ChatRequest(userMessage, "你是助手", Collections.emptyList(), retrievalContext, citations);
    }

    /** 记录回调的 StreamHandler 替身。 */
    static final class RecordingHandler implements StreamHandler {
        final List<String> deltas = new ArrayList<>();
        volatile boolean completed = false;
        volatile boolean errorOccurred = false;
        volatile int errorCount = 0;
        volatile Throwable lastError = null;
        volatile String citations = null;

        @Override
        public void onDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onCitations(String citationsJson) {
            this.citations = citationsJson;
        }

        @Override
        public synchronized void onComplete() {
            completed = true;
        }

        @Override
        public synchronized void onError(Throwable error) {
            errorOccurred = true;
            errorCount++;
            lastError = error;
        }
    }
}
