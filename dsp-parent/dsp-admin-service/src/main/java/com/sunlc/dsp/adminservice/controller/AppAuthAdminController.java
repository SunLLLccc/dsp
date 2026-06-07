package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.AppAuth;
import com.sunlc.dsp.enums.CommonStatus;
import com.sunlc.dsp.service.AppAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/app")
@RequiredArgsConstructor
public class AppAuthAdminController {

    private final AppAuthService appAuthService;

    @GetMapping("/list")
    public ApiResponse<List<AppAuth>> list() {
        List<AppAuth> list = appAuthService.listAll();
        list.forEach(a -> a.setAppSecret(null));
        return ApiResponse.success("APP_LIST", "", list);
    }

    @GetMapping("/{id}")
    public ApiResponse<AppAuth> detail(@PathVariable Long id) {
        AppAuth appAuth = appAuthService.getById(id);
        if (appAuth != null) {
            appAuth.setAppSecret(null);
        }
        return ApiResponse.success("APP_DETAIL", "", appAuth);
    }

    @PostMapping
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<AppAuth> create(@RequestBody AppAuth appAuth) {
        // 必填字段校验
        if (appAuth.getAppId() == null || appAuth.getAppId().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "应用ID不能为空");
        }
        if (appAuth.getAppName() == null || appAuth.getAppName().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "应用名称不能为空");
        }

        appAuth.setStatus(CommonStatus.ENABLED.getCode());
        appAuth.setCreatedTime(LocalDateTime.now());
        appAuth.setUpdatedTime(LocalDateTime.now());
        if (appAuth.getAppSecret() == null || appAuth.getAppSecret().isEmpty()) {
            appAuth.setAppSecret(cn.hutool.core.util.RandomUtil.randomString(32));
        }
        appAuthService.save(appAuth);
        // 返回前脱敏，不暴露 appSecret
        appAuth.setAppSecret(null);
        return ApiResponse.success("APP_CREATE", "", appAuth);
    }

    @PutMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody AppAuth appAuth) {
        appAuth.setId(id);
        appAuth.setUpdatedTime(LocalDateTime.now());
        appAuthService.updateById(appAuth);
        return ApiResponse.success("APP_UPDATE", "", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        appAuthService.removeById(id);
        return ApiResponse.success("APP_DELETE", "", null);
    }

    @PostMapping("/{appId}/token")
    @RequireRole({"DEPT_MANAGER", "ADMIN"})
    public ApiResponse<Map<String, Object>> generateToken(@PathVariable String appId) {
        try {
            Map<String, Object> result = appAuthService.generateToken(appId);
            return ApiResponse.success("APP_TOKEN", "", result);
        } catch (RuntimeException e) {
            return ApiResponse.error("APP_TOKEN", "", "4004", e.getMessage());
        }
    }
}
