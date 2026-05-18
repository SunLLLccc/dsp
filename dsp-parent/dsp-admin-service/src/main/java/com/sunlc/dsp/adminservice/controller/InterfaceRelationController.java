package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.InterfaceRelation;
import com.sunlc.dsp.service.InterfaceRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/relation")
@RequiredArgsConstructor
public class InterfaceRelationController {

    private final InterfaceRelationService interfaceRelationService;

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
    private boolean isAdmin(HttpServletRequest request) {
        Object roles = request.getAttribute("adminRoles");
        return roles instanceof List && ((List<String>) roles).contains("ADMIN");
    }

    @GetMapping("/provider")
    public ApiResponse<Object> provider(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) Long providerSystemId,
            @RequestParam(required = false) Long applicantSystemId,
            @RequestParam(required = false) String requirementNo,
            HttpServletRequest request) {
        Long deptId = getCurrentDeptId(request);
        return ApiResponse.success("RELATION", "PROVIDER",
                interfaceRelationService.getByProvider(deptId, isAdmin(request), transno, providerSystemId, applicantSystemId, requirementNo, pageNum, pageSize));
    }

    @GetMapping("/applicant")
    public ApiResponse<Object> applicant(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) Long providerSystemId,
            @RequestParam(required = false) Long applicantSystemId,
            @RequestParam(required = false) String requirementNo,
            HttpServletRequest request) {
        Long deptId = getCurrentDeptId(request);
        return ApiResponse.success("RELATION", "APPLICANT",
                interfaceRelationService.getByApplicant(deptId, isAdmin(request), transno, providerSystemId, applicantSystemId, requirementNo, pageNum, pageSize));
    }

    @GetMapping("/applicants-by-transno")
    public ApiResponse<List<InterfaceRelation>> applicantsByTransno(@RequestParam String transno) {
        return ApiResponse.success("RELATION", "APPLICANTS",
                interfaceRelationService.getApplicantsByTransno(transno));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id, @RequestBody Map<String, String> body,
                                      HttpServletRequest request) {
        String operator = request.getAttribute("adminUser") != null
                ? request.getAttribute("adminUser").toString() : "anonymous";
        interfaceRelationService.offline(id, body.get("reason"), operator);
        return ApiResponse.success("RELATION", "OFFLINE", null);
    }
}
