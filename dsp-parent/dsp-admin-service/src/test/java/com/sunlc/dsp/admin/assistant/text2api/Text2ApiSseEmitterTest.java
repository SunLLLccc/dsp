package com.sunlc.dsp.admin.assistant.text2api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text2ApiSseEmitter 事件协议单测。
 * <p>
 * 验证：
 * <ul>
 *   <li>正常链路 start -> result/needs_more_info/failed -> complete 的事件数量与顺序</li>
 *   <li>终态 release 幂等（只释放一次）</li>
 *   <li>取消：客户端断开/超时 -> cancelled 事件 + cancel 句柄 + release</li>
 * </ul>
 * <p>
 * 子类覆盖 {@code send(SseEventBuilder)} 记录事件名，避免依赖真实 servlet 响应。
 * 生命周期回调通过 {@link Text2ApiSseEmitter#fireLifecycleTimeout()} 等 package-private 入口模拟。
 */
class Text2ApiSseEmitterTest {

    private int releaseCount;
    private int cancelCount;

    @BeforeEach
    void setUp() {
        releaseCount = 0;
        cancelCount = 0;
    }

    // ===== 正常链路 =====

    @Test
    void generated_emitsStartResultComplete() {
        EventEmitter em = newEmitter();
        em.sendStart();
        em.sendStageResult(StageResult.generated(DraftStage.INTERFACE, "done"));
        em.completeNormally();

        assertEquals(
                java.util.Arrays.asList(
                        Text2ApiSseEmitter.EVENT_START,
                        Text2ApiSseEmitter.EVENT_RESULT,
                        Text2ApiSseEmitter.EVENT_COMPLETE),
                em.names);
        assertEquals(1, releaseCount);
        assertEquals(0, cancelCount, "正常完成不触发取消句柄");
    }

    @Test
    void needsMoreInfo_emitsNeedsMoreInfoThenComplete() {
        EventEmitter em = newEmitter();
        em.sendStageResult(StageResult.needsMoreInfo(DraftStage.SQL, "补充字段"));
        em.completeNormally();

        assertEquals(
                java.util.Arrays.asList(
                        Text2ApiSseEmitter.EVENT_NEEDS_MORE_INFO,
                        Text2ApiSseEmitter.EVENT_COMPLETE),
                em.names);
        assertEquals(1, releaseCount);
    }

    @Test
    void failed_emitsFailedThenComplete() {
        EventEmitter em = newEmitter();
        em.sendStageResult(StageResult.failed(DraftStage.XML, "失败"));
        em.completeNormally();

        assertEquals(
                java.util.Arrays.asList(
                        Text2ApiSseEmitter.EVENT_FAILED,
                        Text2ApiSseEmitter.EVENT_COMPLETE),
                em.names);
        assertEquals(1, releaseCount);
    }

    @Test
    void completeNormally_calledTwice_releasesOnlyOnce() {
        EventEmitter em = newEmitter();
        em.completeNormally();
        em.completeNormally();

        // 第一次发 complete，第二次 closed 已置位不发事件
        assertEquals(java.util.Arrays.asList(Text2ApiSseEmitter.EVENT_COMPLETE), em.names);
        assertEquals(1, releaseCount, "幂等：重复 complete 只 release 一次");
    }

    @Test
    void stageResultSentTwice_secondIgnored() {
        EventEmitter em = newEmitter();
        em.sendStageResult(StageResult.generated(DraftStage.INTERFACE, "a"));
        em.sendStageResult(StageResult.generated(DraftStage.INTERFACE, "b"));
        em.completeNormally();

        // 只第一次 result 生效
        assertEquals(
                java.util.Arrays.asList(
                        Text2ApiSseEmitter.EVENT_RESULT,
                        Text2ApiSseEmitter.EVENT_COMPLETE),
                em.names);
    }

    // ===== 取消 / 生命周期 =====

    @Test
    void lifecycleTimeout_emitsCancelledTriggersCancelAndRelease() {
        EventEmitter em = newEmitter();
        em.fireLifecycleTimeout();

        assertTrue(em.names.contains(Text2ApiSseEmitter.EVENT_CANCELLED), "超时应发 cancelled");
        assertFalse(em.names.contains(Text2ApiSseEmitter.EVENT_COMPLETE), "取消不应发 complete");
        assertEquals(1, cancelCount, "超时应触发取消句柄");
        assertEquals(1, releaseCount, "超时应释放并发额度");
    }

    @Test
    void lifecycleError_emitsErrorTriggersCancelAndRelease() {
        EventEmitter em = newEmitter();
        em.fireLifecycleError(new IllegalStateException("broken pipe"));

        assertTrue(em.names.contains(Text2ApiSseEmitter.EVENT_ERROR), "error 回调应发 error 事件");
        assertEquals(1, cancelCount, "error 应触发取消句柄");
        assertEquals(1, releaseCount, "error 应释放并发额度");
    }

    @Test
    void lifecycleClientClose_emitsCancelledAndReleases() {
        EventEmitter em = newEmitter();
        em.fireLifecycleComplete();

        assertTrue(em.names.contains(Text2ApiSseEmitter.EVENT_CANCELLED), "客户端关闭应发 cancelled");
        assertEquals(1, releaseCount, "客户端断开应释放并发额度");
    }

    @Test
    void clientCloseAfterComplete_doesNotDoubleRelease() {
        EventEmitter em = newEmitter();
        em.sendStart();
        em.sendStageResult(StageResult.generated(DraftStage.INTERFACE, "done"));
        em.completeNormally();

        // 已正常完成，再模拟客户端关闭（容器回调）—— 不应重复 release
        em.fireLifecycleComplete();

        assertEquals(1, releaseCount, "已完成后客户端关闭不重复 release");
    }

    // ===== 协议常量稳定性（前端契约）=====

    @Test
    void eventConstants_areStableForFrontendContract() {
        assertEquals("start", Text2ApiSseEmitter.EVENT_START);
        assertEquals("delta", Text2ApiSseEmitter.EVENT_DELTA);
        assertEquals("result", Text2ApiSseEmitter.EVENT_RESULT);
        assertEquals("needs_more_info", Text2ApiSseEmitter.EVENT_NEEDS_MORE_INFO);
        assertEquals("failed", Text2ApiSseEmitter.EVENT_FAILED);
        assertEquals("complete", Text2ApiSseEmitter.EVENT_COMPLETE);
        assertEquals("error", Text2ApiSseEmitter.EVENT_ERROR);
        assertEquals("cancelled", Text2ApiSseEmitter.EVENT_CANCELLED);
    }

    // ===== 辅助 =====

    /** 记录事件名的 emitter（覆盖 send，不依赖真实 servlet 响应）。 */
    private EventEmitter newEmitter() {
        return new EventEmitter();
    }

    private final class EventEmitter extends Text2ApiSseEmitter {
        final List<String> names = new ArrayList<>();

        EventEmitter() {
            super(3000L, () -> releaseCount++);
            bindCanceller(() -> cancelCount++);
        }

        @Override
        public void send(SseEmitter.SseEventBuilder builder) {
            // 解析事件名：SseEmitter.event().name(x) 会写入 builder；
            // 这里无法直接读，改为按调用顺序映射——但 send 仅在 sendEvent 内被调用，
            // 事件名在调用 send 前已知。改用 recordLastEvent 模式不可行（private）。
            // 因此用「事件名 + 计数」近似：通过覆盖 send 并由父类 sendEvent 传入的事件名已知。
            // 真正捕获事件名需要解析 builder，见下。
            names.add(extractName(builder));
        }

        private String extractName(SseEmitter.SseEventBuilder builder) {
            // SseEventBuilder.build() 返回 Set<DataWithMediaType>，
            // 其中含 "event:name\ndata:..." 片段，解析出事件名（取 event: 后、换行前的部分）。
            try {
                java.util.Set<org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType> items =
                        builder.build();
                for (org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType item : items) {
                    Object data = item.getData();
                    if (data instanceof String) {
                        String s = (String) data;
                        int idx = s.indexOf("event:");
                        if (idx >= 0) {
                            String rest = s.substring(idx + "event:".length());
                            int nl = rest.indexOf('\n');
                            return nl >= 0 ? rest.substring(0, nl).trim() : rest.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
                // 解析失败兜底
            }
            return "unknown";
        }
    }
}
