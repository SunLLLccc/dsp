package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Text2API 草稿实体。
 * 6 阶段状态机：1-需求 2-接口定义 3-SQL 4-模板 5-XML 6-已发布。
 * 回退 invalidation：confirmed_stage 记录最后确认阶段，invalidated_from_stage 标记失效起点。
 */
@Data
@TableName("ai_text2api_draft")
public class AiText2ApiDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务草稿 ID（UUID），唯一 */
    private String draftId;

    /** 归属用户 ID */
    private Long userId;

    /** 归属用户名（冗余展示） */
    private String userName;

    /** 当前阶段：1-需求 2-接口定义 3-SQL 4-模板 5-XML 6-已发布 */
    private Integer stage;

    /** 用户最后确认到的阶段 */
    private Integer confirmedStage;

    /** 回退时标记的失效起点（null=无失效） */
    private Integer invalidatedFromStage;

    /** 状态：0-进行中 1-已完成 2-已取消 */
    private Integer status;

    /** 需求原文 */
    private String requirementText;

    /** 接口定义 JSON（transno/name/system/inputSchema/outputSchema） */
    private String interfaceDraft;

    /** Text2SQL 依据 JSON（表结构/元数据） */
    private String schemaEvidence;

    /** SQL 草稿（结构化 JSON 数组：sqlId/sql/purpose/dependsOn/outputAlias/relationDescription） */
    private String sqlDraft;

    /** 模板选择结果 JSON */
    private String templateSelection;

    /** XML 草稿 */
    private String xmlDraft;

    /** 导入 JSON 草稿 */
    private String importJsonDraft;

    /** 修正记录 JSON（见 ai-assets/correction-memory.md） */
    private String correctionRecords;

    /** 导入失败时的错误信息（允许重试） */
    private String publishError;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 逻辑删除：0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
}
