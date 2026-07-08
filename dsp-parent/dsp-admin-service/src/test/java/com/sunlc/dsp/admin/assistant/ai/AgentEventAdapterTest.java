package com.sunlc.dsp.admin.assistant.ai;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentScope 事件适配逻辑单测。
 * 直接构造 AgentScope 事件对象，验证 adapter 转换正确，不依赖 Flux / 真实模型。
 */
class AgentEventAdapterTest {

    private final AgentEventAdapter adapter = new AgentEventAdapter();

    @Test
    void textBlockDelta_invokesOnDeltaWithText() {
        RecordingHandler handler = new RecordingHandler();
        TextBlockDeltaEvent event = new TextBlockDeltaEvent("reply-1", "block-1", "你好");

        boolean terminated = adapter.handle(event, handler);

        assertFalse(terminated, "文本增量事件不应触发终止");
        assertEquals(List.of("你好"), handler.deltas);
    }

    @Test
    void textBlockDelta_emptyDelta_skipsOnDelta() {
        RecordingHandler handler = new RecordingHandler();
        TextBlockDeltaEvent event = new TextBlockDeltaEvent("reply-1", "block-1", "");

        adapter.handle(event, handler);

        assertTrue(handler.deltas.isEmpty(), "空 delta 不应触发 onDelta");
    }

    @Test
    void multipleDeltas_accumulate() {
        RecordingHandler handler = new RecordingHandler();
        adapter.handle(new TextBlockDeltaEvent("r", "b", "A"), handler);
        adapter.handle(new TextBlockDeltaEvent("r", "b", "B"), handler);
        adapter.handle(new TextBlockDeltaEvent("r", "b", "C"), handler);

        assertEquals(List.of("A", "B", "C"), handler.deltas);
    }

    @Test
    void agentEndEvent_isTermination() {
        RecordingHandler handler = new RecordingHandler();

        boolean terminated = adapter.handle(new AgentEndEvent("reply-1"), handler);

        assertTrue(terminated, "AGENT_END 应标记为终止事件");
        // adapter 本身不回调 onComplete，终止由 gateway 的 tracker 处理
        assertFalse(handler.completed, "adapter 不直接回调 onComplete");
    }

    @Test
    void otherEvent_ignored() {
        RecordingHandler handler = new RecordingHandler();
        AgentEvent other = new AgentEvent("id", "now") {
            @Override
            public AgentEventType getType() {
                return AgentEventType.MODEL_CALL_START;
            }
        };

        boolean terminated = adapter.handle(other, handler);

        assertFalse(terminated);
        assertTrue(handler.deltas.isEmpty());
        assertFalse(handler.completed);
    }

    /** 记录回调的 StreamHandler 替身。 */
    static final class RecordingHandler implements StreamHandler {
        final List<String> deltas = new ArrayList<>();
        volatile boolean completed = false;
        volatile Throwable error = null;

        @Override
        public void onDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onCitations(String citationsJson) {
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}
