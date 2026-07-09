package com.sunlc.dsp.admin.assistant.text2api;

/**
 * 阶段生成结果。
 * <p>
 * 两种状态：
 * <ul>
 *   <li>{@link #generated}：阶段已生成（或已确认），产物已落库。</li>
 *   <li>{@link #needsMoreInfo}：需要用户补充信息（如 Text2SQL 缺 SchemaEvidence），AiGateway 未被调用。</li>
 * </ul>
 */
public class StageResult {

    private final boolean generated;
    private final boolean needsMoreInfo;
    private final int stage;
    private final String message;
    /** 需要追问的具体问题（needsMoreInfo=true 时非空）。 */
    private final String followUpQuestion;

    private StageResult(boolean generated, boolean needsMoreInfo, int stage,
                        String message, String followUpQuestion) {
        this.generated = generated;
        this.needsMoreInfo = needsMoreInfo;
        this.stage = stage;
        this.message = message;
        this.followUpQuestion = followUpQuestion;
    }

    /** 阶段已生成。 */
    public static StageResult generated(int stage, String message) {
        return new StageResult(true, false, stage, message, null);
    }

    /** 需要补充信息（不调 AiGateway）。 */
    public static StageResult needsMoreInfo(int stage, String followUpQuestion) {
        return new StageResult(false, true, stage,
                "缺少必要依据，需要补充信息", followUpQuestion);
    }

    public boolean isGenerated() { return generated; }
    public boolean isNeedsMoreInfo() { return needsMoreInfo; }
    public int getStage() { return stage; }
    public String getMessage() { return message; }
    public String getFollowUpQuestion() { return followUpQuestion; }
}
