package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.AiChatSession;
import com.sunlc.dsp.mapper.AiChatSessionMapper;
import com.sunlc.dsp.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 智能助手会话服务实现
 */
@Slf4j
@Service
public class AiChatSessionServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession>
        implements AiChatSessionService {

    @Override
    public AiChatSession getOwnedSession(String sessionId, Long userId) {
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getSessionId, sessionId)
                .eq(AiChatSession::getUserId, userId);
        // deleted 字段由 @TableLogic 自动过滤，无需手动加条件
        return getOne(wrapper);
    }
}
