package com.sunlc.dsp.admin.assistant.text2api;

/**
 * 确认/回退阶段请求（confirm / rollback）。
 */
public class ConfirmStageRequest {
    /** 目标阶段：1-需求 2-接口定义 3-SQL 4-模板 5-XML */
    private Integer stage;

    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
}
