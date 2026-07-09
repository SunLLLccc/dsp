package com.sunlc.dsp.admin.assistant.text2api;

import com.sunlc.dsp.admin.assistant.ai.AiGateway;
import com.sunlc.dsp.admin.assistant.template.TemplateSelectionResult;
import com.sunlc.dsp.admin.assistant.template.TemplateSelector;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.AiText2ApiDraft;
import com.sunlc.dsp.service.AiText2ApiDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Text2API 阶段生成编排服务实现。
 * <p>
 * T3-A 范围：状态机骨架 + SchemaEvidence 门禁 + confirm/rollback invalidation + 落库。
 * 各阶段 AI 生成（接口定义/SQL/XML）在 T3-B/C 填充。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Text2ApiServiceImpl implements Text2ApiService {

    private final AiText2ApiDraftService draftService;
    private final AiGateway aiGateway;
    private final TemplateSelector templateSelector;

    // ===== 草稿管理 =====

    @Override
    public AiText2ApiDraft createDraft(Long userId, String userName, String title) {
        AiText2ApiDraft draft = new AiText2ApiDraft();
        draft.setDraftId(UUID.randomUUID().toString().replace("-", ""));
        draft.setUserId(userId);
        draft.setUserName(userName);
        draft.setStage(DraftStage.REQUIREMENT);
        draft.setConfirmedStage(0);
        draft.setStatus(0);
        draft.setCreatedTime(LocalDateTime.now());
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.save(draft);
        return draft;
    }

    @Override
    public AiText2ApiDraft getOwnedDraft(String draftId, Long userId) {
        return draftService.getOwnedDraft(draftId, userId);
    }

    @Override
    public List<AiText2ApiDraft> listDrafts(Long userId) {
        return draftService.lambdaQuery()
                .eq(AiText2ApiDraft::getUserId, userId)
                .orderByDesc(AiText2ApiDraft::getUpdatedTime)
                .list();
    }

    @Override
    public AiText2ApiDraft updateRequirement(String draftId, Long userId, String requirementText) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        draft.setRequirementText(requirementText);
        // 必改3：修改需求后失效下游产物（接口定义/SQL/模板/XML 都可能过时）
        if (draft.getStage() != null && draft.getStage() > DraftStage.REQUIREMENT) {
            draft.setStage(DraftStage.REQUIREMENT);
            draft.setInvalidatedFromStage(DraftStage.INTERFACE);
            if (draft.getConfirmedStage() != null && draft.getConfirmedStage() > DraftStage.REQUIREMENT) {
                draft.setConfirmedStage(DraftStage.REQUIREMENT);
            }
            log.info("修改需求后失效下游产物: draftId={}, invalidatedFrom={}",
                    draftId, DraftStage.INTERFACE);
        }
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return draft;
    }

    // ===== 阶段生成（含 SchemaEvidence 门禁）=====

    @Override
    public StageResult generate(String draftId, Long userId, int stage, SchemaEvidence evidence) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        if (!DraftStage.isValid(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法阶段: " + stage);
        }

        switch (stage) {
            case DraftStage.INTERFACE:
                return generateInterface(draft);
            case DraftStage.SQL:
                return generateSql(draft, evidence);
            case DraftStage.TEMPLATE:
                return generateTemplate(draft);
            case DraftStage.XML:
                return generateXml(draft);
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "阶段 " + DraftStage.name(stage) + " 暂不支持生成（需求阶段用 updateRequirement）");
        }
    }

    /**
     * Text2SQL 门禁（P0 安全约束）。
     * SchemaEvidence 为空 → 返回 needs_more_info，绝对不调 AiGateway。
     */
    private StageResult generateSql(AiText2ApiDraft draft, SchemaEvidence evidence) {
        // ===== 门禁：evidence 为空不调 AiGateway =====
        if (evidence == null || evidence.isEmpty()) {
            log.info("Text2SQL 缺少 SchemaEvidence，返回 needs_more_info（不调 AiGateway）: draftId={}",
                    draft.getDraftId());
            return StageResult.needsMoreInfo(DraftStage.SQL,
                    "缺少表结构依据。请提供涉及的表名、字段、关联关系和过滤条件，"
                            + "或选择数据源读取表结构。");
        }

        // ===== 有依据：落 evidence + 调 AiGateway 生成 SQL（T3-B 填充真实生成）=====
        draft.setSchemaEvidence(evidenceToText(evidence));
        draft.setStage(DraftStage.SQL);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);

        // T3-B：调 aiGateway.streamChat 生成 SQL（当前骨架，返回 generated 占位）
        // 真实生成会解析 AI 输出为结构化 SQL JSON（sqlId/sql/purpose/dependsOn）
        log.info("Text2SQL 有依据，进入生成（T3-B 填充）: draftId={}", draft.getDraftId());
        return StageResult.generated(DraftStage.SQL, "SQL 生成骨架（T3-B 填充真实 AI 生成）");
    }

    /** 接口定义生成（T3-B 填充真实 AI 生成）。 */
    private StageResult generateInterface(AiText2ApiDraft draft) {
        draft.setStage(DraftStage.INTERFACE);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return StageResult.generated(DraftStage.INTERFACE, "接口定义生成骨架（T3-B 填充）");
    }

    /** 模板选择（复用 T2 的 TemplateSelector）。 */
    private StageResult generateTemplate(AiText2ApiDraft draft) {
        // 从 SQL 草稿解析特征（T3-B 填充真实解析，当前用默认单 SQL）
        int sqlCount = parseSqlCount(draft.getSqlDraft());
        boolean hasDependency = parseHasDependency(draft.getSqlDraft());
        boolean hasPagination = parseHasPagination(draft.getRequirementText());
        boolean hasDynamic = parseHasDynamic(draft.getRequirementText());

        TemplateSelectionResult selection = templateSelector.select(sqlCount, hasDependency, hasPagination, hasDynamic);
        if (!selection.isMatched()) {
            return StageResult.needsMoreInfo(DraftStage.TEMPLATE, selection.getUnmatchedMessage());
        }
        draft.setTemplateSelection(templateSelectionToText(selection));
        draft.setStage(DraftStage.TEMPLATE);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return StageResult.generated(DraftStage.TEMPLATE,
                "模板: " + selection.getTemplateFile() + "（" + selection.getSelectionReason() + "）");
    }

    /** XML/JSON 生成（T3-C 填充真实模板填充）。 */
    private StageResult generateXml(AiText2ApiDraft draft) {
        draft.setStage(DraftStage.XML);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return StageResult.generated(DraftStage.XML, "XML/JSON 生成骨架（T3-C 填充）");
    }

    // ===== confirm / rollback invalidation =====

    @Override
    public AiText2ApiDraft confirmStage(String draftId, Long userId, int stage) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        if (!DraftStage.isValid(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法阶段: " + stage);
        }
        // 只能确认已到达的阶段
        if (stage > draft.getStage()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不能确认未到达的阶段（当前 stage=" + draft.getStage() + "）");
        }
        // 推进 confirmed_stage
        if (draft.getConfirmedStage() == null || stage > draft.getConfirmedStage()) {
            draft.setConfirmedStage(stage);
        }
        // 必改2：重新确认到 XML 阶段且 xml/json 产物存在时，清空 invalidated_from_stage
        // （用户重新走完了失效阶段，后续产物已被新产物覆盖）
        if (stage >= DraftStage.XML
                && draft.getInvalidatedFromStage() != null
                && !isBlank(draft.getXmlDraft())
                && !isBlank(draft.getImportJsonDraft())) {
            draft.setInvalidatedFromStage(null);
            log.info("重新确认到 XML 且产物存在，清空 invalidated_from_stage: draftId={}", draftId);
        }
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return draft;
    }

    @Override
    public AiText2ApiDraft rollbackToStage(String draftId, Long userId, int stage) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        if (!DraftStage.isValid(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法阶段: " + stage);
        }
        // 不能回退到未到达的阶段
        if (stage > draft.getStage()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不能回退到未到达的阶段");
        }
        // 保留后续产物（不删除），标记失效起点 = stage + 1
        draft.setStage(stage);
        if (stage + 1 <= DraftStage.XML) {
            draft.setInvalidatedFromStage(stage + 1);
        }
        // confirmed_stage 回退（不能超过当前 stage）
        if (draft.getConfirmedStage() != null && draft.getConfirmedStage() > stage) {
            draft.setConfirmedStage(stage);
        }
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        log.info("草稿回退: draftId={}, toStage={}, invalidatedFrom={}",
                draftId, stage, draft.getInvalidatedFromStage());
        return draft;
    }

    // ===== 发布前置校验（T3-A 只校验，不发布）=====

    @Override
    public void validateBeforePublish(String draftId, Long userId) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        if (draft.getConfirmedStage() == null || draft.getConfirmedStage() < DraftStage.XML) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未确认到 XML 生成阶段，不允许发布（当前 confirmed=" + draft.getConfirmedStage() + "）");
        }
        // 阶段 5 产物必须 valid（未被 invalidated）
        Integer invalidated = draft.getInvalidatedFromStage();
        if (invalidated != null && invalidated <= DraftStage.XML) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "阶段 5 产物已失效（因回退），请重新生成后再发布");
        }
        // xml/import_json 必须存在
        if (isBlank(draft.getXmlDraft()) || isBlank(draft.getImportJsonDraft())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "XML 或导入 JSON 草稿为空，不允许发布");
        }
    }

    @Override
    public void deleteDraft(String draftId, Long userId) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        draftService.removeById(draft.getId());
    }

    // ===== 内部工具 =====

    private AiText2ApiDraft getOwnedDraftOrThrow(String draftId, Long userId) {
        AiText2ApiDraft draft = draftService.getOwnedDraft(draftId, userId);
        if (draft == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "草稿不存在或无权访问");
        }
        return draft;
    }

    private String evidenceToText(SchemaEvidence evidence) {
        StringBuilder sb = new StringBuilder();
        for (SchemaEvidence.TableEvidence t : evidence.getTables()) {
            sb.append("表 ").append(t.getTableName());
            if (t.getDescription() != null) {
                sb.append("（").append(t.getDescription()).append("）");
            }
            sb.append(": ").append(String.join(", ", t.getColumns())).append("\n");
        }
        return sb.toString();
    }

    private String templateSelectionToText(TemplateSelectionResult selection) {
        return selection.getTemplateFile() + "|" + selection.getSelectionReason();
    }

    // T3-B 填充：从结构化 SQL JSON 解析特征
    private int parseSqlCount(String sqlDraft) {
        if (isBlank(sqlDraft)) {
            return 1; // 默认单 SQL
        }
        // 简单按 "sqlId" 出现次数估算（T3-B 改为 JSON 解析）
        return Math.max(1, countOccurrences(sqlDraft, "sqlId"));
    }

    private boolean parseHasDependency(String sqlDraft) {
        return !isBlank(sqlDraft) && sqlDraft.contains("dependsOn")
                && !sqlDraft.contains("\"dependsOn\":[]");
    }

    private boolean parseHasPagination(String text) {
        return text != null && (text.contains("分页") || text.contains("pagination"));
    }

    private boolean parseHasDynamic(String text) {
        return text != null && (text.contains("动态") || text.contains("可选")
                || text.contains("条件筛选") || text.contains("if") || text.contains("foreach"));
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
