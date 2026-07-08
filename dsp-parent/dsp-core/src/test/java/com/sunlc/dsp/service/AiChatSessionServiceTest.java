package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sunlc.dsp.entity.AiChatSession;
import com.sunlc.dsp.mapper.AiChatSessionMapper;
import com.sunlc.dsp.service.impl.AiChatSessionServiceImpl;
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
 * 覆盖定死的软删语义：getOwnedSession 只返回属于当前用户的会话。
 */
@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceTest {

    private AiChatSessionServiceImpl sessionService;

    @BeforeEach
    void setUp() throws Exception {
        AiChatSessionMapper mapper = Mockito.mock(AiChatSessionMapper.class);
        sessionService = Mockito.spy(new AiChatSessionServiceImpl());
        setField(sessionService, sessionService.getClass().getSuperclass(), "baseMapper", mapper);
    }

    @Test
    void getOwnedSession_returnsSessionWhenOwned() {
        AiChatSession session = new AiChatSession();
        session.setSessionId("s1");
        session.setUserId(1L);
        // getOne 是 ServiceImpl 继承方法，通过 spy mock
        doReturn(session).when(sessionService).getOne(any(Wrapper.class));

        AiChatSession result = sessionService.getOwnedSession("s1", 1L);
        assertNotNull(result);
    }

    @Test
    void getOwnedSession_returnsNullWhenNotOwnedOrDeleted() {
        // 模拟会话不属于该用户或已删除：getOne 返回 null（@TableLogic 已过滤已删除）
        doReturn(null).when(sessionService).getOne(any(Wrapper.class));

        AiChatSession result = sessionService.getOwnedSession("s1", 2L);
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
