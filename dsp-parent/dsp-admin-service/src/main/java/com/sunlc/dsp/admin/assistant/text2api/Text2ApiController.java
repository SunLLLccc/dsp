package com.sunlc.dsp.admin.assistant.text2api;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import com.sunlc.dsp.admin.assistant.chat.ChatConcurrencyLimiter;
import com.sunlc.dsp.admin.assistant.chat.CurrentUserResolver;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.AiText2ApiDraft;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Text2API Web Controller。
 * <p>
 * 路径前缀 {@code /dsp/admin/assistant/text2api}，走 {@code AdminAuthInterceptor}。
 * <p>
 * 契约：Controller 只注入 {@link Text2ApiService}（编排服务），不直接注入 Mapper 或底层 draft service，
 * 不绕过 Service 的归属校验边界。
 * <p>
 * 并发：AI 生成阶段复用 chat 的 {@link ChatConcurrencyLimiter}（同一用户全局单 AI 生成额度）。
 * release 覆盖 complete/failed/cancel/throw。
 * <p>
 * T4 范围：草稿 CRUD + 阶段生成（SSE）+ confirm/rollback。
 * <b>不含发布</b>（发布留 T5）。
 */
@Slf4j
@RestController
@RequestMapping("/dsp/admin/assistant/text2api")
@RequiredArgsConstructor
public class Text2ApiController {

    private static final String TRANSNO = "ASSISTANT_TEXT2API";
    /** 需求文本最大长度（字符）。 */
    private static final int MAX_REQUIREMENT_LENGTH = 30000;

    private final Text2ApiService text2ApiService;
    private final ChatConcurrencyLimiter concurrencyLimiter;
    private final AssistantProperties assistantProperties;

    /**
     * 阶段生成执行器：有界线程池，避免无限制创建线程。
     * <p>
     * 拒绝策略用 {@link ThreadPoolExecutor.AbortPolicy}：队列满时抛
     * {@link java.util.concurrent.RejectedExecutionException}，由 {@link #runGeneration}
     * 的 catch 分支转成 failed + complete + release，确保不拖住 servlet 请求线程、不泄漏并发额度。
     * （不用 CallerRunsPolicy：那会在请求线程同步跑生成任务，与"SSE 生成在后台线程"的语义冲突。）
     */
    private final ThreadPoolExecutor generateExecutor = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(64),
            r -> {
                Thread t = new Thread(r, "text2api-generate");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy
    void shutdown() {
        generateExecutor.shutdownNow();
    }

    // ===== 草稿管理 =====

    /** 创建草稿。 */
    @PostMapping("/drafts")
    public ApiResponse<DraftVO> createDraft(@RequestBody(required = false) CreateDraftRequest req,
                                            HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        String userName = CurrentUserResolver.resolveUserName(request);
        String title = req == null ? null : req.getTitle();
        AiText2ApiDraft draft = text2ApiService.createDraft(userId, userName, title);
        return ApiResponse.success(TRANSNO, "", DraftVO.from(draft));
    }

    /** 当前用户草稿列表。 */
    @GetMapping("/drafts")
    public ApiResponse<List<DraftVO>> listDrafts(HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        List<AiText2ApiDraft> drafts = text2ApiService.listDrafts(userId);
        List<DraftVO> vos = drafts == null ? Collections.emptyList()
                : drafts.stream().map(DraftVO::from).collect(Collectors.toList());
        return ApiResponse.success(TRANSNO, "", vos);
    }

    /** 草稿详情（归属校验在 Service 内，非本人按不存在处理）。 */
    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftVO> getDraft(@PathVariable String draftId, HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        AiText2ApiDraft draft = text2ApiService.getOwnedDraft(draftId, userId);
        if (draft == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "草稿不存在或无权访问");
        }
        return ApiResponse.success(TRANSNO, "", DraftVO.from(draft));
    }

    /** 逻辑删除草稿。 */
    @DeleteMapping("/drafts/{draftId}")
    public ApiResponse<Void> deleteDraft(@PathVariable String draftId, HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        text2ApiService.deleteDraft(draftId, userId);
        return ApiResponse.success(TRANSNO, "", null);
    }

    /** 更新需求文本（阶段 1）。 */
    @PutMapping("/drafts/{draftId}/requirement")
    public ApiResponse<DraftVO> updateRequirement(@PathVariable String draftId,
                                                  @RequestBody UpdateRequirementRequest req,
                                                  HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        if (req == null || req.getRequirementText() == null || req.getRequirementText().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requirementText 不能为空");
        }
        if (req.getRequirementText().length() > MAX_REQUIREMENT_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "需求文本长度超过上限 " + MAX_REQUIREMENT_LENGTH + " 字符");
        }
        AiText2ApiDraft draft = text2ApiService.updateRequirement(draftId, userId, req.getRequirementText());
        return ApiResponse.success(TRANSNO, "", DraftVO.from(draft));
    }

    // ===== 阶段流转：confirm / rollback =====

    /** 确认阶段（推进 confirmed_stage）。 */
    @PostMapping("/drafts/{draftId}/confirm")
    public ApiResponse<DraftVO> confirmStage(@PathVariable String draftId,
                                             @RequestBody ConfirmStageRequest req,
                                             HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        int stage = validateStage(req);
        AiText2ApiDraft draft = text2ApiService.confirmStage(draftId, userId, stage);
        return ApiResponse.success(TRANSNO, "", DraftVO.from(draft));
    }

    /** 回退阶段（设置 invalidated_from_stage）。 */
    @PostMapping("/drafts/{draftId}/rollback")
    public ApiResponse<DraftVO> rollbackStage(@PathVariable String draftId,
                                              @RequestBody ConfirmStageRequest req,
                                              HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        int stage = validateStage(req);
        AiText2ApiDraft draft = text2ApiService.rollbackToStage(draftId, userId, stage);
        return ApiResponse.success(TRANSNO, "", DraftVO.from(draft));
    }

    // ===== 发布（T5）=====

    /**
     * 发布草稿：校验前置条件后调用 ConfigImportService.importConfig 完成接口导入。
     * <p>
     * 前置不足 / 导入失败均抛 BusinessException，由统一异常处理返回错误响应；
     * 失败已落库 publishError，前端可拉取 draft 展示并重新发布（允许重试）。
     * <p>
     * Controller 不直接注入 ConfigImportService，发布逻辑全在 {@link Text2ApiService#publish} 内。
     */
    @PostMapping("/drafts/{draftId}/publish")
    public ApiResponse<DraftVO> publish(@PathVariable String draftId, HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        String operator = CurrentUserResolver.resolveUserName(request);
        if (operator == null || operator.isBlank()) {
            operator = String.valueOf(userId);
        }
        AiText2ApiDraft draft = text2ApiService.publish(draftId, userId, operator);
        return ApiResponse.success(TRANSNO, "", DraftVO.from(draft));
    }

    // ===== 阶段生成（SSE）=====

    /**
     * 阶段生成（SSE 流式响应）。
     * <p>
     * 阶段 2/3/4/5 可生成；阶段 1 用 {@link #updateRequirement}。
     * 阶段 6 发布不在 T4。
     * <p>
     * 事件链：{@code start -> (result|needs_more_info|failed) -> complete}。
     * 取消/超时/断开发 {@code cancelled}；SSE 生命周期异常发 {@code error}。
     * 并发额度在 complete/cancelled/error/throw 任一终态释放，幂等。
     */
    @PostMapping("/drafts/{draftId}/generate")
    public SseEmitter generate(@PathVariable String draftId,
                               @RequestBody GenerateRequest req,
                               HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        int stage = validateGenerateRequest(req);
        SchemaEvidence evidence = toSchemaEvidence(req.getEvidence());

        // 并发限制：单用户全局单 AI 生成（与 chat 共用额度）
        if (!concurrencyLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前 AI 生成并发数已达上限，请稍后再试");
        }

        // emitter：终态释放并发额度（幂等）
        Text2ApiSseEmitter emitter = new Text2ApiSseEmitter(
                assistantProperties.getSseTimeoutMs(),
                () -> concurrencyLimiter.release(userId));
        emitter.sendStart();

        // 并发额度已获取，后续任何异常都必须 release
        runGeneration(emitter, draftId, userId, stage, evidence);
        return emitter;
    }

    // ===== 内部：异步执行阶段生成 =====

    /**
     * 在执行器线程中执行同步 {@link Text2ApiService#generate}，
     * 把 {@link StageResult} 映射为 SSE 事件，并在工作线程中断点绑定取消句柄。
     * <p>
     * 异常安全：tryAcquire 之后任何失败都通过 emitter 终态事件 + release 覆盖。
     */
    private void runGeneration(Text2ApiSseEmitter emitter, String draftId,
                               Long userId, int stage, SchemaEvidence evidence) {
        // 用 final 数组持有工作线程引用，供取消句柄中断
        final Thread[] workerHolder = new Thread[1];
        emitter.bindCanceller(() -> {
            Thread w = workerHolder[0];
            if (w != null) {
                w.interrupt();
            }
        });

        try {
            generateExecutor.submit(() -> {
                workerHolder[0] = Thread.currentThread();
                try {
                    StageResult result = text2ApiService.generate(draftId, userId, stage, evidence);
                    emitter.sendStageResult(result);
                    emitter.completeNormally();
                } catch (Throwable e) {
                    handleGenerationThrow(emitter, stage, e);
                } finally {
                    workerHolder[0] = null;
                }
            });
        } catch (RuntimeException e) {
            // 执行器拒绝（队列满）等：直接发 failed + complete，并释放额度
            log.warn("text2api 生成任务提交失败: draftId={}", draftId, e);
            emitter.sendStageResult(StageResult.failed(stage, "生成任务提交失败，请稍后重试"));
            emitter.completeNormally();
        }
    }

    /** 生成过程抛异常（非 StageResult.failed，而是 BusinessException 等）：转 failed 事件。 */
    private void handleGenerationThrow(Text2ApiSseEmitter emitter, int stage, Throwable e) {
        if (Thread.currentThread().isInterrupted()) {
            // 取消：completeNormally 已由生命周期/取消流程处理 release，这里不再重复发事件
            // 但为保险仍走 complete（幂等）
            emitter.completeNormally();
            return;
        }
        log.warn("text2api 阶段生成抛异常", e);
        String msg = e instanceof BusinessException ? e.getMessage() : "阶段生成异常";
        emitter.sendStageResult(StageResult.failed(stage, msg));
        emitter.completeNormally();
    }

    // ===== 请求校验 =====

    /** confirm/rollback 的 stage 校验。 */
    private int validateStage(ConfirmStageRequest req) {
        if (req == null || req.getStage() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "stage 不能为空");
        }
        int stage = req.getStage();
        if (!DraftStage.isValid(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法阶段: " + stage);
        }
        return stage;
    }

    /** generate 请求校验：stage 合法且为可生成阶段（2-5）。 */
    private int validateGenerateRequest(GenerateRequest req) {
        if (req == null || req.getStage() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "stage 不能为空");
        }
        int stage = req.getStage();
        if (!DraftStage.isValid(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法阶段: " + stage);
        }
        if (stage == DraftStage.REQUIREMENT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "需求阶段无需 AI 生成，请使用更新需求接口");
        }
        if (stage == DraftStage.PUBLISHED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段 6 发布未开放");
        }
        return stage;
    }

    /** SchemaEvidenceDto -> SchemaEvidence。结构合法性在此校验，是否充分由 Service 门禁兜底。 */
    private SchemaEvidence toSchemaEvidence(SchemaEvidenceDto dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getTables() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "evidence.tables 不能为空（可为空数组）");
        }
        List<SchemaEvidence.TableEvidence> tables = new ArrayList<>();
        for (SchemaEvidenceDto.TableEvidenceDto t : dto.getTables()) {
            if (t == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "evidence.tables 含空元素");
            }
            List<String> columns = t.getColumns() == null
                    ? Collections.emptyList() : new ArrayList<>(t.getColumns());
            tables.add(new SchemaEvidence.TableEvidence(t.getTableName(), columns, t.getDescription()));
        }
        return new SchemaEvidence(dto.getSource(), tables);
    }
}
