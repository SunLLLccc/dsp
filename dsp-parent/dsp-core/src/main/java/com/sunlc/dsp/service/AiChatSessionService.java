package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.AiChatSession;

/**
 * AI 智能助手会话服务
 */
public interface AiChatSessionService extends IService<AiChatSession> {

    /**
     * 根据业务会话 ID 查询属于指定用户的未删除会话。
     * 软删语义：仅校验 session 未删除且属于当前用户，删除后的会话返回 null。
     *
     * @param sessionId 业务会话 ID
     * @param userId    当前登录用户 ID
     * @return 会话实体；不存在/已删除/不属于该用户时返回 null
     */
    AiChatSession getOwnedSession(String sessionId, Long userId);
}
