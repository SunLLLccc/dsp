package com.sunlc.dsp.engine;

import com.sunlc.dsp.engine.model.DebugTrace;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 调试跟踪上下文。线程安全（queries 在 QueryOrchestrator 线程池中并发写入）。
 * 仅在 debug 模式下由调用方创建并传入；生产普通查询传 null，零开销。
 */
@Getter
@Setter
public class DebugContext {

    private final boolean debugMode;
    private final List<DebugTrace> traces = new CopyOnWriteArrayList<>();
    private final List<DebugStep> steps = new CopyOnWriteArrayList<>();

    private String transno;
    private long startTimeMs;
    private long endTimeMs;
    private long totalTimeMs;
    private boolean success;
    private String errorMessage;

    public DebugContext(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void addTrace(DebugTrace trace) {
        traces.add(trace);
    }

    public void addStep(DebugStep step) {
        steps.add(step);
    }

    /**
     * 执行阶段记录（参数校验、查询执行、结果映射、响应构建）。
     * 不可变，创建后不再修改。
     */
    public static class DebugStep {
        private final String name;
        private final String status;
        private final long elapsedTimeMs;
        private final String errorMessage;

        public DebugStep(String name, String status, long elapsedTimeMs, String errorMessage) {
            this.name = name;
            this.status = status;
            this.elapsedTimeMs = elapsedTimeMs;
            this.errorMessage = errorMessage;
        }

        public String getName() { return name; }
        public String getStatus() { return status; }
        public long getElapsedTimeMs() { return elapsedTimeMs; }
        public String getErrorMessage() { return errorMessage; }

        public static DebugStep success(String name, long elapsedTimeMs) {
            return new DebugStep(name, "SUCCESS", elapsedTimeMs, null);
        }

        public static DebugStep error(String name, long elapsedTimeMs, String errorMessage) {
            return new DebugStep(name, "ERROR", elapsedTimeMs, errorMessage);
        }
    }
}
