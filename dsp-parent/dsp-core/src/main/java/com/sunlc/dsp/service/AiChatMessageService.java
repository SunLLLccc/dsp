package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.AiChatMessage;

import java.util.List;

/**
 * AI 智能助手消息服务。
 * 软删语义：业务流程不主动删除 ai_chat_message，消息保留用于审计；
 * 虽然 IService 继承了 remove / removeById / removeBatchByIds 等删除能力，但本服务不在业务流程中调用它们，
 * Controller / admin-service 也不直接暴露消息删除能力。
 * 后续 P4/P5 只能通过高层 AssistantChatService 编排消息查询，
 * 不允许对外暴露 AiChatMessageService 的 remove 系列删除能力（P4 增加代码审查规则：Controller 禁止直接注入本服务）。
 */
public interface AiChatMessageService extends IService<AiChatMessage> {

    /**
     * 按会话 ID 升序查询消息（用户视角的会话历史）。
     * <p>
     * 契约说明（防越权误用）：本方法<b>不校验会话归属</b>，直接按 session_id 查询。
     * 调用方不得是 Controller。P4 必须由高层 {@code AssistantChatService.listOwnedMessages(sessionId, userId)}
     * 组合入口兜住：内部先调用 {@link AiChatSessionService#getOwnedSession} 校验会话未删除且属于当前用户，
     * 通过后再调用本方法查消息。Controller 不直接调用本方法。
     *
     * @param sessionId 业务会话 ID
     * @return 该会话的消息列表（按创建时间升序）
     */
    List<AiChatMessage> listBySession(String sessionId);
}
