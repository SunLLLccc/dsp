package com.sunlc.dsp.admin.assistant.text2api;

/**
 * 阶段生成请求（POST /drafts/{draftId}/generate，SSE 响应）。
 * <p>
 * body 指定目标 stage + 输入（仅阶段 3 Text2SQL 需要 {@link SchemaEvidenceDto}）。
 */
public class GenerateRequest {
    /** 目标阶段：2-接口定义 3-SQL 4-模板 5-XML */
    private Integer stage;
    /** Text2SQL 依据（仅阶段 3 使用，其它阶段忽略）。 */
    private SchemaEvidenceDto evidence;

    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
    public SchemaEvidenceDto getEvidence() { return evidence; }
    public void setEvidence(SchemaEvidenceDto evidence) { this.evidence = evidence; }
}
