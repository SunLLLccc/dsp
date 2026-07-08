package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.AiChatMessage;
import com.sunlc.dsp.mapper.AiChatMessageMapper;
import com.sunlc.dsp.service.AiChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 智能助手消息服务实现。
 * 软删语义：业务流程不主动删除消息（见接口注释），保留用于审计。
 * 虽然 ServiceImpl 继承了 remove 系列删除能力，但本实现不在业务流程中调用，
 * 也不对外暴露；消息删除能力的隔离由 P4 编排层 + 代码审查规则保证。
 */
@Slf4j
@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage>
        implements AiChatMessageService {

    @Override
    public List<AiChatMessage> listBySession(String sessionId) {
        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreatedTime);
        // deleted 字段由 @TableLogic 自动过滤
        return list(wrapper);
    }
}
