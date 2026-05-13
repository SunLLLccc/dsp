package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.ApprovalRecord;
import com.sunlc.dsp.service.ApprovalRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/approval")
@RequiredArgsConstructor
public class ApprovalApplicationController {

    private final ApprovalRecordService approvalRecordService;

    private String getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute("adminUser");
        return user != null ? user.toString() : "anonymous";
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("adminUserId");
        return userId != null ? Long.valueOf(userId.toString()) : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getCurrentRoles(HttpServletRequest request) {
        Object roles = request.getAttribute("adminRoles");
        return roles != null ? (List<String>) roles : java.util.Collections.emptyList();
    }

    private Long getCurrentDeptId(HttpServletRequest request) {
        Object deptId = request.getAttribute("adminDeptId");
        return deptId != null ? Long.valueOf(deptId.toString()) : null;
    }

    /**
     * 提交接口申请
     */
    @PostMapping("/application")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<ApprovalRecord> submitApplication(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {
        String applicant = getCurrentUser(request);
        Long deptId = getCurrentDeptId(request);
        ApprovalRecord record = approvalRecordService.submitApplication(params, applicant, deptId);
        return ApiResponse.success("APPLICATION_SUBMIT", "", record);
    }

    /**
     * 我的申请列表
     */
    @GetMapping("/my-applications")
    public ApiResponse<Page<ApprovalRecord>> myApplications(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        String applicant = getCurrentUser(request);
        return ApiResponse.success("MY_APPLICATIONS", "",
                approvalRecordService.myApplications(applicant, pageNum, pageSize));
    }

    /**
     * 待我审批列表
     */
    @GetMapping("/pending-approval")
    @RequireRole({"DEPT_MANAGER", "ADMIN"})
    public ApiResponse<Page<ApprovalRecord>> pendingApproval(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        String username = getCurrentUser(request);
        List<String> roles = getCurrentRoles(request);
        Long deptId = getCurrentDeptId(request);
        return ApiResponse.success("PENDING_APPROVAL", "",
                approvalRecordService.pendingApproval(username, roles, deptId, pageNum, pageSize));
    }

    /**
     * 已审批列表
     */
    @GetMapping("/approved-list")
    @RequireRole({"DEPT_MANAGER", "ADMIN"})
    public ApiResponse<Page<ApprovalRecord>> approvedList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        String username = getCurrentUser(request);
        return ApiResponse.success("APPROVED_LIST", "",
                approvalRecordService.approvedList(username, pageNum, pageSize));
    }

    /**
     * 审批通过
     */
    @PostMapping("/application/{id}/approve")
    @RequireRole({"DEPT_MANAGER", "ADMIN"})
    public ApiResponse<Void> approveApplication(
            @PathVariable Long id,
            HttpServletRequest request) {
        approvalRecordService.approveApplication(id, getCurrentUser(request));
        return ApiResponse.success("APPLICATION_APPROVE", "", null);
    }

    /**
     * 审批驳回
     */
    @PostMapping("/application/{id}/reject")
    @RequireRole({"DEPT_MANAGER", "ADMIN"})
    public ApiResponse<Void> rejectApplication(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        approvalRecordService.rejectApplication(id, getCurrentUser(request), body.get("reason"));
        return ApiResponse.success("APPLICATION_REJECT", "", null);
    }
}
