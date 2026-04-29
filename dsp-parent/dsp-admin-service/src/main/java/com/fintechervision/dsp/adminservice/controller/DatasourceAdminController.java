package com.fintechervision.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fintechervision.dsp.common.model.ApiResponse;
import com.fintechervision.dsp.entity.DatasourceConfig;
import com.fintechervision.dsp.service.DatasourceManagerService;
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

        return ApiResponse.success("DS_LIST", "", datasourceManagerService.page(page, wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasourceConfig> detail(@PathVariable Long id) {
        return ApiResponse.success("DS_DETAIL", "", datasourceManagerService.getById(id));
    }

    @PostMapping
    public ApiResponse<DatasourceConfig> create(@RequestBody DatasourceConfig config) {
        config.setStatus(1);
        config.setCreatedTime(LocalDateTime.now());
        config.setUpdatedTime(LocalDateTime.now());
        datasourceManagerService.save(config);

        try {
            datasourceManagerService.registerDatasource(config);
        } catch (Exception e) {
            log.warn("数据源注册失败，但配置已保存: dsName={}", config.getDsName(), e);
        }

        return ApiResponse.success("DS_CREATE", "", config);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody DatasourceConfig config) {
        config.setId(id);
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
    public ApiResponse<String> testConnection(@RequestBody DatasourceConfig config) {
        try {
            datasourceManagerService.registerDatasource(config);
            datasourceManagerService.removeDatasource(config.getDsName());
            return ApiResponse.success("DS_TEST", "", "连接成功");
        } catch (Exception e) {
            return ApiResponse.error("DS_TEST", "", "5002", "连接失败: " + e.getMessage());
        }
    }
}
