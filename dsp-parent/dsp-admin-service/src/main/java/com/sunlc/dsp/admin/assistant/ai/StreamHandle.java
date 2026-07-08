package com.sunlc.dsp.admin.assistant.ai;

/**
 * 流式问答句柄，由 {@link AiGateway#streamChat} 返回，用于取消生成。
 * <p>
 * 实现 {@link AutoCloseable}，便于 try-with-resources；{@link #close()} 与 {@link #cancel()} 等价。
 * 多次取消幂等。取消后上游生成应尽快停止，{@link StreamHandler#onComplete()}/{@link StreamHandler#onError(Throwable)}
 * 语义仍由 gateway 保证（已终止则忽略，未终止则按取消处理）。
 */
public interface StreamHandle extends AutoCloseable {

    /** 取消生成。幂等，可多次调用。 */
    void cancel();

    /** 等价于 {@link #cancel()}，支持 try-with-resources。不抛受检异常。 */
    @Override
    default void close() {
        cancel();
    }
}
