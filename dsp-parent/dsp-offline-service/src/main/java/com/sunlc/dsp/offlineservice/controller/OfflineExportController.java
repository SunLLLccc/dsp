package com.sunlc.dsp.offlineservice.controller;

import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.ExportTask;
import com.sunlc.dsp.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/offline/export")
@RequiredArgsConstructor
public class OfflineExportController {

    private final ExportService exportService;

    @PostMapping
    public ApiResponse<ExportTask> submitOfflineExport(@RequestBody Map<String, Object> requestBody,
                                                        HttpServletRequest httpRequest) {
        String transno = (String) requestBody.get("transno");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) requestBody.get("params");
        String format = (String) requestBody.getOrDefault("format", "xlsx");
        String applyUser = (String) httpRequest.getAttribute("appId");

        log.info("离线导出请求: transno={}, format={}", transno, format);
        ExportTask task = exportService.submitOfflineExport(transno, params, format, applyUser);
        return ApiResponse.success("OFFLINE_EXPORT", "", task);
    }

    @GetMapping("/{taskId}/progress")
    public ApiResponse<ExportTask> getProgress(@PathVariable Long taskId) {
        ExportTask task = exportService.getExportTask(taskId);
        return ApiResponse.success("EXPORT_PROGRESS", "", task);
    }

    @GetMapping("/{taskId}/download")
    public void downloadFile(@PathVariable Long taskId, HttpServletResponse response) {
        log.info("下载导出文件: taskId={}", taskId);
        exportService.downloadExportFile(taskId, response);
    }
}
