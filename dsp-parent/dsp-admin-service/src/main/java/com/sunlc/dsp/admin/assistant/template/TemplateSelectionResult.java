package com.sunlc.dsp.admin.assistant.template;

import java.util.Collections;
import java.util.List;

/**
 * 模板选择结果。
 */
public class TemplateSelectionResult {

    /** 是否匹配到模板 */
    private final boolean matched;
    /** 模板文件路径（matched=true 时非空） */
    private final String templateFile;
    /** 模板 ID */
    private final String templateId;
    /** 适用场景 */
    private final String scenario;
    /** 选择理由（基于特征匹配） */
    private final String selectionReason;
    /** 填充点提示（来自 selectionSignals） */
    private final List<String> fillHints;
    /** 重点复核项（来自 requiresUserConfirmation） */
    private final List<String> reviewPoints;
    /** 无匹配时的提示信息（matched=false 时非空） */
    private final String unmatchedMessage;

    private TemplateSelectionResult(boolean matched, String templateFile, String templateId,
                                    String scenario, String selectionReason,
                                    List<String> fillHints, List<String> reviewPoints,
                                    String unmatchedMessage) {
        this.matched = matched;
        this.templateFile = templateFile;
        this.templateId = templateId;
        this.scenario = scenario;
        this.selectionReason = selectionReason;
        this.fillHints = fillHints == null ? Collections.emptyList() : Collections.unmodifiableList(fillHints);
        this.reviewPoints = reviewPoints == null ? Collections.emptyList() : Collections.unmodifiableList(reviewPoints);
        this.unmatchedMessage = unmatchedMessage;
    }

    /** 匹配成功。 */
    public static TemplateSelectionResult matched(TemplateIndexConfig.TemplateEntry entry, String reason) {
        return new TemplateSelectionResult(true,
                entry.getFile(), entry.getId(), entry.getScenario(), reason,
                entry.getSelectionSignals(), entry.getRequiresUserConfirmation(), null);
    }

    /** 无匹配。 */
    public static TemplateSelectionResult unmatched(String message) {
        return new TemplateSelectionResult(false, null, null, null, null,
                Collections.emptyList(), Collections.emptyList(), message);
    }

    public boolean isMatched() { return matched; }
    public String getTemplateFile() { return templateFile; }
    public String getTemplateId() { return templateId; }
    public String getScenario() { return scenario; }
    public String getSelectionReason() { return selectionReason; }
    public List<String> getFillHints() { return fillHints; }
    public List<String> getReviewPoints() { return reviewPoints; }
    public String getUnmatchedMessage() { return unmatchedMessage; }
}
