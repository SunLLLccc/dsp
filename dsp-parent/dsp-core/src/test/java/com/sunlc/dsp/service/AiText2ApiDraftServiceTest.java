package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sunlc.dsp.entity.AiText2ApiDraft;
import com.sunlc.dsp.mapper.AiText2ApiDraftMapper;
import com.sunlc.dsp.service.impl.AiText2ApiDraftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Text2API 草稿服务单测。覆盖 getOwnedDraft 越权防护。
 */
@ExtendWith(MockitoExtension.class)
class AiText2ApiDraftServiceTest {

    private AiText2ApiDraftServiceImpl draftService;

    @BeforeEach
    void setUp() throws Exception {
        AiText2ApiDraftMapper mapper = Mockito.mock(AiText2ApiDraftMapper.class);
        draftService = Mockito.spy(new AiText2ApiDraftServiceImpl());
        setField(draftService, draftService.getClass().getSuperclass(), "baseMapper", mapper);
    }

    @Test
    void getOwnedDraft_returnsDraftWhenOwned() {
        AiText2ApiDraft draft = new AiText2ApiDraft();
        draft.setDraftId("d1");
        draft.setUserId(1L);
        doReturn(draft).when(draftService).getOne(any(Wrapper.class));

        AiText2ApiDraft result = draftService.getOwnedDraft("d1", 1L);
        assertNotNull(result);
    }

    @Test
    void getOwnedDraft_returnsNullWhenNotOwnedOrDeleted() {
        // 不存在/已删除/非本人 → getOne 返回 null（@TableLogic 已过滤已删除）
        doReturn(null).when(draftService).getOne(any(Wrapper.class));

        AiText2ApiDraft result = draftService.getOwnedDraft("d1", 2L);
        assertNull(result);
    }

    private void setField(Object target, Class<?> clazz, String fieldName, Object value) throws Exception {
        Field f = null;
        Class<?> current = clazz;
        while (current != null && f == null) {
            try {
                f = current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        if (f == null) {
            throw new NoSuchFieldException(fieldName);
        }
        f.setAccessible(true);
        f.set(target, value);
    }
}
