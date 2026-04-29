package com.fintechervision.dsp.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fintechervision.dsp.common.model.ApiResponse;
import com.fintechervision.dsp.entity.AuditLog;
import com.fintechervision.dsp.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/dsp/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/list")
    public ApiResponse<Page<AuditLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String appId) {
        return ApiResponse.success("AUDIT_LIST", "",
                auditLogService.listAuditLog(pageNum, pageSize, transno, operation, appId));
    }
}
