package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员登录控制器
 */
@Slf4j
@RestController
@RequestMapping("/dsp/admin")
@RequiredArgsConstructor
public class LoginController {

    private final JwtUtil jwtUtil;

    @Value("${dsp.admin.username:admin}")
    private String adminUsername;

    @Value("${dsp.admin.password:admin123}")
    private String adminPassword;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> loginForm) {
        String username = loginForm.get("username");
        String password = loginForm.get("password");

        if (username == null || password == null) {
            return ApiResponse.error("LOGIN", "", "4001", "用户名和密码不能为空");
        }

        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            log.warn("管理端登录失败: username={}", username);
            return ApiResponse.error("LOGIN", "", "4003", "用户名或密码错误");
        }

        // 签发管理员 Token，allowedTransnos 设为 * 表示全部权限
        String token = jwtUtil.generateToken("admin:" + username, Collections.singletonList("*"));

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);

        log.info("管理端登录成功: username={}", username);
        return ApiResponse.success("LOGIN", "", result);
    }
}
