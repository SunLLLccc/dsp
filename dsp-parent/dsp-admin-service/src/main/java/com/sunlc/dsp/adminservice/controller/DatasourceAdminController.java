package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.common.util.PasswordEncryptor;
import com.sunlc.dsp.entity.DatasourceConfig;
import com.sunlc.dsp.enums.CommonStatus;
import com.sunlc.dsp.service.DatasourceManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/datasource")
@RequiredArgsConstructor
public class DatasourceAdminController {

    private final DatasourceManagerService datasourceManagerService;
    private final PasswordEncryptor passwordEncryptor;

    @GetMapping("/list")
    public ApiResponse<Page<DatasourceConfig>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String dsName,
            @RequestParam(required = false) String dsType) {

        Page<DatasourceConfig> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<>();
        if (dsName != null && !dsName.isEmpty()) {
            wrapper.like(DatasourceConfig::getDsName, dsName);
        }
        if (dsType != null && !dsType.isEmpty()) {
            wrapper.eq(DatasourceConfig::getDsType, dsType);
        }
        wrapper.orderByDesc(DatasourceConfig::getCreatedTime);

        Page<DatasourceConfig> result = datasourceManagerService.page(page, wrapper);
        result.getRecords().forEach(c -> c.setPassword(null));
        return ApiResponse.success("DS_LIST", "", result);
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasourceConfig> detail(@PathVariable Long id) {
        DatasourceConfig config = datasourceManagerService.getById(id);
        if (config != null) {
            config.setPassword(null);
        }
        return ApiResponse.success("DS_DETAIL", "", config);
    }

    @PostMapping
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<DatasourceConfig> create(@RequestBody DatasourceConfig config) {
        // 必填字段校验
        if (config.getDsName() == null || config.getDsName().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据源名称不能为空");
        }
        if (config.getDsType() == null || config.getDsType().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据源类型不能为空");
        }
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JDBC连接地址不能为空");
        }
        if (config.getUsername() == null || config.getUsername().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据库用户名不能为空");
        }
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据库密码不能为空");
        }

        config.setPassword(passwordEncryptor.encrypt(config.getPassword()));
        config.setStatus(CommonStatus.ENABLED.getCode());
        config.setCreatedTime(LocalDateTime.now());
        config.setUpdatedTime(LocalDateTime.now());
        datasourceManagerService.save(config);

        try {
            datasourceManagerService.registerDatasource(config);
        } catch (Exception e) {
            log.warn("数据源注册失败，但配置已保存: dsName={}", config.getDsName(), e);
        }

        // 返回前脱敏，不暴露加密后的密码
        config.setPassword(null);
        return ApiResponse.success("DS_CREATE", "", config);
    }

    @PutMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody DatasourceConfig config) {
        config.setId(id);
        // 如果密码被修改（不是 ENC() 格式），则加密
        if (config.getPassword() != null && !config.getPassword().startsWith("ENC(")) {
            config.setPassword(passwordEncryptor.encrypt(config.getPassword()));
        }
        config.setUpdatedTime(LocalDateTime.now());
        datasourceManagerService.updateById(config);

        try {
            DatasourceConfig updated = datasourceManagerService.getById(id);
            datasourceManagerService.removeDatasource(updated.getDsName());
            datasourceManagerService.registerDatasource(updated);
        } catch (Exception e) {
            log.warn("数据源重新注册失败: id={}", id, e);
        }

        return ApiResponse.success("DS_UPDATE", "", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        DatasourceConfig config = datasourceManagerService.getById(id);
        if (config != null) {
            try {
                datasourceManagerService.removeDatasource(config.getDsName());
            } catch (Exception e) {
                log.warn("数据源注销失败: dsName={}", config.getDsName(), e);
            }
        }
        datasourceManagerService.removeById(id);
        return ApiResponse.success("DS_DELETE", "", null);
    }

    @PostMapping("/test")
    @RequireRole({"DEPT_MANAGER", "ADMIN"})
    public ApiResponse<String> testConnection(@RequestBody DatasourceConfig config) {
        DatasourceConfig toTest;
        if (config.getId() != null) {
            // 已保存数据源：直接使用数据库中的完整配置测试，不与请求体混用
            toTest = datasourceManagerService.getById(config.getId());
            if (toTest == null) {
                return ApiResponse.error("DS_TEST", "", "4004", "数据源配置不存在");
            }
        } else {
            // 未保存配置：使用请求体中的 dsType/jdbcUrl/username/password，校验必填字段
            if (config.getDsType() == null || config.getDsType().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "数据源类型不能为空");
            }
            if (config.getJdbcUrl() == null || config.getJdbcUrl().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "JDBC连接地址不能为空");
            }
            if (config.getUsername() == null || config.getUsername().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "数据库用户名不能为空");
            }
            if (config.getPassword() == null || config.getPassword().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "数据库密码不能为空");
            }
            toTest = config;
        }
        String result = datasourceManagerService.testConnection(toTest);
        if ("连接成功".equals(result)) {
            return ApiResponse.success("DS_TEST", "", result);
        } else {
            return ApiResponse.error("DS_TEST", "", "5002", result);
        }
    }
}
