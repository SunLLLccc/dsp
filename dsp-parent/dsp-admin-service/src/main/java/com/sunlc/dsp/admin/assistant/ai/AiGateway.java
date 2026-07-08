package com.sunlc.dsp.admin.assistant.ai;

/**
 * 项目自有 AI 网关接口。
 * <p>
 * 业务编排层（{@code AssistantService} 等）只能依赖本接口，不得依赖 AgentScope / Reactor 类型。
 * 唯一实现 {@code AgentScopeAiGateway} 在内部适配 AgentScope。
 */
public interface AiGateway {

    /**
     * 流式问答。生成过程中通过 {@link StreamHandler} 回调：
     * <ul>
     *   <li>多次 {@link StreamHandler#onDelta(String)} —— 文本增量</li>
     *   <li>零或一次 {@link StreamHandler#onCitations(String)} —— 引用来源（gateway 在请求带 citations 时回调）</li>
     *   <li>恰好一次 {@link StreamHandler#onComplete()} 或 {@link StreamHandler#onError(Throwable)} —— 终止</li>
     * </ul>
     * 终止保证：onComplete 与 onError 互斥，恰好回调一次。终止后不再有 onDelta / onCitations。
     * <p>
     * 本方法立即返回 {@link StreamHandle}，生成在内部异步进行（不阻塞调用线程）。
     * 调用方可通过 {@link StreamHandle#close()} / {@link StreamHandle#cancel()} 取消生成，
     * P4 的 SSE 层在 onCompletion/onTimeout/onError 时调用以停止上游订阅。
     *
     * @param request 问答请求
     * @param handler 流式回调
     * @return 流句柄，可用于取消生成；不可空
     */
    StreamHandle streamChat(ChatRequest request, StreamHandler handler);
}
