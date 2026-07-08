package com.sunlc.dsp.admin.assistant.ai;

/**
 * AI 网关流式回调接口。
 * <p>
 * 由 P4 的 SSE 层适配实现，{@link AiGateway#streamChat} 在生成过程中回调本接口。
 * 本接口及其所有实现均不得依赖 AgentScope / Reactor 类型。
 */
public interface StreamHandler {

    /** 流式文本增量（逐 token 或逐片段）。 */
    void onDelta(String text);

    /**
     * 引用来源回调（gateway 在 request.citations 非空时回调；P3 检索层负责填充 citations）。
     *
     * @param citationsJson 引用来源 JSON 字符串（docs + sources 结构）；无引用时传 null 或空
     */
    void onCitations(String citationsJson);

    /** 正常完成。 */
    void onComplete();

    /** 失败。异常信息通过 throwable 传递，由 SSE 层转为 error 事件。 */
    void onError(Throwable error);
}
