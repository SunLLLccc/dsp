package com.sunlc.dsp.admin.assistant.ai;

/**
 * AI 流式调用被调用方取消时抛出，由 {@link AgentScopeAiGateway.TerminationTracker#cancel()} 触发，
 * 经 {@link StreamHandler#onError(Throwable)} 回调给上层（P4 SSE 层据此发 cancel 事件或静默关闭）。
 */
public class StreamCancelledException extends RuntimeException {
    public StreamCancelledException(String message) {
        super(message);
    }
}
