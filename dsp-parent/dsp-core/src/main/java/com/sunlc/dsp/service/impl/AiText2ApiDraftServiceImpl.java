package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.AiText2ApiDraft;
import com.sunlc.dsp.mapper.AiText2ApiDraftMapper;
import com.sunlc.dsp.service.AiText2ApiDraftService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Text2API 草稿服务实现。
 * 越权防护与 chat 一致：getOwnedDraft 返回 null 表示不存在/已删除/非本人。
 */
@Slf4j
@Service
public class AiText2ApiDraftServiceImpl extends ServiceImpl<AiText2ApiDraftMapper, AiText2ApiDraft>
        implements AiText2ApiDraftService {

    @Override
    public AiText2ApiDraft getOwnedDraft(String draftId, Long userId) {
        LambdaQueryWrapper<AiText2ApiDraft> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiText2ApiDraft::getDraftId, draftId)
                .eq(AiText2ApiDraft::getUserId, userId);
        // deleted 字段由 @TableLogic 自动过滤
        return getOne(wrapper);
    }
}
