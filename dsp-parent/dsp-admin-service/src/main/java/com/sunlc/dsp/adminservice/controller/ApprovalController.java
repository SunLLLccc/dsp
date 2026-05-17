package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.ApprovalFlow;
import com.sunlc.dsp.entity.ApprovalInfo;
import com.sunlc.dsp.service.ApprovalInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalInfoService approvalInfoService;

    private String getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute("adminUser");
        return user != null ? user.toString() : "anonymous";
    }

    private String getCurrentUserName(HttpServletRequest request) {
        Object name = request.getAttribute("adminRealName");
        return name != null ? name.toString() : "";
    }

    private Long getCurrentDeptId(HttpServletRequest request) {
        Object deptId = request.getAttribute("adminDeptId");
        if (deptId instanceof Long) return (Long) deptId;
        if (deptId instanceof Number) return ((Number) deptId).longValue();
        if (deptId instanceof String) {
            try { return Long.parseLong((String) deptId); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getCurrentRoles(HttpServletRequest request) {
        Object roles = request.getAttribute("adminRoles");
        return roles instanceof List ? (List<String>) roles : java.util.Collections.emptyList();
    }

    @PostMapping("/submit")
    public ApiResponse<ApprovalInfo> submit(@RequestBody Map<String, Object> params,
                                             HttpServletRequest request) {
        String applicant = getCurrentUser(request);
        params.put("applicantName", getCurrentUserName(request));
        params.put("applicantDeptId", getCurrentDeptId(request));
        Integer type = params.get("type") instanceof Number
                ? ((Number) params.get("type")).intValue() : null;
        if (type == null) {
            return ApiResponse.error("APPROVAL", "SUBMIT", "4100", "缺少审批类型");
        }
        ApprovalInfo info = approvalInfoService.submit(type, params, applicant);
        return ApiResponse.success("APPROVAL", "SUBMIT", info);
    }

    @GetMapping("/my-submissions")
    public ApiResponse<Object> mySubmissions(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        String applicant = getCurrentUser(request);
        return ApiResponse.success("APPROVAL", "MY_SUBMISSIONS",
                approvalInfoService.mySubmissions(applicant, type, status, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/pending")
    public ApiResponse<Object> pending(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long deptId = getCurrentDeptId(request);
        List<String> roles = getCurrentRoles(request);
        return ApiResponse.success("APPROVAL", "PENDING",
                approvalInfoService.pendingApproval(deptId, roles, pageNum, pageSize));
    }

    @GetMapping("/history")
    public ApiResponse<Object> history(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long deptId = getCurrentDeptId(request);
        List<String> roles = getCurrentRoles(request);
        return ApiResponse.success("APPROVAL", "HISTORY",
                approvalInfoService.approvedHistory(deptId, roles, startDate, endDate, pageNum, pageSize));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        String approver = getCurrentUser(request);
        String approverName = getCurrentUserName(request);
        approvalInfoService.approve(id, approver, approverName);
        return ApiResponse.success("APPROVAL", "APPROVE", null);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        String approver = getCurrentUser(request);
        String approverName = getCurrentUserName(request);
        approvalInfoService.reject(id, approver, approverName, body.get("reason"));
        return ApiResponse.success("APPROVAL", "REJECT", null);
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable Long id, HttpServletRequest request) {
        String applicant = getCurrentUser(request);
        approvalInfoService.withdraw(id, applicant);
        return ApiResponse.success("APPROVAL", "WITHDRAW", null);
    }

    @GetMapping("/{id}/flow")
    public ApiResponse<List<ApprovalFlow>> flowDetail(@PathVariable Long id) {
        return ApiResponse.success("APPROVAL", "FLOW", approvalInfoService.getFlowDetail(id));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<ApprovalInfo> detail(@PathVariable Long id) {
        return ApiResponse.success("APPROVAL", "DETAIL", approvalInfoService.getDetail(id));
    }
}
