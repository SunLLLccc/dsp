package com.sunlc.dsp.admin.assistant.text2api;

/**
 * 更新需求文本请求（阶段 1）。
 */
public class UpdateRequirementRequest {
    /** 需求原文（非空） */
    private String requirementText;

    public String getRequirementText() { return requirementText; }
    public void setRequirementText(String requirementText) { this.requirementText = requirementText; }
}
