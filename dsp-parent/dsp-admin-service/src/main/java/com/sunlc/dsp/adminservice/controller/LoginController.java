package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.common.util.JwtUtil;
import com.sunlc.dsp.entity.SysUser;
import com.sunlc.dsp.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin")
@RequiredArgsConstructor
public class LoginController {

    private final JwtUtil jwtUtil;
    private final SysUserService sysUserService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> loginForm) {
        String username = loginForm.get("username");
        String password = loginForm.get("password");

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名和密码不能为空");
        }

        SysUser user = sysUserService.login(username, password);
        if (user == null) {
            log.warn("管理端登录失败: username={}", username);
            return ApiResponse.error("LOGIN", "", "4003", "用户名或密码错误");
        }

        List<String> roles = sysUserService.getRoleCodes(user.getId());
        String token = jwtUtil.generateToken(
                "admin:" + username,
                Collections.singletonList("*"),
                user.getId(),
                roles,
                user.getDeptId()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("realName", user.getRealName());
        result.put("roles", roles);
        result.put("deptId", user.getDeptId());

        log.info("管理端登录成功: username={}", username);
        return ApiResponse.success("LOGIN", "", result);
    }
}
