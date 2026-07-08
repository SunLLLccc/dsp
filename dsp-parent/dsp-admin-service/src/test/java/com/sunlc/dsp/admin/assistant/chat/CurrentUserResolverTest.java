package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * adminUserId 稳健转换单测。
 */
class CurrentUserResolverTest {

    @Test
    void resolveUserId_longValue() {
        HttpServletRequest req = mockWithAttr(CurrentUserResolver.ATTR_ADMIN_USER_ID, 100L);
        assertEquals(100L, CurrentUserResolver.requireUserId(req));
    }

    @Test
    void resolveUserId_integerValue() {
        HttpServletRequest req = mockWithAttr(CurrentUserResolver.ATTR_ADMIN_USER_ID, 200);
        assertEquals(200L, CurrentUserResolver.requireUserId(req));
    }

    @Test
    void resolveUserId_stringValue() {
        HttpServletRequest req = mockWithAttr(CurrentUserResolver.ATTR_ADMIN_USER_ID, "300");
        assertEquals(300L, CurrentUserResolver.requireUserId(req));
    }

    @Test
    void resolveUserId_nullThrowsBusinessException() {
        HttpServletRequest req = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> CurrentUserResolver.requireUserId(req),
                "adminUserId 缺失应抛 BusinessException");
    }

    @Test
    void resolveUserId_invalidFormatThrowsBusinessException() {
        HttpServletRequest req = mockWithAttr(CurrentUserResolver.ATTR_ADMIN_USER_ID, "abc");
        assertThrows(BusinessException.class, () -> CurrentUserResolver.requireUserId(req),
                "格式非法应抛 BusinessException（ACCESS_DENIED）");
    }

    @Test
    void resolveUserName() {
        HttpServletRequest req = mockWithAttr(CurrentUserResolver.ATTR_ADMIN_USER, "admin:alice");
        assertEquals("admin:alice", CurrentUserResolver.resolveUserName(req));
    }

    @Test
    void resolveUserName_null() {
        HttpServletRequest req = new MockHttpServletRequest();
        assertNull(CurrentUserResolver.resolveUserName(req));
    }

    @Test
    void toLongOrNull_valid() {
        assertEquals(42L, CurrentUserResolver.toLongOrNull("42"));
    }

    @Test
    void toLongOrNull_invalid() {
        assertNull(CurrentUserResolver.toLongOrNull("xyz"));
    }

    @Test
    void toLongOrNull_null() {
        assertNull(CurrentUserResolver.toLongOrNull(null));
    }

    private HttpServletRequest mockWithAttr(String name, Object value) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(name, value);
        return req;
    }
}
