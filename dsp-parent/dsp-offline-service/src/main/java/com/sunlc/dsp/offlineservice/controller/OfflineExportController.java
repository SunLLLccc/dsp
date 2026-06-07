package com.sunlc.dsp.offlineservice.controller;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.ExportTask;
import com.sunlc.dsp.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
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

        // 校验 transno 白名单
        checkTransnoAllowed(transno, httpRequest);

        log.info("离线导出请求: transno={}, format={}, applyUser={}", transno, format, applyUser);
        ExportTask task = exportService.submitOfflineExport(transno, params, format, applyUser);
        return ApiResponse.success("OFFLINE_EXPORT", "", task);
    }

    @GetMapping("/{taskId}/progress")
    public ApiResponse<ExportTask> getProgress(@PathVariable Long taskId, HttpServletRequest httpRequest) {
        String appId = (String) httpRequest.getAttribute("appId");
        ExportTask task = exportService.getExportTask(taskId);
        checkTaskOwnership(task, appId);
        return ApiResponse.success("EXPORT_PROGRESS", "", task);
    }

    @GetMapping("/{taskId}/download")
    public void downloadFile(@PathVariable Long taskId, HttpServletRequest httpRequest, HttpServletResponse response) {
        String appId = (String) httpRequest.getAttribute("appId");
        ExportTask task = exportService.getExportTask(taskId);
        checkTaskOwnership(task, appId);
        log.info("下载导出文件: taskId={}, appId={}", taskId, appId);
        exportService.downloadExportFile(taskId, response);
    }

    /**
     * 校验 transno 是否在当前 token 的授权白名单中
     */
    @SuppressWarnings("unchecked")
    private void checkTransnoAllowed(String transno, HttpServletRequest request) {
        if (transno == null || transno.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少接口编号(transno)");
        }
        List<String> allowed = (List<String>) request.getAttribute("allowedTransnos");
        if (allowed == null || allowed.isEmpty()) {
            log.warn("导出请求无授权白名单: transno={}", transno);
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该接口");
        }
        if (!allowed.contains("*") && !allowed.contains(transno)) {
            log.warn("导出请求越权: transno={}, allowed={}", transno, allowed);
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权导出该接口数据");
        }
    }

    /**
     * 校验任务归属：只允许任务提交者查看/下载
     */
    private void checkTaskOwnership(ExportTask task, String appId) {
        if (task == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "导出任务不存在");
        }
        if (task.getApplyUser() == null || !task.getApplyUser().equals(appId)) {
            log.warn("导出任务归属校验失败: taskId={}, taskOwner={}, requestAppId={}",
                    task.getId(), task.getApplyUser(), appId);
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该导出任务");
        }
    }
}
