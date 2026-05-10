package com.fintechervision.dsp.adminservice.controller;

import com.fintechervision.dsp.common.model.ApiResponse;
import com.fintechervision.dsp.entity.AppAuth;
import com.fintechervision.dsp.enums.CommonStatus;
import com.fintechervision.dsp.service.AppAuthService;
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
        return ApiResponse.success("APP_LIST", "", appAuthService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AppAuth> detail(@PathVariable Long id) {
        return ApiResponse.success("APP_DETAIL", "", appAuthService.getById(id));
    }

    @PostMapping
    public ApiResponse<AppAuth> create(@RequestBody AppAuth appAuth) {
        appAuth.setStatus(CommonStatus.ENABLED.getCode());
        appAuth.setCreatedTime(LocalDateTime.now());
        appAuth.setUpdatedTime(LocalDateTime.now());
        if (appAuth.getAppSecret() == null || appAuth.getAppSecret().isEmpty()) {
            appAuth.setAppSecret(cn.hutool.core.util.RandomUtil.randomString(32));
        }
        appAuthService.save(appAuth);
        return ApiResponse.success("APP_CREATE", "", appAuth);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody AppAuth appAuth) {
        appAuth.setId(id);
        appAuth.setUpdatedTime(LocalDateTime.now());
        appAuthService.updateById(appAuth);
        return ApiResponse.success("APP_UPDATE", "", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        appAuthService.removeById(id);
        return ApiResponse.success("APP_DELETE", "", null);
    }

    @PostMapping("/{appId}/token")
    public ApiResponse<Map<String, Object>> generateToken(@PathVariable String appId) {
        try {
            Map<String, Object> result = appAuthService.generateToken(appId);
            return ApiResponse.success("APP_TOKEN", "", result);
        } catch (RuntimeException e) {
            return ApiResponse.error("APP_TOKEN", "", "4004", e.getMessage());
        }
    }
}
