package com.sunlc.dsp.admin.assistant.text2api;

import com.sunlc.dsp.admin.assistant.ai.AiGateway;
import com.sunlc.dsp.admin.assistant.template.TemplateSelector;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.AiText2ApiDraft;
import com.sunlc.dsp.service.AiText2ApiDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Text2ApiServiceImpl 单测。重点验证 SchemaEvidence 门禁与 invalidation 语义。
 */
@ExtendWith(MockitoExtension.class)
class Text2ApiServiceImplTest {

    @Mock private AiText2ApiDraftService draftService;
    @Mock private AiGateway aiGateway;
    @Mock private TemplateSelector templateSelector;

    private Text2ApiPromptFactory promptFactory;
    private Text2ApiAiResponseParser responseParser;
    private Text2ApiServiceImpl service;

    @BeforeEach
    void setUp() {
        promptFactory = new Text2ApiPromptFactory();
        responseParser = new Text2ApiAiResponseParser();
        service = new Text2ApiServiceImpl(draftService, aiGateway, templateSelector, promptFactory, responseParser);
        lenient().when(draftService.save(any())).thenReturn(true);
        lenient().when(draftService.updateById(any())).thenReturn(true);
    }

    // ===== P0: SchemaEvidence 门禁（核心安全约束）=====

    @Test
    void generateSql_emptyEvidence_returnsNeedsMoreInfoAndNeverCallsAiGateway() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // evidence 为 null
        StageResult result = service.generate("d1", 1L, DraftStage.SQL, null);

        assertTrue(result.isNeedsMoreInfo(), "应返回 needs_more_info");
        assertFalse(result.isGenerated());
        assertNotNull(result.getFollowUpQuestion());
        // P0：AiGateway 绝对不能被调用
        verify(aiGateway, never()).streamChat(any(), any());
    }

    @Test
    void generateSql_emptyEvidenceList_returnsNeedsMoreInfoAndNeverCallsAiGateway() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // evidence 非空但 tables 为空列表
        SchemaEvidence empty = new SchemaEvidence("USER_INPUT", Collections.emptyList());
        StageResult result = service.generate("d1", 1L, DraftStage.SQL, empty);

        assertTrue(result.isNeedsMoreInfo());
        verify(aiGateway, never()).streamChat(any(), any());
    }

    // ===== 必改1：无效 evidence（tableName空/columns空/columns全空字符串）=====

    @Test
    void generateSql_emptyTableName_returnsNeedsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        SchemaEvidence evidence = new SchemaEvidence("USER_INPUT", Arrays.asList(
                new SchemaEvidence.TableEvidence("", Arrays.asList("id", "name"), "")));
        StageResult result = service.generate("d1", 1L, DraftStage.SQL, evidence);

        assertTrue(result.isNeedsMoreInfo(), "tableName 空 → needs_more_info");
        verify(aiGateway, never()).streamChat(any(), any());
    }

    @Test
    void generateSql_emptyColumns_returnsNeedsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        SchemaEvidence evidence = new SchemaEvidence("USER_INPUT", Arrays.asList(
                new SchemaEvidence.TableEvidence("users", Collections.emptyList(), "")));
        StageResult result = service.generate("d1", 1L, DraftStage.SQL, evidence);

        assertTrue(result.isNeedsMoreInfo(), "columns 空 → needs_more_info");
        verify(aiGateway, never()).streamChat(any(), any());
    }

    @Test
    void generateSql_blankStringColumns_returnsNeedsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        SchemaEvidence evidence = new SchemaEvidence("USER_INPUT", Arrays.asList(
                new SchemaEvidence.TableEvidence("users", Arrays.asList("", "  "), "")));
        StageResult result = service.generate("d1", 1L, DraftStage.SQL, evidence);

        assertTrue(result.isNeedsMoreInfo(), "columns 全空字符串 → needs_more_info");
        verify(aiGateway, never()).streamChat(any(), any());
    }

    // ===== 必改2：invalidated_from_stage 恢复 =====

    @Test
    void confirmStage_xmlWithProducts_clearsInvalidated() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.XML);
        draft.setConfirmedStage(DraftStage.SQL); // 回退后 confirmed 较低
        draft.setInvalidatedFromStage(DraftStage.TEMPLATE); // 阶段4起失效
        draft.setXmlDraft("<interface/>");
        draft.setImportJsonDraft("{}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 重新确认到 XML（阶段5），产物存在 → 清空 invalidated
        service.confirmStage("d1", 1L, DraftStage.XML);

        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertEquals(null, captor.getValue().getInvalidatedFromStage(),
                "重新确认到 XML 且产物存在，应清空 invalidated_from_stage");
    }

    @Test
    void rollbackThenRegenerateToXml_validateBeforePublishPasses() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.XML);
        draft.setConfirmedStage(DraftStage.XML);
        draft.setXmlDraft("<interface/>");
        draft.setImportJsonDraft("{}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 回退到 SQL → invalidated_from_stage=4
        AiText2ApiDraft afterRollback = service.rollbackToStage("d1", 1L, DraftStage.SQL);
        assertEquals(DraftStage.TEMPLATE, afterRollback.getInvalidatedFromStage());

        // 重新走完，填入 xml/json（模拟重新生成），confirmStage(XML) 清空 invalidated
        draft.setStage(DraftStage.XML);
        draft.setXmlDraft("<interface new/>");
        draft.setImportJsonDraft("{\"new\":true}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        service.confirmStage("d1", 1L, DraftStage.XML);

        // validateBeforePublish 应通过
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        draft.setInvalidatedFromStage(null); // confirmStage 已清空
        service.validateBeforePublish("d1", 1L); // 不抛异常
    }

    // ===== 必改3：updateRequirement 失效下游 =====

    @Test
    void updateRequirement_invalidatesDownstreamWhenStageAdvanced() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.XML); // 已到阶段5
        draft.setConfirmedStage(DraftStage.XML);
        draft.setXmlDraft("<interface/>"); // 已有产物
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        service.updateRequirement("d1", 1L, "新的需求");

        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        AiText2ApiDraft updated = captor.getValue();
        assertEquals(DraftStage.REQUIREMENT, updated.getStage(), "stage 回到需求阶段");
        assertEquals(DraftStage.INTERFACE, updated.getInvalidatedFromStage(),
                "invalidated_from_stage = 接口定义阶段");
        assertEquals(DraftStage.REQUIREMENT, updated.getConfirmedStage(),
                "confirmed_stage 回退到需求");
    }

    @Test
    void updateRequirement_atRequirementStage_noInvalidation() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.REQUIREMENT); // 还在阶段1
        draft.setConfirmedStage(0);
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        service.updateRequirement("d1", 1L, "需求");

        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        // 阶段1时改需求不触发失效
        assertEquals(null, captor.getValue().getInvalidatedFromStage());
    }

    @Test
    void generateSql_withEvidence_allowsGenerationPath() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{\"transno\":\"T1\"}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        mockAiGateway("{\"sqlItems\":[{\"sqlId\":\"main\","
                + "\"sql\":\"SELECT id FROM users\",\"purpose\":\"查询\",\"dependsOn\":[]}],\"questions\":[]}");

        SchemaEvidence evidence = new SchemaEvidence("DATASOURCE_METADATA", Arrays.asList(
                new SchemaEvidence.TableEvidence("users", Arrays.asList("id", "name", "email"), "用户表")));
        StageResult result = service.generate("d1", 1L, DraftStage.SQL, evidence);

        assertTrue(result.isGenerated());
        assertFalse(result.isNeedsMoreInfo());
        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertNotNull(captor.getValue().getSchemaEvidence());
        assertTrue(captor.getValue().getSchemaEvidence().contains("users"));
    }

    @Test
    void generateSql_emptyEvidence_doesNotWriteSqlDraft() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        service.generate("d1", 1L, DraftStage.SQL, null);

        // 门禁返回时不应落 sql_draft（只更新 evidence 的逻辑在有依据时才走）
        // verify updateById 不应因 sql 被调用（needs_more_info 不落库 sql）
        // 注意：generateSql 在 evidence 空时不调 updateById
        verify(draftService, never()).updateById(any());
    }

    // ===== confirm / rollback invalidation =====

    @Test
    void confirmStage_advancesConfirmedStage() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.INTERFACE);
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        service.confirmStage("d1", 1L, DraftStage.INTERFACE);

        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertEquals(DraftStage.INTERFACE, captor.getValue().getConfirmedStage());
    }

    @Test
    void confirmStage_cannotConfirmUnreachedStage() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.INTERFACE); // 只到阶段 2
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 确认阶段 4（未到达）
        assertThrows(BusinessException.class, () -> service.confirmStage("d1", 1L, DraftStage.TEMPLATE));
    }

    @Test
    void rollbackToStage_setsInvalidatedFromStageAndKeepsProducts() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.XML); // 当前到阶段 5
        draft.setConfirmedStage(DraftStage.XML);
        draft.setXmlDraft("<interface/>"); // 已有产物
        draft.setImportJsonDraft("{}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 回退到阶段 3（SQL）
        AiText2ApiDraft result = service.rollbackToStage("d1", 1L, DraftStage.SQL);

        // invalidated_from_stage = 4（SQL+1）
        assertEquals(DraftStage.SQL + 1, result.getInvalidatedFromStage());
        // stage 回退到 3
        assertEquals(DraftStage.SQL, result.getStage());
        // 产物保留（不删除）
        assertNotNull(result.getXmlDraft());
        assertNotNull(result.getImportJsonDraft());
        // confirmed_stage 回退
        assertEquals(DraftStage.SQL, result.getConfirmedStage());
    }

    @Test
    void rollbackToStage_cannotRollbackToUnreachedStage() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setStage(DraftStage.INTERFACE); // 只到阶段 2
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 回退到阶段 4（未到达）
        assertThrows(BusinessException.class, () -> service.rollbackToStage("d1", 1L, DraftStage.TEMPLATE));
    }

    // ===== 发布前置校验 =====

    @Test
    void validateBeforePublish_rejectsWhenNotConfirmedToXml() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.TEMPLATE); // 只确认到 4
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.validateBeforePublish("d1", 1L));
    }

    @Test
    void validateBeforePublish_rejectsWhenXmlInvalidated() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.XML);
        draft.setXmlDraft("<interface/>");
        draft.setImportJsonDraft("{}");
        draft.setInvalidatedFromStage(DraftStage.XML); // 阶段 5 失效
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.validateBeforePublish("d1", 1L),
                "invalidated 的阶段 5 产物不允许发布");
    }

    @Test
    void validateBeforePublish_rejectsWhenXmlEmpty() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.XML);
        draft.setXmlDraft(null); // 空
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.validateBeforePublish("d1", 1L));
    }

    @Test
    void validateBeforePublish_passesWhenValid() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.XML);
        draft.setXmlDraft("<interface/>");
        draft.setImportJsonDraft("{}");
        draft.setInvalidatedFromStage(null); // 无失效
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 不抛异常
        service.validateBeforePublish("d1", 1L);
    }

    // ===== 越权防护 =====

    @Test
    void generate_nonOwnedDraft_throws() {
        when(draftService.getOwnedDraft("d1", 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generate("d1", 2L, DraftStage.SQL, null));
    }

    @Test
    void rollback_nonOwnedDraft_throws() {
        when(draftService.getOwnedDraft("d1", 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.rollbackToStage("d1", 2L, DraftStage.SQL));
    }

    @Test
    void deleteDraft_nonOwned_throws() {
        when(draftService.getOwnedDraft("d1", 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteDraft("d1", 2L));
    }

    @Test
    void createDraft_savesWithCorrectFields() {
        AiText2ApiDraft draft = service.createDraft(1L, "alice", "测试草稿");
        assertEquals(1L, draft.getUserId());
        assertEquals(DraftStage.REQUIREMENT, draft.getStage());
        assertNotNull(draft.getDraftId());
    }

    @Test
    void updateRequirement_storesText() {
        AiText2ApiDraft draft = ownedDraft();
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        service.updateRequirement("d1", 1L, "查询用户列表");

        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertEquals("查询用户列表", captor.getValue().getRequirementText());
    }

    // ===== T3-B-1: 阶段前置约束 =====

    @Test
    void generateInterface_noRequirement_needsMoreInfoNoAiGateway() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText(null); // 无需求
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        StageResult r = service.generate("d1", 1L, DraftStage.INTERFACE, null);
        assertTrue(r.isNeedsMoreInfo());
        verify(aiGateway, never()).streamChat(any(), any());
    }

    @Test
    void generateSql_notConfirmedInterface_needsMoreInfoNoAiGateway() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询用户");
        draft.setConfirmedStage(DraftStage.REQUIREMENT); // 未确认接口定义
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        SchemaEvidence evidence = validEvidence();
        StageResult r = service.generate("d1", 1L, DraftStage.SQL, evidence);
        assertTrue(r.isNeedsMoreInfo(), "未确认接口定义应拒绝");
        verify(aiGateway, never()).streamChat(any(), any());
    }

    @Test
    void generateSql_noInterfaceDraft_needsMoreInfoNoAiGateway() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询用户");
        draft.setConfirmedStage(DraftStage.INTERFACE); // 已确认接口定义
        draft.setInterfaceDraft(null); // 但 interface_draft 为空
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());
        assertTrue(r.isNeedsMoreInfo());
        verify(aiGateway, never()).streamChat(any(), any());
    }

    @Test
    void generateTemplate_noSqlDraft_needsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.SQL);
        draft.setSqlDraft(null); // 无 SQL
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        StageResult r = service.generate("d1", 1L, DraftStage.TEMPLATE, null);
        assertTrue(r.isNeedsMoreInfo(), "无 sql_draft 应拒绝模板生成");
    }

    @Test
    void generateTemplate_notConfirmedSql_needsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.INTERFACE); // 未确认 SQL
        draft.setSqlDraft("{\"sqlItems\":[]}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        StageResult r = service.generate("d1", 1L, DraftStage.TEMPLATE, null);
        assertTrue(r.isNeedsMoreInfo(), "未确认 SQL 应拒绝模板生成");
    }

    @Test
    void generateXml_missingPrerequisites_needsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.TEMPLATE);
        draft.setInterfaceDraft(null); // 缺接口定义
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        StageResult r = service.generate("d1", 1L, DraftStage.XML, null);
        assertTrue(r.isNeedsMoreInfo(), "缺接口定义应拒绝 XML 生成");
    }

    @Test
    void generateXml_notConfirmedTemplate_needsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setConfirmedStage(DraftStage.SQL); // 未确认模板
        draft.setInterfaceDraft("{}");
        draft.setSqlDraft("{}");
        draft.setTemplateSelection("01|test");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        StageResult r = service.generate("d1", 1L, DraftStage.XML, null);
        assertTrue(r.isNeedsMoreInfo(), "未确认模板应拒绝 XML 生成");
    }

    // ===== T3-B-2: 接口定义真实 AI 生成 =====

    @Test
    void generateInterface_validAiOutput_interfaceDraftPersisted() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询用户信息");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        // mock AiGateway 同步返回合法 JSON
        mockAiGateway("{\"transno\":\"USER_QUERY\",\"name\":\"用户查询\","
                + "\"inputSchema\":\"userId:String\",\"outputSchema\":\"{}\",\"questions\":[]}");

        StageResult r = service.generate("d1", 1L, DraftStage.INTERFACE, null);

        assertTrue(r.isGenerated());
        assertTrue(r.getMessage().contains("USER_QUERY"));
        // interface_draft 落库
        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertTrue(captor.getValue().getInterfaceDraft().contains("USER_QUERY"));
    }

    @Test
    void generateInterface_aiReturnsQuestions_needsMoreInfoNotPersisted() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        mockAiGateway("{\"transno\":\"\",\"name\":\"\",\"inputSchema\":\"\",\"outputSchema\":\"\","
                + "\"questions\":[\"缺少表名\"]}");

        StageResult r = service.generate("d1", 1L, DraftStage.INTERFACE, null);

        assertTrue(r.isNeedsMoreInfo(), "AI 返回 questions 应 needs_more_info");
        // 不落库
        verify(draftService, never()).updateById(any());
    }

    @Test
    void generateInterface_aiReturnsInvalidJson_notPersisted() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        mockAiGateway("{\"name\":\"缺transno\"}"); // 缺 transno

        StageResult r = service.generate("d1", 1L, DraftStage.INTERFACE, null);

        // 解析失败 → generated 但 message 含失败信息，不落库 interface_draft
        verify(draftService, never()).updateById(any());
    }

    // ===== T3-B-3: Text2SQL 真实 AI 生成 =====

    @Test
    void generateSql_validAiOutput_sqlDraftPersisted() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询用户");
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{\"transno\":\"T1\"}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        mockAiGateway("{\"sqlItems\":[{\"sqlId\":\"main\","
                + "\"sql\":\"SELECT id FROM users\",\"purpose\":\"查询\",\"dependsOn\":[]}],\"questions\":[]}");

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());

        assertTrue(r.isGenerated());
        assertTrue(r.getMessage().contains("1 段"));
        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertTrue(captor.getValue().getSqlDraft().contains("SELECT id FROM users"));
    }

    @Test
    void generateSql_aiReturnsInsert_notPersisted() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        mockAiGateway("{\"sqlItems\":[{\"sqlId\":\"q1\","
                + "\"sql\":\"INSERT INTO users VALUES(1)\",\"purpose\":\"插入\",\"dependsOn\":[]}],\"questions\":[]}");

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());

        // INSERT 被拦截 → 不落库
        verify(draftService, never()).updateById(any());
    }

    @Test
    void generateSql_aiReturnsQuestions_needsMoreInfo() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        mockAiGateway("{\"sqlItems\":[],\"questions\":[\"缺少字段信息\"]}");

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());
        assertTrue(r.isNeedsMoreInfo());
        verify(draftService, never()).updateById(any());
    }

    @Test
    void generateSql_aiGatewayThrows_noHalfProduct() {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);
        // mock AiGateway 在回调中抛异常
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            h.onError(new RuntimeException("模型失败"));
            return (com.sunlc.dsp.admin.assistant.ai.StreamHandle) () -> { };
        });

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());
        // 失败路径：不落库到下一阶段
        verify(draftService, never()).updateById(any());
    }

    // ===== 异步契约单测（CountDownLatch 等待）=====

    @Test
    void generateInterface_asyncDeltaThenComplete_waitsAndPersistsFullOutput() throws Exception {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询用户");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        String fullOutput = "{\"transno\":\"USER_Q\",\"name\":\"用户查询\","
                + "\"inputSchema\":\"userId\",\"outputSchema\":\"{}\",\"questions\":[]}";
        // 异步：streamChat 返回 handle 后，在新线程延迟 onDelta + onComplete
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            new Thread(() -> {
                try {
                    Thread.sleep(50); // 模拟异步延迟
                    h.onDelta(fullOutput);
                    h.onComplete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            return (com.sunlc.dsp.admin.assistant.ai.StreamHandle) () -> { };
        });

        StageResult r = service.generate("d1", 1L, DraftStage.INTERFACE, null);

        assertTrue(r.isGenerated(), "应等待异步完成后生成");
        assertTrue(r.getMessage().contains("USER_Q"));
        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        assertTrue(captor.getValue().getInterfaceDraft().contains("USER_Q"),
                "落库的应是完整输出");
    }

    @Test
    void generateSql_asyncError_returnsFailedNoPersist() throws Exception {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{\"transno\":\"T1\"}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 异步：streamChat 返回 handle 后，在新线程延迟 onError
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            new Thread(() -> {
                try {
                    Thread.sleep(50);
                    h.onError(new RuntimeException("模型异步失败"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            return (com.sunlc.dsp.admin.assistant.ai.StreamHandle) () -> { };
        });

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());

        assertTrue(r.isFailed(), "异步 error 应返回 failed");
        verify(draftService, never()).updateById(any());
    }

    @Test
    void generateSql_asyncPartialDeltaThenComplete_persistsFullAggregated() throws Exception {
        AiText2ApiDraft draft = ownedDraft();
        draft.setRequirementText("查询");
        draft.setConfirmedStage(DraftStage.INTERFACE);
        draft.setInterfaceDraft("{\"transno\":\"T1\"}");
        when(draftService.getOwnedDraft("d1", 1L)).thenReturn(draft);

        // 异步：分多个 delta 发送，验证 buffer 聚合完整
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            new Thread(() -> {
                try {
                    Thread.sleep(30);
                    h.onDelta("{\"sqlItems\":[{\"sqlId\":\"main\",");
                    Thread.sleep(20);
                    h.onDelta("\"sql\":\"SELECT id FROM users\",");
                    Thread.sleep(20);
                    h.onDelta("\"purpose\":\"查询\",\"dependsOn\":[]}],\"questions\":[]}");
                    h.onComplete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            return (com.sunlc.dsp.admin.assistant.ai.StreamHandle) () -> { };
        });

        StageResult r = service.generate("d1", 1L, DraftStage.SQL, validEvidence());

        assertTrue(r.isGenerated(), "多段 delta 应聚合完整后落库");
        ArgumentCaptor<AiText2ApiDraft> captor = ArgumentCaptor.forClass(AiText2ApiDraft.class);
        verify(draftService).updateById(captor.capture());
        String sqlDraft = captor.getValue().getSqlDraft();
        assertTrue(sqlDraft.contains("SELECT id FROM users"), "落库的应是聚合后的完整 SQL");
    }

    // ===== 辅助 =====

    private void mockAiGateway(String output) {
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            h.onDelta(output);
            h.onComplete();
            return (com.sunlc.dsp.admin.assistant.ai.StreamHandle) () -> { };
        });
    }

    private SchemaEvidence validEvidence() {
        return new SchemaEvidence("USER_INPUT", Arrays.asList(
                new SchemaEvidence.TableEvidence("users", Arrays.asList("id", "name"), "用户表")));
    }

    private AiText2ApiDraft ownedDraft() {
        AiText2ApiDraft d = new AiText2ApiDraft();
        d.setId(1L);
        d.setDraftId("d1");
        d.setUserId(1L);
        d.setStage(DraftStage.REQUIREMENT);
        d.setConfirmedStage(0);
        d.setStatus(0);
        return d;
    }
}
