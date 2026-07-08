package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.admin.assistant.ai.StreamCancelledException;
import com.sunlc.dsp.admin.assistant.ai.StreamHandle;
import com.sunlc.dsp.admin.assistant.ai.StreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 事件封装。实现 {@link StreamHandler}，把 AI 流式回调转成 SSE 事件；
 * 并在 SseEmitter 生命周期回调（onCompletion/onTimeout/onError）里取消上游生成。
 * <p>
 * 事件协议：start / delta / citations / complete / error。
 * <p>
 * 终止事件保证送达：onComplete/onError 先发送 terminal event，再标记 closed，再 complete emitter。
 * <p>
 * 回调绑定（防竞态）：{@link TerminationCallback} 必须在调 {@code aiGateway.streamChat} 之前绑定，
 * 因为 streamChat 可能在返回前同步触发回调。{@link StreamHandle} 可后绑定（streamChat 返回后）。
 */
@Slf4j
public class ChatSseEmitter extends SseEmitter implements StreamHandler {

    public static final String EVENT_START = "start";
    public static final String EVENT_DELTA = "delta";
    public static final String EVENT_CITATIONS = "citations";
    public static final String EVENT_COMPLETE = "complete";
    public static final String EVENT_ERROR = "error";

    /** 终止回调（完成/失败/取消统一走这里），带 Throwable：null=完成，CancelledException=取消，其他=失败。 */
    private volatile TerminationCallback terminationCallback;
    /** AI 网关取消句柄，streamChat 返回后绑定。 */
    private volatile StreamHandle handle;

    private volatile boolean closed = false;

    /** 终止回调接口（在 streamChat 调用前绑定，避免竞态）。 */
    @FunctionalInterface
    public interface TerminationCallback {
        /**
         * @param error null 表示正常完成；StreamCancelledException 表示取消；其他表示失败。
         */
        void onTerminate(Throwable error);
    }

    /**
     * @param timeoutMs SSE 超时（毫秒），由配置注入
     * @param terminationCallback 终止回调（streamChat 调用前已绑定）
     */
    public ChatSseEmitter(long timeoutMs, TerminationCallback terminationCallback) {
        super(timeoutMs);
        this.terminationCallback = terminationCallback;
        onCompletion(this::onLifecycleClose);
        onTimeout(this::onLifecycleTimeout);
        onError(this::onLifecycleError);
    }

    /** 绑定 AI 网关取消句柄（streamChat 返回后调用）。 */
    public void bindHandle(StreamHandle handle) {
        this.handle = handle;
    }

    // ===== StreamHandler 回调 =====

    @Override
    public void onDelta(String text) {
        sendEvent(EVENT_DELTA, text);
    }

    @Override
    public void onCitations(String citationsJson) {
        sendEvent(EVENT_CITATIONS, citationsJson);
    }

    @Override
    public synchronized void onComplete() {
        terminate(null, EVENT_COMPLETE, "");
    }

    @Override
    public synchronized void onError(Throwable error) {
        if (closed) {
            return;
        }
        boolean cancelled = error instanceof StreamCancelledException;
        String message = cancelled ? "cancelled"
                : (error.getMessage() == null ? "AI 调用失败" : error.getMessage());
        if (cancelled) {
            log.debug("AI 流式调用被取消");
        } else {
            log.warn("AI 流式调用失败", error);
        }
        // 走 error 事件（取消也用 error 事件，data=cancelled，前端据此区分）
        terminate(error, EVENT_ERROR, message);
    }

    /** 发送 start 事件。 */
    public void sendStart() {
        sendEvent(EVENT_START, "");
    }

    // ===== 终止处理（先发事件，再标记，再 complete，最后回调）=====

    private synchronized void terminate(Throwable error, String eventName, String data) {
        if (closed) {
            return;
        }
        // 1. 先发送 terminal event（此时 closed 还是 false，sendEvent 能发出）
        sendEvent(eventName, data);
        // 2. 标记关闭（之后的 sendEvent 被拦截）
        closed = true;
        // 3. 关闭 emitter
        try {
            complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常", e);
        }
        // 4. 触发终止回调（更新消息状态、释放并发计数）
        if (terminationCallback != null) {
            try {
                terminationCallback.onTerminate(error);
            } catch (Exception e) {
                log.warn("terminationCallback 异常", e);
            }
        }
    }

    // ===== SseEmitter 生命周期 → 取消上游 =====

    private void onLifecycleClose() {
        log.debug("SSE emitter completed (client closed or finished)");
        cancelHandleQuietly();
    }

    private void onLifecycleTimeout() {
        log.warn("SSE emitter timeout, cancelling AI generation");
        cancelHandleQuietly();
    }

    private void onLifecycleError(Throwable t) {
        log.warn("SSE emitter error, cancelling AI generation: {}", t.getMessage());
        cancelHandleQuietly();
    }

    private void cancelHandleQuietly() {
        if (handle != null) {
            try {
                handle.cancel();
            } catch (Exception e) {
                log.debug("cancel handle 异常", e);
            }
        }
    }

    // ===== 内部：发送事件（closed 后拦截，保证终止后不再 send）=====

    private void sendEvent(String eventName, String data) {
        if (closed) {
            return;
        }
        try {
            send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开：标记关闭并取消上游（不在这里触发 terminate，避免与生命周期回调递归）
            closed = true;
            log.debug("SSE send 失败（客户端可能已断开）：{}", e.getMessage());
            cancelHandleQuietly();
        }
    }
}
