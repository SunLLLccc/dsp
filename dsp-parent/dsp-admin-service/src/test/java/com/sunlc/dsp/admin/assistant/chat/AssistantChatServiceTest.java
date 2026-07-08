package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.admin.assistant.ai.AiGateway;
import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import com.sunlc.dsp.admin.assistant.ai.ChatRequest;
import com.sunlc.dsp.admin.assistant.ai.StreamCancelledException;
import com.sunlc.dsp.admin.assistant.ai.StreamHandle;
import com.sunlc.dsp.admin.assistant.retrieval.RetrievalResult;
import com.sunlc.dsp.admin.assistant.retrieval.RetrievalService;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.AiChatMessage;
import com.sunlc.dsp.entity.AiChatSession;
import com.sunlc.dsp.service.AiChatMessageService;
import com.sunlc.dsp.service.AiChatSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AssistantChatService 编排单测。
 * 覆盖必改2（同步回调竞态）、必改3（失败vs取消状态）、建议改1（history过滤）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantChatServiceTest {

    @Mock private AiChatSessionService sessionService;
    @Mock private AiChatMessageService messageService;
    @Mock private AiGateway aiGateway;
    @Mock private RetrievalService retrievalService;
    @Mock private ChatConcurrencyLimiter concurrencyLimiter;

    private AssistantChatService service;

    @BeforeEach
    void setUp() {
        AssistantProperties props = new AssistantProperties();
        props.setSseTimeoutMs(120000L);
        service = new AssistantChatService(
                sessionService, messageService, aiGateway, retrievalService, concurrencyLimiter, props);
        lenient().when(concurrencyLimiter.tryAcquire(any())).thenReturn(true);
    }

    @Test
    void createSession_savesAndReturnsVO() {
        ChatSessionVO vo = service.createSession(1L, "alice", "测试会话");
        verify(sessionService, times(1)).save(any(AiChatSession.class));
        assertEquals("测试会话", vo.getTitle());
    }

    @Test
    void listOwnedMessages_sessionNotOwned_throws() {
        when(sessionService.getOwnedSession("s1", 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.listOwnedMessages("s1", 2L));
        verify(messageService, times(0)).listBySession(any());
    }

    @Test
    void listOwnedMessages_sessionOwned_returnsMessages() {
        when(sessionService.getOwnedSession("s1", 1L)).thenReturn(new AiChatSession());
        AiChatMessage m = newMessage("user", "hello", 1);
        when(messageService.listBySession("s1")).thenReturn(List.of(m));

        List<ChatMessageVO> result = service.listOwnedMessages("s1", 1L);
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getContent());
    }

    @Test
    void deleteSession_sessionOwned_removesSession() {
        AiChatSession session = new AiChatSession();
        session.setId(10L);
        when(sessionService.getOwnedSession("s1", 1L)).thenReturn(session);
        service.deleteSession("s1", 1L);
        verify(sessionService, times(1)).removeById(10L);
        verify(messageService, times(0)).removeById(anyLong());
    }

    @Test
    void deleteSession_sessionNotOwned_throws() {
        when(sessionService.getOwnedSession("s1", 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteSession("s1", 2L));
    }

    // ===== 必改2：streamChat 同步触发回调，终态正常 =====

    @Test
    void ask_syncCompleteBeforeReturn_messageFinalizedAndConcurrencyReleased() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        when(messageService.listBySession(any())).thenReturn(List.of());
        // streamChat 在返回前同步触发 onComplete（模拟 Flux.just 快速完成）
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            h.onDelta("答");          // 同步触发 delta
            h.onComplete();            // 同步触发完成
            return noopHandle();
        });

        service.ask(req("s1", "test"), 1L);

        // 消息更新（finalize 落 status=1）+ 并发 release
        verify(messageService, times(1)).updateById(any());
        verify(concurrencyLimiter, times(1)).release(1L);
    }

    @Test
    void ask_syncErrorBeforeReturn_messageFinalizedAsFailed() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        when(messageService.listBySession(any())).thenReturn(List.of());
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            h.onError(new RuntimeException("模型失败"));  // 同步触发失败
            return noopHandle();
        });

        service.ask(req("s1", "test"), 1L);

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageService, times(1)).updateById(captor.capture());
        // 必改3：RuntimeException → status=2 失败
        assertEquals(2, captor.getValue().getStatus(), "RuntimeException 应落 status=2 失败");
        verify(concurrencyLimiter, times(1)).release(1L);
    }

    // ===== 必改3：取消 vs 失败状态 =====

    @Test
    void ask_cancelException_statusCancelled() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        when(messageService.listBySession(any())).thenReturn(List.of());
        when(aiGateway.streamChat(any(), any())).thenAnswer(inv -> {
            com.sunlc.dsp.admin.assistant.ai.StreamHandler h = inv.getArgument(1);
            h.onError(new StreamCancelledException("取消"));
            return noopHandle();
        });

        service.ask(req("s1", "test"), 1L);

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageService, times(1)).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus(), "StreamCancelledException 应落 status=3 取消");
    }

    // ===== 检索接入 =====

    @Test
    void ask_projectRelated_passesRetrievalContext() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        RetrievalResult retrieval = new RetrievalResult(true, "【文档依据】xxx", "[{\"type\":\"doc\"}]", List.of());
        when(retrievalService.retrieve("什么是 DSL")).thenReturn(retrieval);
        when(messageService.listBySession(any())).thenReturn(List.of());
        ArgumentCaptor<ChatRequest> reqCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(aiGateway.streamChat(reqCaptor.capture(), any())).thenReturn(noopHandle());

        service.ask(req("s1", "什么是 DSL"), 1L);

        ChatRequest passed = reqCaptor.getValue();
        assertEquals("【文档依据】xxx", passed.getRetrievalContext());
    }

    @Test
    void ask_nonProjectRelated_noContext() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        when(messageService.listBySession(any())).thenReturn(List.of());
        ArgumentCaptor<ChatRequest> reqCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(aiGateway.streamChat(reqCaptor.capture(), any())).thenReturn(noopHandle());

        service.ask(req("s1", "今天天气"), 1L);

        ChatRequest passed = reqCaptor.getValue();
        assertEquals("", passed.getRetrievalContext());
        assertEquals("[]", passed.getCitations());
    }

    // ===== 并发 / 越权 / 入参校验 =====

    @Test
    void ask_concurrencyLimited_throws() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(concurrencyLimiter.tryAcquire(1L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.ask(req("s1", "test"), 1L));
    }

    @Test
    void ask_sessionNotOwned_throws() {
        when(sessionService.getOwnedSession("s1", 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.ask(req("s1", "x"), 2L));
    }

    @Test
    void ask_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> service.ask(null, 1L));
    }

    @Test
    void ask_blankQuestion_throws() {
        AskRequest r = req("s1", "  ");
        // validateAskRequest 在 getOwnedSession 之前执行，空 question 直接抛
        assertThrows(BusinessException.class, () -> service.ask(r, 1L));
    }

    @Test
    void ask_nullSessionId_throws() {
        AskRequest r = new AskRequest();
        r.setQuestion("hi");
        assertThrows(BusinessException.class, () -> service.ask(r, 1L));
    }

    // ===== 建议改1：mapHistory 只含 status=1 =====

    @Test
    void ask_historyOnlyIncludesCompletedMessages() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        // 历史含 生成中(0)/完成(1)/失败(2)/取消(3)，应只带完成的
        List<AiChatMessage> history = new ArrayList<>();
        history.add(newMessageWithId(1L, "user", "q1", 1));
        history.add(newMessageWithId(2L, "assistant", "生成中", 0));
        history.add(newMessageWithId(3L, "assistant", "失败", 2));
        history.add(newMessageWithId(4L, "assistant", "取消", 3));
        history.add(newMessageWithId(5L, "assistant", "ok", 1));
        when(messageService.listBySession(any())).thenReturn(history);
        ArgumentCaptor<ChatRequest> reqCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(aiGateway.streamChat(reqCaptor.capture(), any())).thenReturn(noopHandle());

        service.ask(req("s1", "新问题"), 1L);

        ChatRequest passed = reqCaptor.getValue();
        // 只有 status=1 且排除当前 user 消息（listBySession 返回里若含刚保存的也会被排除）
        assertEquals(2, passed.getHistory().size());
    }

    // ===== 必改1：当前问题不重复进入 history =====

    @Test
    void ask_currentQuestionNotDuplicatedInHistory() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        // listBySession 返回含刚保存的当前 user 消息（id=100，content="当前问题"）
        // 加上历史一条 assistant 完成
        List<AiChatMessage> dbMessages = new ArrayList<>();
        AiChatMessage current = newMessageWithId(100L, "user", "当前问题", 1);
        AiChatMessage prev = newMessageWithId(99L, "assistant", "上轮回答", 1);
        dbMessages.add(prev);
        dbMessages.add(current);
        when(messageService.listBySession(any())).thenReturn(dbMessages);
        // userMsg 保存时设 id=100（模拟 MyBatis 回填）
        when(messageService.save(any())).thenAnswer(inv -> {
            AiChatMessage m = inv.getArgument(0);
            if ("user".equals(m.getRole())) {
                m.setId(100L);
            } else {
                m.setId(101L);
            }
            return true;
        });
        ArgumentCaptor<ChatRequest> reqCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(aiGateway.streamChat(reqCaptor.capture(), any())).thenReturn(noopHandle());

        service.ask(req("s1", "当前问题"), 1L);

        ChatRequest passed = reqCaptor.getValue();
        // history 不应包含 id=100 的当前问题
        boolean currentInHistory = passed.getHistory().stream()
                .anyMatch(m -> "当前问题".equals(m.getContent()) && "user".equals(m.getRole()));
        assertEquals(false, currentInHistory, "当前问题不应重复进入 history");
        // 当前问题只通过 userMessage 传一次
        assertEquals("当前问题", passed.getUserMessage());
        // history 应含上一轮 assistant 回答
        assertEquals(1, passed.getHistory().size());
        assertEquals("上轮回答", passed.getHistory().get(0).getContent());
    }

    // ===== 必改2：streamChat 直接 throw 时落失败状态 + 释放并发 =====

    @Test
    void ask_streamChatThrows_finalizesAsFailedAndReleasesConcurrency() {
        when(sessionService.getOwnedSession(any(), eq(1L))).thenReturn(ownedSession());
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.notProjectRelated());
        when(messageService.listBySession(any())).thenReturn(List.of());
        // streamChat 直接抛异常（模拟网关实现 bug / 构造失败）
        when(aiGateway.streamChat(any(), any())).thenThrow(new RuntimeException("网关构造失败"));

        service.ask(req("s1", "test"), 1L);

        // assistant 消息应落 status=2（失败）
        ArgumentCaptor<AiChatMessage> msgCaptor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageService, times(1)).updateById(msgCaptor.capture());
        AiChatMessage updated = msgCaptor.getValue();
        assertEquals(2, updated.getStatus(), "streamChat throw 应落 status=2 失败");
        // 并发计数必须释放
        verify(concurrencyLimiter, times(1)).release(1L);
    }

    private AiChatMessage newMessage(String role, String content, int status) {
        return newMessageWithId(null, role, content, status);
    }

    private AiChatMessage newMessageWithId(Long id, String role, String content, int status) {
        AiChatMessage m = new AiChatMessage();
        m.setId(id);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        return m;
    }

    private AiChatSession ownedSession() {
        AiChatSession s = new AiChatSession();
        s.setSessionId("s1");
        s.setUserId(1L);
        return s;
    }

    private AskRequest req(String sessionId, String question) {
        AskRequest r = new AskRequest();
        r.setSessionId(sessionId);
        r.setQuestion(question);
        return r;
    }

    private static StreamHandle noopHandle() {
        return () -> { };
    }
}
