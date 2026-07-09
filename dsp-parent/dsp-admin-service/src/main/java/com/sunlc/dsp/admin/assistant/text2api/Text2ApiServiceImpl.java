package com.sunlc.dsp.admin.assistant.text2api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunlc.dsp.admin.assistant.ai.AiGateway;
import com.sunlc.dsp.admin.assistant.ai.ChatMessage;
import com.sunlc.dsp.admin.assistant.ai.ChatRequest;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Text2API 阶段生成编排服务实现。
 * <p>
 * T3-B：阶段前置约束锁紧 + 接口定义/Text2SQL 真实 AI 生成。
 * XML 生成（阶段5）仍为骨架（T3-C）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Text2ApiServiceImpl implements Text2ApiService {

    private final AiText2ApiDraftService draftService;
    private final AiGateway aiGateway;
    private final TemplateSelector templateSelector;
    private final Text2ApiPromptFactory promptFactory;
    private final Text2ApiAiResponseParser responseParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        if (draft.getStage() != null && draft.getStage() > DraftStage.REQUIREMENT) {
            draft.setStage(DraftStage.REQUIREMENT);
            draft.setInvalidatedFromStage(DraftStage.INTERFACE);
            if (draft.getConfirmedStage() != null && draft.getConfirmedStage() > DraftStage.REQUIREMENT) {
                draft.setConfirmedStage(DraftStage.REQUIREMENT);
            }
            log.info("修改需求后失效下游产物: draftId={}, invalidatedFrom={}", draftId, DraftStage.INTERFACE);
        }
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return draft;
    }

    // ===== 阶段生成（含前置约束 + 真实 AI）=====

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

    // ===== 阶段 2：接口定义（真实 AI 生成）=====

    private StageResult generateInterface(AiText2ApiDraft draft) {
        // 前置约束：requirement_text 必须非空
        if (isBlank(draft.getRequirementText())) {
            return StageResult.needsMoreInfo(DraftStage.INTERFACE,
                    "缺少需求文本，请先填写需求说明。");
        }

        String prompt = promptFactory.buildInterfacePrompt(draft.getRequirementText());
        String aiOutput;
        try {
            aiOutput = callAiSync(prompt);
        } catch (Exception e) {
            log.warn("接口定义 AI 生成失败: draftId={}", draft.getDraftId(), e);
            return StageResult.failed(DraftStage.INTERFACE, "AI 生成失败: " + e.getMessage());
        }

        InterfaceDraft interfaceDraft;
        try {
            interfaceDraft = responseParser.parseInterfaceDraft(aiOutput);
        } catch (IllegalArgumentException e) {
            return StageResult.failed(DraftStage.INTERFACE, "接口定义解析失败: " + e.getMessage());
        }
        if (interfaceDraft == null) {
            return StageResult.needsMoreInfo(DraftStage.INTERFACE,
                    "信息不足以生成完整接口定义，请补充需求细节。");
        }

        // 落库
        try {
            draft.setInterfaceDraft(objectMapper.writeValueAsString(interfaceDraft));
        } catch (Exception e) {
            return StageResult.failed(DraftStage.INTERFACE, "接口定义序列化失败");
        }
        draft.setStage(DraftStage.INTERFACE);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return StageResult.generated(DraftStage.INTERFACE,
                "接口定义已生成: " + interfaceDraft.getTransno());
    }

    // ===== 阶段 3：Text2SQL（真实 AI 生成 + SchemaEvidence 门禁）=====

    private StageResult generateSql(AiText2ApiDraft draft, SchemaEvidence evidence) {
        // 前置约束 1：必须已确认接口定义
        if (draft.getConfirmedStage() == null || draft.getConfirmedStage() < DraftStage.INTERFACE) {
            return StageResult.needsMoreInfo(DraftStage.SQL, "请先确认接口定义。");
        }
        // 前置约束 2：interface_draft 必须非空
        if (isBlank(draft.getInterfaceDraft())) {
            return StageResult.needsMoreInfo(DraftStage.SQL, "缺少接口定义，请先生成并确认接口定义。");
        }
        // 前置约束 3（P0 门禁）：SchemaEvidence 必须有效
        if (evidence == null || evidence.isEmpty()) {
            log.info("Text2SQL 缺少有效 SchemaEvidence，返回 needs_more_info: draftId={}", draft.getDraftId());
            return StageResult.needsMoreInfo(DraftStage.SQL,
                    "缺少表结构依据。请提供涉及的表名、字段、关联关系和过滤条件，或选择数据源读取表结构。");
        }

        // 有依据：构造 prompt 调 AI
        String evidenceText = evidenceToText(evidence);
        String prompt = promptFactory.buildSqlPrompt(
                draft.getRequirementText(), draft.getInterfaceDraft(), evidenceText);

        String aiOutput;
        try {
            aiOutput = callAiSync(prompt);
        } catch (Exception e) {
            log.warn("Text2SQL AI 生成失败: draftId={}", draft.getDraftId(), e);
            return StageResult.failed(DraftStage.SQL, "AI 生成失败: " + e.getMessage());
        }

        SqlDraft sqlDraft;
        try {
            sqlDraft = responseParser.parseSqlDraft(aiOutput);
        } catch (IllegalArgumentException e) {
            return StageResult.failed(DraftStage.SQL, "SQL 解析失败: " + e.getMessage());
        }
        if (sqlDraft == null) {
            return StageResult.needsMoreInfo(DraftStage.SQL,
                    "信息不足以生成 SQL，请补充表结构/关联/过滤条件。");
        }

        // 落库
        try {
            draft.setSchemaEvidence(evidenceText);
            draft.setSqlDraft(objectMapper.writeValueAsString(sqlDraft));
        } catch (Exception e) {
            return StageResult.failed(DraftStage.SQL, "SQL 序列化失败");
        }
        draft.setStage(DraftStage.SQL);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return StageResult.generated(DraftStage.SQL,
                "SQL 已生成，共 " + sqlDraft.getSqlItems().size() + " 段");
    }

    // ===== 阶段 4：模板选择 =====

    private StageResult generateTemplate(AiText2ApiDraft draft) {
        // 前置约束：必须已确认 SQL + sql_draft 非空
        if (draft.getConfirmedStage() == null || draft.getConfirmedStage() < DraftStage.SQL) {
            return StageResult.needsMoreInfo(DraftStage.TEMPLATE, "请先确认 SQL。");
        }
        if (isBlank(draft.getSqlDraft())) {
            return StageResult.needsMoreInfo(DraftStage.TEMPLATE,
                    "缺少 SQL 草稿，请先生成并确认 SQL。");
        }

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

    // ===== 阶段 5：XML（T3-B 仍骨架，但前置约束已锁）=====

    private StageResult generateXml(AiText2ApiDraft draft) {
        // 前置约束：必须已确认模板 + interface/sql/template 非空
        if (draft.getConfirmedStage() == null || draft.getConfirmedStage() < DraftStage.TEMPLATE) {
            return StageResult.needsMoreInfo(DraftStage.XML, "请先确认模板选择。");
        }
        if (isBlank(draft.getInterfaceDraft()) || isBlank(draft.getSqlDraft()) || isBlank(draft.getTemplateSelection())) {
            return StageResult.needsMoreInfo(DraftStage.XML,
                    "缺少接口定义/SQL/模板选择，无法生成 XML。");
        }
        // T3-C 填充真实模板填充
        draft.setStage(DraftStage.XML);
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        return StageResult.generated(DraftStage.XML, "XML/JSON 生成骨架（T3-C 填充）");
    }

    // ===== confirm / rollback =====

    @Override
    public AiText2ApiDraft confirmStage(String draftId, Long userId, int stage) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        if (!DraftStage.isValid(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法阶段: " + stage);
        }
        if (stage > draft.getStage()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不能确认未到达的阶段（当前 stage=" + draft.getStage() + "）");
        }
        if (draft.getConfirmedStage() == null || stage > draft.getConfirmedStage()) {
            draft.setConfirmedStage(stage);
        }
        if (stage >= DraftStage.XML
                && draft.getInvalidatedFromStage() != null
                && !isBlank(draft.getXmlDraft())
                && !isBlank(draft.getImportJsonDraft())) {
            draft.setInvalidatedFromStage(null);
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
        if (stage > draft.getStage()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能回退到未到达的阶段");
        }
        draft.setStage(stage);
        if (stage + 1 <= DraftStage.XML) {
            draft.setInvalidatedFromStage(stage + 1);
        }
        if (draft.getConfirmedStage() != null && draft.getConfirmedStage() > stage) {
            draft.setConfirmedStage(stage);
        }
        draft.setUpdatedTime(LocalDateTime.now());
        draftService.updateById(draft);
        log.info("草稿回退: draftId={}, toStage={}, invalidatedFrom={}",
                draftId, stage, draft.getInvalidatedFromStage());
        return draft;
    }

    @Override
    public void validateBeforePublish(String draftId, Long userId) {
        AiText2ApiDraft draft = getOwnedDraftOrThrow(draftId, userId);
        if (draft.getConfirmedStage() == null || draft.getConfirmedStage() < DraftStage.XML) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未确认到 XML 生成阶段，不允许发布（当前 confirmed=" + draft.getConfirmedStage() + "）");
        }
        Integer invalidated = draft.getInvalidatedFromStage();
        if (invalidated != null && invalidated <= DraftStage.XML) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "阶段 5 产物已失效（因回退），请重新生成后再发布");
        }
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

    // ===== AI 同步调用（CountDownLatch 等待异步完成，T4 接 SSE 时改流式透传）=====

    /** 同步调用超时（秒）。 */
    private static final long AI_SYNC_TIMEOUT_SECONDS = 90;

    /**
     * 同步调用 AiGateway，用 CountDownLatch 等待 onComplete/onError。
     * 超时后 cancel StreamHandle 并抛异常。失败不留下半成品。
     */
    private String callAiSync(String prompt) {
        StringBuilder buffer = new StringBuilder();
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        ChatRequest request = new ChatRequest(
                prompt, "你是 DSP 接口生成助手，严格按要求输出。",
                Collections.emptyList(), null, null);

        com.sunlc.dsp.admin.assistant.ai.StreamHandle handle = aiGateway.streamChat(
                request,
                new com.sunlc.dsp.admin.assistant.ai.StreamHandler() {
                    @Override
                    public void onDelta(String text) {
                        buffer.append(text);
                    }

                    @Override
                    public void onCitations(String citationsJson) {
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        errorRef.set(error);
                        latch.countDown();
                    }
                });

        try {
            boolean completed = latch.await(AI_SYNC_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                // 超时：cancel handle
                if (handle != null) {
                    try {
                        handle.cancel();
                    } catch (Exception ignored) {
                    }
                }
                throw new RuntimeException("AI 调用超时（" + AI_SYNC_TIMEOUT_SECONDS + "s）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (handle != null) {
                try {
                    handle.cancel();
                } catch (Exception ignored) {
                }
            }
            throw new RuntimeException("AI 调用被中断", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw new RuntimeException("AI 调用失败: " + error.getMessage(), error);
        }
        return buffer.toString();
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
            if (t.getDescription() != null && !t.getDescription().isBlank()) {
                sb.append("（").append(t.getDescription()).append("）");
            }
            sb.append(": ").append(String.join(", ", t.getColumns())).append("\n");
        }
        return sb.toString();
    }

    private String templateSelectionToText(TemplateSelectionResult selection) {
        return selection.getTemplateFile() + "|" + selection.getSelectionReason();
    }

    private int parseSqlCount(String sqlDraft) {
        if (isBlank(sqlDraft)) {
            return 1;
        }
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
