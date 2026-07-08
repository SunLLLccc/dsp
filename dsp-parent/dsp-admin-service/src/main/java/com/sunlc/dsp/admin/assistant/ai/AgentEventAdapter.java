package com.sunlc.dsp.admin.assistant.ai;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;

/**
 * AgentScope 事件 → {@link StreamHandler} 适配器（内部适配类，package-private）。
 * <p>
 * 本类是项目中少数允许 import AgentScope 事件类型的类之一。
 * 把「单事件如何转回调」拆成纯逻辑方法，便于单测直接构造事件对象验证，
 * 无需订阅真实 Flux 或调用真实模型。
 */
final class AgentEventAdapter {

    /**
     * 处理单个 AgentScope 事件，转换为对 {@link StreamHandler} 的回调。
     * <ul>
     *   <li>{@link AgentEventType#TEXT_BLOCK_DELTA} → {@link StreamHandler#onDelta(String)}</li>
     *   <li>{@link AgentEventType#AGENT_END} → 标记完成（由调用方据此回调 onComplete）</li>
     *   <li>其余事件忽略（一期只关心文本流与终止）</li>
     * </ul>
     *
     * @param event   AgentScope 事件
     * @param handler 流式回调
     * @return true 表示该事件是终止事件（AGENT_END），调用方据此保证 onComplete/onError 语义
     */
    boolean handle(AgentEvent event, StreamHandler handler) {
        AgentEventType type = event.getType();
        if (type == AgentEventType.TEXT_BLOCK_DELTA && event instanceof TextBlockDeltaEvent) {
            String delta = ((TextBlockDeltaEvent) event).getDelta();
            if (delta != null && !delta.isEmpty()) {
                handler.onDelta(delta);
            }
            return false;
        }
        if (type == AgentEventType.AGENT_END || event instanceof AgentEndEvent) {
            return true;
        }
        return false;
    }
}
