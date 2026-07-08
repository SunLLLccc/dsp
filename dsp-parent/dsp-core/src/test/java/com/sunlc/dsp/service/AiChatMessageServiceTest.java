package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sunlc.dsp.entity.AiChatMessage;
import com.sunlc.dsp.mapper.AiChatMessageMapper;
import com.sunlc.dsp.service.impl.AiChatMessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * 覆盖消息查询语义：listBySession 返回该会话的消息列表（@TableLogic 自动过滤已删除）。
 * 软删语义：业务流程不主动删除消息，保留用于审计（见 design.md 第 4 节）。
 */
@ExtendWith(MockitoExtension.class)
class AiChatMessageServiceTest {

    private AiChatMessageServiceImpl messageService;

    @BeforeEach
    void setUp() throws Exception {
        AiChatMessageMapper mapper = Mockito.mock(AiChatMessageMapper.class);
        messageService = Mockito.spy(new AiChatMessageServiceImpl());
        setField(messageService, messageService.getClass().getSuperclass(), "baseMapper", mapper);
    }

    @Test
    void listBySession_returnsMessages() {
        AiChatMessage m1 = new AiChatMessage();
        m1.setSessionId("s1");
        m1.setRole("user");
        AiChatMessage m2 = new AiChatMessage();
        m2.setSessionId("s1");
        m2.setRole("assistant");
        doReturn(Arrays.asList(m1, m2)).when(messageService).list(any(Wrapper.class));

        List<AiChatMessage> result = messageService.listBySession("s1");
        assertEquals(2, result.size());
    }

    @Test
    void listBySession_emptyWhenNoMessages() {
        doReturn(Collections.emptyList()).when(messageService).list(any(Wrapper.class));
        List<AiChatMessage> result = messageService.listBySession("s1");
        assertTrue(result.isEmpty());
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
