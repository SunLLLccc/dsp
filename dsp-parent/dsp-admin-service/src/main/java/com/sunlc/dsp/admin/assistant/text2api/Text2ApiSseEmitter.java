package com.sunlc.dsp.admin.assistant.text2api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Text2API 阶段生成 SSE 事件封装。
 * <p>
 * 事件协议（前端用 fetch + ReadableStream 消费，不依赖原生 EventSource）：
 * <ul>
 *   <li>{@code start} —— 生成开始</li>
 *   <li>{@code delta} —— 流式增量（一期同步聚合无 delta，预留）</li>
 *   <li>{@code result} —— 阶段已生成，产物已落库（{@link StageResult#isGenerated()}）</li>
 *   <li>{@code needs_more_info} —— 需要用户补充信息，AiGateway 未被调用</li>
 *   <li>{@code failed} —— 阶段生成失败</li>
 *   <li>{@code complete} —— 正常结束（无论 result/needs_more_info/failed，最后必发一次）</li>
 *   <li>{@code error} —— SSE 生命周期异常（非阶段失败）</li>
 *   <li>{@code cancelled} —— 客户端断开/超时/取消</li>
 * </ul>
 * <p>
 * 终止事件保证：result/needs_more_info/failed 之后必发 {@code complete}；
 * error/cancelled 为终态事件，发送后不再有事件。
 * <p>
 * 并发额度释放：通过 {@link Releaser} 在终态（complete/error/cancelled）回调，幂等。
 *
 * @see ChatSseEmitter chat 版 SSE 封装（事件协议不同，故独立成类）
 */
@Slf4j
public class Text2ApiSseEmitter extends SseEmitter {

    public static final String EVENT_START = "start";
    public static final String EVENT_DELTA = "delta";
    public static final String EVENT_RESULT = "result";
    public static final String EVENT_NEEDS_MORE_INFO = "needs_more_info";
    public static final String EVENT_FAILED = "failed";
    public static final String EVENT_COMPLETE = "complete";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_CANCELLED = "cancelled";

    /** 取消句柄：客户端断开/超时时调用，用于尽力取消上游生成（如中断工作线程）。 */
    private volatile Canceller canceller;
    /** 终态回调（释放并发额度），幂等。 */
    private final Releaser releaser;

    /** 已发送 result/needs_more_info/failed（complete 之前的阶段结果）。 */
    private volatile boolean stageResultSent = false;
    /** emitter 已关闭（终态事件已发）。 */
    private volatile boolean closed = false;
    /** 并发额度是否已释放（幂等保证：终态只 release 一次）。 */
    private volatile boolean released = false;

    /** 终态释放回调。 */
    @FunctionalInterface
    public interface Releaser {
        void release();
    }

    /** 取消回调（设置后 SSE 生命周期触发时调用）。 */
    @FunctionalInterface
    public interface Canceller {
        void cancel();
    }

    public Text2ApiSseEmitter(long timeoutMs, Releaser releaser) {
        super(timeoutMs);
        this.releaser = releaser;
        onCompletion(this::onLifecycleClose);
        onTimeout(this::onLifecycleTimeout);
        onError(this::onLifecycleError);
    }

    /** 绑定取消句柄（工作线程启动后绑定）。 */
    public void bindCanceller(Canceller canceller) {
        this.canceller = canceller;
    }

    /** 发送 start 事件。 */
    public void sendStart() {
        sendEvent(EVENT_START, "");
    }

    /** 发送 delta 事件（预留，一期同步聚合无 delta）。 */
    public void sendDelta(String text) {
        sendEvent(EVENT_DELTA, text == null ? "" : text);
    }

    /**
     * 发送阶段结果事件（result/needs_more_info/failed），标记阶段结果已送达。
     * complete 由调用方在之后单独发送。
     */
    public void sendStageResult(StageResult result) {
        if (closed || stageResultSent) {
            return;
        }
        if (result.isGenerated()) {
            sendEvent(EVENT_RESULT, result.getMessage() == null ? "" : result.getMessage());
        } else if (result.isNeedsMoreInfo()) {
            sendEvent(EVENT_NEEDS_MORE_INFO, result.getFollowUpQuestion() == null ? "" : result.getFollowUpQuestion());
        } else {
            sendEvent(EVENT_FAILED, result.getMessage() == null ? "" : result.getMessage());
        }
        stageResultSent = true;
    }

    /** 正常完成：发送 complete + 释放额度（幂等）。 */
    public synchronized void completeNormally() {
        if (closed) {
            // 已发终态事件，但仍要保证 release（release 幂等）
            releaseOnce();
            return;
        }
        sendEvent(EVENT_COMPLETE, "");
        closed = true;
        try {
            complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常", e);
        }
        releaseOnce();
    }

    /** SSE 生命周期：客户端正常关闭。若尚未发终态事件，视为取消。 */
    private void onLifecycleClose() {
        log.debug("SSE emitter completed (client closed or finished)");
        handleLifecycleCancel();
    }

    private void onLifecycleTimeout() {
        log.warn("SSE emitter timeout, cancelling text2api generation");
        handleLifecycleCancel();
    }

    private void onLifecycleError(Throwable t) {
        log.warn("SSE emitter error: {}", t == null ? "" : t.getMessage());
        terminateWithError();
    }

    /** 生命周期触发的取消：若生成未正常完成，发 cancelled 事件 + 取消上游 + 释放额度。 */
    private synchronized void handleLifecycleCancel() {
        if (closed) {
            return;
        }
        // 先发送 cancelled 事件（closed 此刻仍为 false，sendEvent 可发出），再标记关闭
        sendEvent(EVENT_CANCELLED, "cancelled");
        closed = true;
        try {
            complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常(cancel)", e);
        }
        cancelQuietly();
        releaseOnce();
    }

    private synchronized void terminateWithError() {
        if (closed) {
            return;
        }
        sendEvent(EVENT_ERROR, "SSE 生命周期异常");
        closed = true;
        try {
            complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常(error)", e);
        }
        cancelQuietly();
        releaseOnce();
    }

    private void cancelQuietly() {
        if (canceller != null) {
            try {
                canceller.cancel();
            } catch (Exception e) {
                log.debug("cancel 异常", e);
            }
        }
    }

    private void releaseOnce() {
        if (released) {
            return;
        }
        released = true;
        if (releaser != null) {
            try {
                releaser.release();
            } catch (Exception e) {
                log.warn("releaser 异常", e);
            }
        }
    }

    private void sendEvent(String eventName, String data) {
        if (closed) {
            return;
        }
        try {
            send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开：标记关闭并取消上游
            closed = true;
            log.debug("SSE send 失败（客户端可能已断开）：{}", e.getMessage());
            cancelQuietly();
        }
    }

    // ===== 生命周期处理入口（package-private，供单测模拟 SSE 容器回调）=====
    //
    // 这些方法纯转发到上面的私有处理逻辑，自身不包含业务分支，
    // 仅用于在没有 servlet 容器的单测中模拟 onTimeout/onError/onCompletion 回调。

    /** 模拟 SSE 容器的 onCompletion 回调（客户端关闭/请求完成）。 */
    void fireLifecycleComplete() {
        onLifecycleClose();
    }

    /** 模拟 SSE 容器的 onTimeout 回调。 */
    void fireLifecycleTimeout() {
        onLifecycleTimeout();
    }

    /** 模拟 SSE 容器的 onError 回调。 */
    void fireLifecycleError(Throwable t) {
        onLifecycleError(t);
    }
}
