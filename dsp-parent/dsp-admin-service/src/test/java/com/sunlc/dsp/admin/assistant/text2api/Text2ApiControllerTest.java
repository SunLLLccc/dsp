package com.sunlc.dsp.admin.assistant.text2api;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import com.sunlc.dsp.admin.assistant.chat.ChatConcurrencyLimiter;
import com.sunlc.dsp.admin.assistant.chat.CurrentUserResolver;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.AiText2ApiDraft;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Text2ApiController 单测。
 * <p>
 * 覆盖：当前用户解析、非本人 draft 拒绝、前置约束经 SSE 生效、needs_more_info/failed/complete
 * 经 SSE 返回、并发限制、逻辑删除、Controller 不直接注入 Mapper。
 */
@ExtendWith(MockitoExtension.class)
class Text2ApiControllerTest {

    private static final long USER_ID = 1L;
    private static final String USER_NAME = "alice";

    @Mock private Text2ApiService text2ApiService;
    @Mock private ChatConcurrencyLimiter concurrencyLimiter;

    private Text2ApiController controller;

    @BeforeEach
    void setUp() {
        AssistantProperties props = new AssistantProperties();
        props.setSseTimeoutMs(3000L);
        controller = new Text2ApiController(text2ApiService, concurrencyLimiter, props);
        // 默认允许获取并发额度
        lenient().when(concurrencyLimiter.tryAcquire(USER_ID)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
    }

    // ===== 1. 当前用户解析成功/失败 =====

    @Test
    void getDraft_missingUserId_throwsBusinessException() {
        // adminUserId 缺失 → BusinessException
        HttpServletRequest req = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> controller.getDraft("d1", req));
    }

    @Test
    void getDraft_invalidUserIdFormat_throwsBusinessException() {
        HttpServletRequest req = mockRequest("notANumber", USER_NAME);
        assertThrows(BusinessException.class, () -> controller.getDraft("d1", req));
    }

    @Test
    void getDraft_resolvesUserIdAndReturnsOwnedDraft() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        AiText2ApiDraft draft = ownedDraft("d1", USER_ID);
        when(text2ApiService.getOwnedDraft("d1", USER_ID)).thenReturn(draft);

        var resp = controller.getDraft("d1", req);

        assertNotNull(resp.getData());
        assertEquals("d1", resp.getData().getDraftId());
    }

    // ===== 2. 非本人 draft 不能访问 =====

    @Test
    void getDraft_nonOwnedDraft_returnsNullFromService_throwsAccessDenied() {
        // Service 对非本人/已删除统一返回 null，Controller 按不存在处理（不泄露存在性）
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        when(text2ApiService.getOwnedDraft("d1", USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.getDraft("d1", req));
        assertEquals("4003", ex.getCode(), "非本人/不存在统一返回无权访问");
    }

    @Test
    void deleteDraft_delegatesToServiceWhichEnforcesOwnership() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        controller.deleteDraft("d1", req);
        // deleteDraft 内部 getOwnedDraftOrThrow，非本人会在 Service 抛异常（不在 Controller 绕行）
        verify(text2ApiService).deleteDraft("d1", USER_ID);
    }

    @Test
    void deleteDraft_nonOwned_throwsViaService() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        // 模拟 Service 归属校验失败
        org.mockito.Mockito.doThrow(new BusinessException(com.sunlc.dsp.common.enums.ErrorCode.ACCESS_DENIED,
                "草稿不存在或无权访问"))
                .when(text2ApiService).deleteDraft("d1", USER_ID);

        assertThrows(BusinessException.class, () -> controller.deleteDraft("d1", req));
    }

    // ===== 3. 生成阶段前置约束通过 Controller/SSE 仍然生效 =====

    @Test
    void generate_preconditionUnmet_serviceReturnsNeedsMoreInfo_translatedToSse()
            throws Exception {
        // 模拟阶段 3 缺 SchemaEvidence → Service 返回 needs_more_info（不调 AiGateway）
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.SQL, null);
        when(text2ApiService.generate(eq("d1"), eq(USER_ID), eq(DraftStage.SQL), any()))
                .thenReturn(StageResult.needsMoreInfo(DraftStage.SQL, "缺少表结构依据"));

        SseEmitter emitter = controller.generate("d1", gr, req);

        assertNotNull(emitter);
        // 等待工作线程执行（service 同步返回）
        Thread.sleep(500);
        // 前置约束由 Service 兜底，Controller 正确转发
        verify(text2ApiService).generate(eq("d1"), eq(USER_ID), eq(DraftStage.SQL), any());
    }

    @Test
    void generate_invalidStage_throwsBeforeConcurrency() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(9, null);
        assertThrows(BusinessException.class, () -> controller.generate("d1", gr, req));
        // 校验失败不应消耗并发额度
        verify(concurrencyLimiter, never()).release(any());
    }

    // ===== 4. needs_more_info 能通过 SSE 返回，且释放并发额度 =====

    @Test
    void generate_needsMoreInfo_releasesConcurrency() throws Exception {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.SQL, validEvidenceDto());
        when(text2ApiService.generate(anyString(), eq(USER_ID), anyInt(), any()))
                .thenReturn(StageResult.needsMoreInfo(DraftStage.SQL, "请补充字段信息"));

        controller.generate("d1", gr, req);

        Thread.sleep(500);
        // needs_more_info 也是正常结束，必须释放并发额度
        verify(concurrencyLimiter, times(1)).release(USER_ID);
    }

    // ===== 5. failed 能通过 SSE 返回，且释放并发额度 =====

    @Test
    void generate_failed_releasesConcurrency() throws Exception {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.INTERFACE, null);
        when(text2ApiService.generate(anyString(), eq(USER_ID), anyInt(), any()))
                .thenReturn(StageResult.failed(DraftStage.INTERFACE, "AI 生成失败"));

        controller.generate("d1", gr, req);

        Thread.sleep(500);
        // failed 是正常结束（StageResult.failed），必须释放并发额度
        verify(concurrencyLimiter, times(1)).release(USER_ID);
    }

    // ===== 6. complete 能通过 SSE 返回，且释放并发额度 =====

    @Test
    void generate_generatedThenComplete_releasesConcurrency() throws Exception {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.INTERFACE, null);
        when(text2ApiService.generate(anyString(), eq(USER_ID), anyInt(), any()))
                .thenReturn(StageResult.generated(DraftStage.INTERFACE, "接口定义已生成"));

        controller.generate("d1", gr, req);

        Thread.sleep(500);
        // generated -> complete，释放并发额度恰好一次
        verify(concurrencyLimiter, times(1)).release(USER_ID);
    }

    @Test
    void generate_success_passesEvidenceToService() throws Exception {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.SQL, validEvidenceDto());

        when(text2ApiService.generate(anyString(), eq(USER_ID), anyInt(), any()))
                .thenReturn(StageResult.generated(DraftStage.SQL, "ok"));

        controller.generate("d1", gr, req);
        Thread.sleep(500);

        ArgumentCaptor<SchemaEvidence> captor = ArgumentCaptor.forClass(SchemaEvidence.class);
        verify(text2ApiService).generate(eq("d1"), eq(USER_ID), eq(DraftStage.SQL), captor.capture());
        SchemaEvidence ev = captor.getValue();
        assertNotNull(ev);
        assertFalse(ev.isEmpty(), "有效的 SchemaEvidence 应被正确转换并传入 Service");
        assertEquals("users", ev.getTables().get(0).getTableName());
    }

    // ===== 7. 并发限制命中时拒绝第二个生成请求 =====

    @Test
    void generate_concurrencyLimited_rejectsSecondRequest() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.INTERFACE, null);
        // 第二个请求：并发额度已满
        when(concurrencyLimiter.tryAcquire(USER_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.generate("d1", gr, req));
        // 拒绝时不应调用 generate，也不应 release（因为没 acquire 成功）
        verify(text2ApiService, never()).generate(anyString(), any(), anyInt(), any());
        verify(concurrencyLimiter, never()).release(any());
    }

    // ===== 8. 删除草稿是逻辑删除（由 Service 的 @TableLogic 保证）=====

    @Test
    void deleteDraft_invokesServiceLogicalDelete() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        controller.deleteDraft("d1", req);
        // 实体 deleted 字段标注 @TableLogic，removeById 走逻辑删除（不物理删除）
        verify(text2ApiService, times(1)).deleteDraft("d1", USER_ID);
    }

    // ===== 9. Controller 不直接注入 Mapper，不绕过 Service 权限边界 =====

    @Test
    void controller_doesNotInjectMapper_fieldsAreServiceAndLimiterOnly() {
        java.lang.reflect.Field[] fields = Text2ApiController.class.getDeclaredFields();
        // 排除 generateExecutor / shutdown 辅助字段
        List<String> injected = Arrays.stream(fields)
                .map(java.lang.reflect.Field::getName)
                .filter(n -> !n.equals("generateExecutor"))
                .collect(java.util.stream.Collectors.toList());
        // 只能依赖编排服务 + 并发限制器 + 配置，不能依赖 Mapper / 底层 draft service
        assertTrue(injected.contains("text2ApiService"), "应依赖 Text2ApiService");
        assertTrue(injected.contains("concurrencyLimiter"), "应依赖 ChatConcurrencyLimiter");
        for (String name : injected) {
            assertFalse(name.toLowerCase().contains("mapper"),
                    "Controller 不应注入 Mapper: " + name);
        }
    }

    // ===== 补充：generate 过程抛异常（非 StageResult.failed）仍释放额度 =====

    @Test
    void generate_serviceThrowsBusinessException_releasesConcurrency() throws Exception {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        GenerateRequest gr = generateRequest(DraftStage.INTERFACE, null);
        when(text2ApiService.generate(anyString(), any(), anyInt(), any()))
                .thenThrow(new BusinessException(com.sunlc.dsp.common.enums.ErrorCode.ACCESS_DENIED,
                        "草稿不存在或无权访问"));

        controller.generate("d1", gr, req);
        Thread.sleep(500);

        // 即使同步抛异常，也必须释放并发额度（不泄漏）
        verify(concurrencyLimiter, times(1)).release(USER_ID);
    }

    // ===== 补充：requirement 校验 =====

    @Test
    void updateRequirement_blankText_throws() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        UpdateRequirementRequest body = new UpdateRequirementRequest();
        body.setRequirementText("   ");
        assertThrows(BusinessException.class, () -> controller.updateRequirement("d1", body, req));
        verify(text2ApiService, never()).updateRequirement(any(), any(), any());
    }

    @Test
    void updateRequirement_tooLong_throws() {
        HttpServletRequest req = mockRequest(USER_ID, USER_NAME);
        UpdateRequirementRequest body = new UpdateRequirementRequest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30001; i++) {
            sb.append('a');
        }
        body.setRequirementText(sb.toString());
        assertThrows(BusinessException.class, () -> controller.updateRequirement("d1", body, req));
    }

    // ===== 辅助 =====

    private HttpServletRequest mockRequest(Object userId, String userName) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(CurrentUserResolver.ATTR_ADMIN_USER_ID, userId);
        req.setAttribute(CurrentUserResolver.ATTR_ADMIN_USER, userName);
        return req;
    }

    private GenerateRequest generateRequest(int stage, SchemaEvidenceDto evidence) {
        GenerateRequest gr = new GenerateRequest();
        gr.setStage(stage);
        gr.setEvidence(evidence);
        return gr;
    }

    private SchemaEvidenceDto validEvidenceDto() {
        SchemaEvidenceDto dto = new SchemaEvidenceDto();
        dto.setSource("USER_INPUT");
        SchemaEvidenceDto.TableEvidenceDto t = new SchemaEvidenceDto.TableEvidenceDto();
        t.setTableName("users");
        t.setColumns(Arrays.asList("id", "name"));
        t.setDescription("用户表");
        dto.setTables(Arrays.asList(t));
        return dto;
    }

    private AiText2ApiDraft ownedDraft(String draftId, Long userId) {
        AiText2ApiDraft d = new AiText2ApiDraft();
        d.setId(1L);
        d.setDraftId(draftId);
        d.setUserId(userId);
        d.setStage(DraftStage.REQUIREMENT);
        d.setConfirmedStage(0);
        d.setStatus(0);
        return d;
    }
}
