package com.fintechervision.dsp.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fintechervision.dsp.common.model.ApiResponse;
import com.fintechervision.dsp.entity.ExportTask;
import com.fintechervision.dsp.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 导出任务管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/dsp/admin/export")
@RequiredArgsConstructor
public class ExportAdminController {

    private final ExportService exportService;

    @GetMapping("/list")
    public ApiResponse<Page<ExportTask>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success("EXPORT_LIST", "",
                exportService.listExportTask(pageNum, pageSize, transno, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExportTask> detail(@PathVariable Long id) {
        return ApiResponse.success("EXPORT_DETAIL", "", exportService.getExportTask(id));
    }
}
