package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.ExportTask;
import com.sunlc.dsp.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

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

    @GetMapping("/{taskId}/download")
    public void downloadFile(@PathVariable Long taskId, HttpServletResponse response) {
        exportService.downloadExportFile(taskId, response);
    }
}
