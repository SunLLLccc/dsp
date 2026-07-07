package com.sunlc.dsp.service;

import com.sunlc.dsp.entity.SysUser;
import com.sunlc.dsp.service.impl.SysUserServiceImpl;
import com.sunlc.dsp.mapper.SysRoleMapper;
import com.sunlc.dsp.mapper.SysUserMapper;
import com.sunlc.dsp.mapper.SysUserRoleMapper;
import com.sunlc.dsp.entity.SysRole;
import com.sunlc.dsp.entity.SysUserRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Mock(lenient = true) private SysUserMapper sysUserMapper;
    @Mock(lenient = true) private SysUserRoleMapper sysUserRoleMapper;
    @Mock(lenient = true) private SysRoleMapper sysRoleMapper;
    @Mock(lenient = true) private BCryptPasswordEncoder passwordEncoder;
    @Mock(lenient = true) private SysDeptService sysDeptService;

    private SysUserServiceImpl sysUserService;

    private SysUser testUser;

    @BeforeEach
    void setUp() throws Exception {
        sysUserService = Mockito.spy(new SysUserServiceImpl(sysUserRoleMapper, sysRoleMapper, passwordEncoder, sysDeptService));
        setField(sysUserService, SysUserServiceImpl.class.getSuperclass(), "baseMapper", sysUserMapper);

        testUser = new SysUser();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setPassword("$2b$10$hashedpassword");
        testUser.setRealName("管理员");
        testUser.setDeptId(1L);
        testUser.setStatus(1);
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

    @Test
    void login_success() {
        doReturn(testUser).when(sysUserService).getOne(any(LambdaQueryWrapper.class));
        when(passwordEncoder.matches("admin123", testUser.getPassword())).thenReturn(true);

        SysUser result = sysUserService.login("admin", "admin123");
        assertNotNull(result);
        assertEquals("admin", result.getUsername());
    }

    @Test
    void login_wrongPassword() {
        doReturn(testUser).when(sysUserService).getOne(any(LambdaQueryWrapper.class));
        when(passwordEncoder.matches("wrong", testUser.getPassword())).thenReturn(false);

        SysUser result = sysUserService.login("admin", "wrong");
        assertNull(result);
    }

    @Test
    void login_userNotFound() {
        doReturn(null).when(sysUserService).getOne(any(LambdaQueryWrapper.class));
        SysUser result = sysUserService.login("nobody", "pass");
        assertNull(result);
    }

    @Test
    void getRoleCodes_returnsRoleCodes() {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(1L);
        ur.setRoleId(1L);
        when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(ur));

        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("系统管理员");
        when(sysRoleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));

        List<String> codes = sysUserService.getRoleCodes(1L);
        assertEquals(1, codes.size());
        assertEquals("ADMIN", codes.get(0));
    }

    @Test
    void getRoleCodes_emptyWhenNoRoles() {
        when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        List<String> codes = sysUserService.getRoleCodes(1L);
        assertTrue(codes.isEmpty());
    }

    @Test
    void createUser_encryptsPassword() {
        SysUser newUser = new SysUser();
        newUser.setUsername("testuser");
        newUser.setPassword("plainpwd");
        when(passwordEncoder.encode("plainpwd")).thenReturn("$2b$10$encrypted");
        doReturn(true).when(sysUserService).save(any(SysUser.class));

        sysUserService.createUser(newUser);
        verify(passwordEncoder).encode("plainpwd");
        assertEquals("$2b$10$encrypted", newUser.getPassword());
    }
}
