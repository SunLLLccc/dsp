package com.sunlc.dsp.admin.assistant.chat;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunlc.dsp.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能助手 chat Controller。
 * <p>
 * 所有端点走 {@code AdminAuthInterceptor}（/dsp/admin/** 默认覆盖），无 @RequireRole，一期所有登录用户可用。
 * 会话归属校验在 {@link AssistantChatService} 内部完成（先 getOwnedSession 再操作）。
 * <p>
 * 契约：Controller 只注入 {@link AssistantChatService}，不直接注入 {@code AiChatMessageService}。
 */
@Slf4j
@RestController
@RequestMapping("/dsp/admin/assistant/chat")
@RequiredArgsConstructor
public class AssistantChatController {

    private static final String TRANSNO = "ASSISTANT_CHAT";

    private final AssistantChatService assistantChatService;

    /** 创建会话 */
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionVO> createSession(@RequestBody(required = false) CreateSessionRequest req,
                                                    HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        String userName = CurrentUserResolver.resolveUserName(request);
        String title = req == null ? null : req.getTitle();
        return ApiResponse.success(TRANSNO, "", assistantChatService.createSession(userId, userName, title));
    }

    /** 会话列表 */
    @GetMapping("/sessions")
    public ApiResponse<IPage<ChatSessionVO>> listSessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        return ApiResponse.success(TRANSNO, "", assistantChatService.listSessions(userId, pageNum, pageSize));
    }

    /** 历史消息 */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageVO>> listMessages(@PathVariable String sessionId,
                                                         HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        return ApiResponse.success(TRANSNO, "", assistantChatService.listOwnedMessages(sessionId, userId));
    }

    /** 提问（SSE 流式回答） */
    @PostMapping("/ask")
    public SseEmitter ask(@RequestBody AskRequest req, HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        return assistantChatService.ask(req, userId);
    }

    /** 删除会话（逻辑删除 session） */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId, HttpServletRequest request) {
        Long userId = CurrentUserResolver.requireUserId(request);
        assistantChatService.deleteSession(sessionId, userId);
        return ApiResponse.success(TRANSNO, "", null);
    }
}
