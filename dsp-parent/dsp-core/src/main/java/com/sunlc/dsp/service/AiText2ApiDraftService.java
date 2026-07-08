package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.AiText2ApiDraft;

/**
 * Text2API 草稿服务。
 * 越权防护：getOwnedDraft 同时校验未删除 + 属于当前用户（与 chat 的 getOwnedSession 一致）。
 */
public interface AiText2ApiDraftService extends IService<AiText2ApiDraft> {

    /**
     * 根据业务草稿 ID 查询属于指定用户的未删除草稿。
     * 不存在/已删除/不属于该用户时返回 null（统一不泄露存在性）。
     *
     * @param draftId 业务草稿 ID
     * @param userId  当前登录用户 ID
     * @return 草稿实体；不存在/已删除/非本人时返回 null
     */
    AiText2ApiDraft getOwnedDraft(String draftId, Long userId);
}
