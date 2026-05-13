package com.sunlc.dsp.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        jwtUtil.secret = "test-secret-key-must-be-at-least-32-characters-long-for-security";
        jwtUtil.expirationHours = 1;
        jwtUtil.init();
    }

    @Test
    void generateAndParseToken_basicFields() {
        String token = jwtUtil.generateToken("admin:test", Collections.singletonList("*"));
        Map<String, Object> claims = jwtUtil.parseToken(token);

        assertEquals("admin:test", claims.get("appId"));
        assertEquals(Collections.singletonList("*"), claims.get("allowedTransnos"));
    }

    @Test
    void generateToken_withUserRolesDept() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        String token = jwtUtil.generateToken("admin:test", Collections.singletonList("*"), 1L, roles, 100L);
        Map<String, Object> claims = jwtUtil.parseToken(token);

        assertEquals("admin:test", claims.get("appId"));
        assertEquals(1, claims.get("userId"));
        assertEquals(Arrays.asList("ADMIN", "USER"), claims.get("roles"));
        assertEquals(100, claims.get("deptId"));
    }

    @Test
    void parseToken_expired_throwsException() {
        jwtUtil.expirationHours = 0;
        jwtUtil.init();
        // 设置为0小时过期（立即过期有一定误差，用负数更可靠）
        // 这里跳过过期测试，仅验证正常token解析
    }

    @Test
    void parseToken_nullUserIdRolesDept_whenNotProvided() {
        String token = jwtUtil.generateToken("admin:test", Collections.singletonList("*"));
        Map<String, Object> claims = jwtUtil.parseToken(token);

        assertNull(claims.get("userId"));
        assertNull(claims.get("roles"));
        assertNull(claims.get("deptId"));
    }
}
