package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.engine.XmlEngine;
import com.sunlc.dsp.enums.InterfaceStatus;
import com.sunlc.dsp.entity.ApprovalRecord;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.InterfaceVersion;
import com.sunlc.dsp.service.ApprovalRecordService;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/interface")
@RequiredArgsConstructor
public class InterfaceAdminController {

    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final ApprovalRecordService approvalRecordService;
    private final XmlEngine xmlEngine;

    private String getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute("adminUser");
        return user != null ? user.toString() : "anonymous";
    }

    @GetMapping("/list")
    public ApiResponse<Page<InterfaceInfo>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {

        String currentUser = getCurrentUser(request);
        Page<InterfaceInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
        if (transno != null && !transno.isEmpty()) {
            wrapper.like(InterfaceInfo::getTransno, transno);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like(InterfaceInfo::getName, name);
        }
        if (status != null) {
            wrapper.eq(InterfaceInfo::getStatus, status);
        }
        // 草稿只对创建人可见：status != 0 OR created_by = currentUser
        wrapper.and(w -> w.ne(InterfaceInfo::getStatus, InterfaceStatus.DRAFT.getCode())
                .or(sub -> sub.eq(InterfaceInfo::getStatus, InterfaceStatus.DRAFT.getCode())
                        .eq(InterfaceInfo::getCreatedBy, currentUser)));
        wrapper.orderByDesc(InterfaceInfo::getUpdatedTime);

        return ApiResponse.success("INTERFACE_LIST", "", interfaceInfoService.page(page, wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterfaceInfo> detail(@PathVariable Long id) {
        return ApiResponse.success("INTERFACE_DETAIL", "", interfaceInfoService.getById(id));
    }

    @PostMapping
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<InterfaceInfo> create(@RequestBody InterfaceInfo info, HttpServletRequest request) {
        info.setStatus(InterfaceStatus.DRAFT.getCode());
        info.setCurrentVersion(0);
        info.setCreatedBy(getCurrentUser(request));
        info.setCreatedTime(LocalDateTime.now());
        info.setUpdatedTime(LocalDateTime.now());
        interfaceInfoService.save(info);
        return ApiResponse.success("INTERFACE_CREATE", "", info);
    }

    @PutMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody InterfaceInfo info) {
        info.setId(id);
        info.setUpdatedTime(LocalDateTime.now());
        interfaceInfoService.updateById(info);
        return ApiResponse.success("INTERFACE_UPDATE", "", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interfaceInfoService.removeById(id);
        return ApiResponse.success("INTERFACE_DELETE", "", null);
    }

    @PostMapping("/{transno}/version")
    public ApiResponse<InterfaceVersion> saveSchema(
            @PathVariable String transno,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String operator = body.getOrDefault("operator", getCurrentUser(request));
        InterfaceVersion version = interfaceVersionService.saveSchema(
                transno, body.get("inputSchema"), body.get("outputSchema"),
                body.get("changeLog"), operator);
        return ApiResponse.success("VERSION_SAVE", "", version);
    }

    @GetMapping("/{transno}/versions")
    public ApiResponse<Page<InterfaceVersion>> versionList(
            @PathVariable String transno,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success("VERSION_LIST", "",
                interfaceVersionService.versionList(transno, pageNum, pageSize));
    }

    @GetMapping("/{transno}/version/{versionNo}")
    public ApiResponse<InterfaceVersion> getVersion(
            @PathVariable String transno,
            @PathVariable Integer versionNo) {
        return ApiResponse.success("VERSION_DETAIL", "",
                interfaceVersionService.getVersion(transno, versionNo));
    }

    @GetMapping("/{transno}/version/latest")
    public ApiResponse<InterfaceVersion> getLatestVisibleVersion(
            @PathVariable String transno,
            HttpServletRequest request) {
        return ApiResponse.success("VERSION_LATEST", "",
                interfaceVersionService.getLatestVisibleVersion(transno, getCurrentUser(request)));
    }

    @PostMapping("/{transno}/version/{versionNo}/submit")
    public ApiResponse<Void> submitApproval(
            @PathVariable String transno,
            @PathVariable Integer versionNo,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String operator = body.getOrDefault("operator", getCurrentUser(request));
        interfaceVersionService.submitApproval(transno, versionNo, operator);
        return ApiResponse.success("APPROVAL_SUBMIT", "", null);
    }

    @PostMapping("/{transno}/approve")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> approveAndPublish(
            @PathVariable String transno,
            HttpServletRequest request) {
        interfaceVersionService.approveAndPublish(transno, getCurrentUser(request));
        return ApiResponse.success("APPROVAL_PASS", "", null);
    }

    @PostMapping("/{transno}/reject")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> rejectApproval(
            @PathVariable String transno,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        interfaceVersionService.rejectApproval(transno, body.get("reason"), getCurrentUser(request));
        return ApiResponse.success("APPROVAL_REJECT", "", null);
    }

    @PostMapping("/{transno}/offline")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> offline(@PathVariable String transno, HttpServletRequest request) {
        interfaceVersionService.offline(transno, getCurrentUser(request));
        return ApiResponse.success("INTERFACE_OFFLINE", "", null);
    }

    @PostMapping("/{transno}/withdraw")
    public ApiResponse<Void> withdrawApproval(@PathVariable String transno, HttpServletRequest request) {
        interfaceVersionService.withdrawApproval(transno, getCurrentUser(request));
        return ApiResponse.success("APPROVAL_WITHDRAW", "", null);
    }

    @PostMapping("/debug")
    public ApiResponse<Object> debug(@RequestBody Map<String, Object> body) {
        String transno = (String) body.get("transno");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");

        try {
            String xmlConfig = interfaceInfoService.getActiveXmlConfig(transno);
            Object result = xmlEngine.execute(xmlConfig, params);
            return ApiResponse.success("DEBUG", "", result);
        } catch (Exception e) {
            return ApiResponse.error("DEBUG", "", "5001", "调试失败: " + e.getMessage());
        }
    }

    // ==================== 审批记录接口 ====================

    @GetMapping("/{transno}/approval-records")
    public ApiResponse<Page<ApprovalRecord>> approvalRecords(
            @PathVariable String transno,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success("APPROVAL_RECORDS", "",
                approvalRecordService.listByTransno(transno, pageNum, pageSize));
    }

    @GetMapping("/approval-pending")
    public ApiResponse<Page<ApprovalRecord>> approvalPending(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success("APPROVAL_PENDING", "",
                approvalRecordService.listPending(pageNum, pageSize));
    }

}
