package com.sunlc.dsp.admin.assistant.text2api;

import com.sunlc.dsp.entity.AiText2ApiDraft;

import java.time.LocalDateTime;

/**
 * 草稿展示对象。
 * 不暴露敏感内部字段；返回前端可见的阶段产物与状态。
 */
public class DraftVO {
    private String draftId;
    private String userName;
    private Integer stage;
    private Integer confirmedStage;
    private Integer invalidatedFromStage;
    private Integer status;
    private String requirementText;
    private String interfaceDraft;
    private String schemaEvidence;
    private String sqlDraft;
    private String templateSelection;
    private String xmlDraft;
    private String importJsonDraft;
    private String correctionRecords;
    private String publishError;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public static DraftVO from(AiText2ApiDraft d) {
        DraftVO vo = new DraftVO();
        vo.draftId = d.getDraftId();
        vo.userName = d.getUserName();
        vo.stage = d.getStage();
        vo.confirmedStage = d.getConfirmedStage();
        vo.invalidatedFromStage = d.getInvalidatedFromStage();
        vo.status = d.getStatus();
        vo.requirementText = d.getRequirementText();
        vo.interfaceDraft = d.getInterfaceDraft();
        vo.schemaEvidence = d.getSchemaEvidence();
        vo.sqlDraft = d.getSqlDraft();
        vo.templateSelection = d.getTemplateSelection();
        vo.xmlDraft = d.getXmlDraft();
        vo.importJsonDraft = d.getImportJsonDraft();
        vo.correctionRecords = d.getCorrectionRecords();
        vo.publishError = d.getPublishError();
        vo.createdTime = d.getCreatedTime();
        vo.updatedTime = d.getUpdatedTime();
        return vo;
    }

    public String getDraftId() { return draftId; }
    public String getUserName() { return userName; }
    public Integer getStage() { return stage; }
    public Integer getConfirmedStage() { return confirmedStage; }
    public Integer getInvalidatedFromStage() { return invalidatedFromStage; }
    public Integer getStatus() { return status; }
    public String getRequirementText() { return requirementText; }
    public String getInterfaceDraft() { return interfaceDraft; }
    public String getSchemaEvidence() { return schemaEvidence; }
    public String getSqlDraft() { return sqlDraft; }
    public String getTemplateSelection() { return templateSelection; }
    public String getXmlDraft() { return xmlDraft; }
    public String getImportJsonDraft() { return importJsonDraft; }
    public String getCorrectionRecords() { return correctionRecords; }
    public String getPublishError() { return publishError; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
}
