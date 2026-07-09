package com.sunlc.dsp.admin.assistant.text2api;

import com.sunlc.dsp.entity.AiText2ApiDraft;

import java.util.List;

/**
 * Text2API 阶段生成编排服务。
 * <p>
 * 6 阶段状态机（1-需求 2-接口定义 3-SQL 4-模板 5-XML 6-已发布）。
 * 越权防护：所有操作必须先 getOwnedDraft 校验草稿归属。
 */
public interface Text2ApiService {

    /** 创建草稿。 */
    AiText2ApiDraft createDraft(Long userId, String userName, String title);

    /** 查询属于当前用户的草稿（越权返回 null）。 */
    AiText2ApiDraft getOwnedDraft(String draftId, Long userId);

    /** 当前用户的草稿列表。 */
    List<AiText2ApiDraft> listDrafts(Long userId);

    /** 更新需求文本（阶段 1）。 */
    AiText2ApiDraft updateRequirement(String draftId, Long userId, String requirementText);

    /**
     * 阶段生成。
     * <p>
     * Text2SQL（阶段 3）的 SchemaEvidence 门禁：evidence 为空时返回 needs_more_info，不调 AiGateway。
     * 其它阶段（接口定义/模板/XML）的 AI 生成在 T3-B/C 实现，当前为骨架。
     *
     * @param draftId  草稿 ID
     * @param userId   当前用户
     * @param stage    目标阶段
     * @param evidence Text2SQL 依据（仅阶段 3 使用，可为 null）
     * @return 阶段结果
     */
    StageResult generate(String draftId, Long userId, int stage, SchemaEvidence evidence);

    /** 确认阶段（推进 confirmed_stage）。 */
    AiText2ApiDraft confirmStage(String draftId, Long userId, int stage);

    /**
     * 回退到指定阶段。
     * 保留后续产物 + 标记 invalidated_from_stage = stage + 1。
     */
    AiText2ApiDraft rollbackToStage(String draftId, Long userId, int stage);

    /**
     * 发布前置校验（T3-A 只做校验，不实际发布）。
     * 校验 confirmed_stage >= 5 且阶段 5 产物 valid（未被 invalidated）。
     *
     * @throws com.sunlc.dsp.common.exception.BusinessException 校验失败
     */
    void validateBeforePublish(String draftId, Long userId);

    /**
     * 发布草稿（T5）：校验前置条件后调用 {@code ConfigImportService.importConfig} 完成接口导入。
     * <p>
     * 成功：推进到阶段 6（PUBLISHED）、清空 publishError。
     * 失败：不推进阶段、写入 publishError、抛 {@link com.sunlc.dsp.common.exception.BusinessException}，
     * 调用方可再次调用本方法重试。
     * <p>
     * 允许重复发布（已发布草稿也可重试），用于上次导入失败后重试。
     *
     * @param draftId  草稿 ID
     * @param userId   当前用户（归属校验）
     * @param operator 操作人（写入接口 createdBy/updatedBy），为空时由实现降级为 userId
     * @return 发布后的最新草稿
     * @throws com.sunlc.dsp.common.exception.BusinessException 前置不足或导入失败
     */
    AiText2ApiDraft publish(String draftId, Long userId, String operator);

    /** 逻辑删除草稿。 */
    void deleteDraft(String draftId, Long userId);
}
