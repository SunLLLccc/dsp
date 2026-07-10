package com.sunlc.dsp.admin.assistant.text2api;

/**
 * 阶段生成结果。
 * <p>
 * 三种状态（互斥）：
 * <ul>
 *   <li>{@link #generated}：阶段已生成，产物已落库。</li>
 *   <li>{@link #needsMoreInfo}：需要用户补充信息（如缺 SchemaEvidence），AiGateway 未被调用。</li>
 *   <li>{@link #failed}：阶段生成失败（AI 调用失败/解析失败/校验失败），不落库。</li>
 * </ul>
 */
public class StageResult {

    private final boolean generated;
    private final boolean needsMoreInfo;
    private final boolean failed;
    private final int stage;
    private final String message;
    /** 需要追问的具体问题（needsMoreInfo=true 时非空）。 */
    private final String followUpQuestion;

    private StageResult(boolean generated, boolean needsMoreInfo, boolean failed, int stage,
                        String message, String followUpQuestion) {
        this.generated = generated;
        this.needsMoreInfo = needsMoreInfo;
        this.failed = failed;
        this.stage = stage;
        this.message = message;
        this.followUpQuestion = followUpQuestion;
    }

    /** 阶段已生成。 */
    public static StageResult generated(int stage, String message) {
        return new StageResult(true, false, false, stage, message, null);
    }

    /** 需要补充信息（不调 AiGateway）。 */
    public static StageResult needsMoreInfo(int stage, String followUpQuestion) {
        return new StageResult(false, true, false, stage,
                "缺少必要依据，需要补充信息", followUpQuestion);
    }

    /** 阶段生成失败（AI 调用失败/解析失败/校验失败），不落库。 */
    public static StageResult failed(int stage, String message) {
        return new StageResult(false, false, true, stage, message, null);
    }

    public boolean isGenerated() { return generated; }
    public boolean isNeedsMoreInfo() { return needsMoreInfo; }
    public boolean isFailed() { return failed; }
    public int getStage() { return stage; }
    public String getMessage() { return message; }
    public String getFollowUpQuestion() { return followUpQuestion; }
}
