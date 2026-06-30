package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.engine.DebugContext;
import com.sunlc.dsp.engine.XmlEngine;
import com.sunlc.dsp.engine.model.DebugTrace;
import com.sunlc.dsp.entity.ApprovalFlow;
import com.sunlc.dsp.entity.ApprovalInfo;
import com.sunlc.dsp.enums.InterfaceStatus;
import com.sunlc.dsp.entity.ApprovalRecord;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.SysSystem;
import com.sunlc.dsp.entity.InterfaceVersion;
import com.sunlc.dsp.service.ApprovalInfoService;
import com.sunlc.dsp.service.ApprovalRecordService;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceVersionService;
import com.sunlc.dsp.service.SysSystemService;
import com.sunlc.dsp.engine.validator.SqlSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/interface")
@RequiredArgsConstructor
public class InterfaceAdminController {

    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final ApprovalRecordService approvalRecordService;
    private final XmlEngine xmlEngine;
    private final SysSystemService sysSystemService;
    private final SqlSecurityValidator sqlSecurityValidator;

    @Lazy
    @Autowired
    private ApprovalInfoService approvalInfoService;

    private String getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute("adminUser");
        return user != null ? user.toString() : "anonymous";
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
        return roles instanceof List ? (List<String>) roles : Collections.emptyList();
    }

    /**
     * 通过 transno 查找待审批的 ApprovalInfo 记录 ID
     */
    private Long findPendingApprovalId(String transno) {
        LambdaQueryWrapper<ApprovalInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalInfo::getTransno, transno)
               .eq(ApprovalInfo::getStatus, 0)
               .orderByDesc(ApprovalInfo::getCreatedTime)
               .last("LIMIT 1");
        ApprovalInfo info = approvalInfoService.getOne(wrapper);
        if (info == null) {
            throw new RuntimeException("未找到接口 [" + transno + "] 的待审批记录");
        }
        return info.getId();
    }

    /**
     * 审批发布前校验待发布版本的 SQL 只读安全性
     */
    private void validatePendingVersionSchema(Long approvalId) {
        com.sunlc.dsp.entity.ApprovalInfo approvalInfo = approvalInfoService.getById(approvalId);
        if (approvalInfo == null) return;
        Integer type = approvalInfo.getType();
        if (type == null || (type != 1 && type != 2)) return;
        String transno = approvalInfo.getTransno();
        Integer versionNo = approvalInfo.getVersionNo();
        if (transno == null || versionNo == null) return;
        InterfaceVersion version = interfaceVersionService.getVersion(transno, versionNo);
        if (version != null && version.getInputSchema() != null && !version.getInputSchema().isEmpty()) {
            sqlSecurityValidator.validateXmlConfig(version.getInputSchema());
        }
    }

    private void fillSystemName(InterfaceInfo info) {
        if (info.getSystemId() != null) {
            SysSystem sys = sysSystemService.getById(info.getSystemId());
            if (sys != null) {
                info.setSystemName(sys.getName());
            }
        }
    }

    @GetMapping("/list")
    public ApiResponse<Page<InterfaceInfo>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long systemId,
            HttpServletRequest request) {

        String currentUser = getCurrentUser(request);
        Long deptId = getCurrentDeptId(request);
        List<String> roles = getCurrentRoles(request);
        boolean isAdmin = roles.contains("ADMIN");

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
        if (systemId != null) {
            wrapper.eq(InterfaceInfo::getSystemId, systemId);
        }
        // 非ADMIN用户只能查看自己部门所属系统的接口
        if (!isAdmin && deptId != null) {
            List<SysSystem> systems = sysSystemService.listByDeptId(deptId);
            List<Long> deptSystemIds = systems.stream().map(SysSystem::getId).collect(Collectors.toList());
            if (deptSystemIds.isEmpty()) {
                return ApiResponse.success("INTERFACE_LIST", "", new Page<>());
            }
            wrapper.in(InterfaceInfo::getSystemId, deptSystemIds);
        }
        // 草稿只对创建人可见：status != 0 OR created_by = currentUser
        wrapper.and(w -> w.ne(InterfaceInfo::getStatus, InterfaceStatus.DRAFT.getCode())
                .or(sub -> sub.eq(InterfaceInfo::getStatus, InterfaceStatus.DRAFT.getCode())
                        .eq(InterfaceInfo::getCreatedBy, currentUser)));
        wrapper.orderByDesc(InterfaceInfo::getUpdatedTime);

        Page<InterfaceInfo> result = interfaceInfoService.page(page, wrapper);

        // 计算待审批接口的 canWithdraw 状态
        List<InterfaceInfo> records = result.getRecords();
        List<String> pendingTransnos = records.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(InterfaceInfo::getTransno)
                .collect(Collectors.toList());
        if (!pendingTransnos.isEmpty()) {
            // 批量查询待审批记录
            LambdaQueryWrapper<ApprovalInfo> approvalWrapper = new LambdaQueryWrapper<>();
            approvalWrapper.in(ApprovalInfo::getTransno, pendingTransnos)
                    .eq(ApprovalInfo::getStatus, 0);
            List<ApprovalInfo> pendingApprovals = approvalInfoService.list(approvalWrapper);
            Map<String, Long> transnoToApprovalId = pendingApprovals.stream()
                    .collect(Collectors.toMap(ApprovalInfo::getTransno, ApprovalInfo::getId, (a, b) -> a));

            if (!transnoToApprovalId.isEmpty()) {
                // 批量查询已处理的流程步骤
                LambdaQueryWrapper<ApprovalFlow> flowWrapper = new LambdaQueryWrapper<>();
                flowWrapper.in(ApprovalFlow::getApprovalId, transnoToApprovalId.values())
                        .ne(ApprovalFlow::getStatus, 0);
                List<ApprovalFlow> processedFlows = approvalInfoService.listFlows(flowWrapper);
                Set<Long> approvalsWithProcessedStep = processedFlows.stream()
                        .map(ApprovalFlow::getApprovalId)
                        .collect(Collectors.toSet());

                for (InterfaceInfo info : records) {
                    if (info.getStatus() != null && info.getStatus() == 1) {
                        Long approvalId = transnoToApprovalId.get(info.getTransno());
                        info.setCanWithdraw(approvalId != null && !approvalsWithProcessedStep.contains(approvalId));
                    } else {
                        info.setCanWithdraw(false);
                    }
                }
            } else {
                records.forEach(r -> r.setCanWithdraw(false));
            }
        }

        return ApiResponse.success("INTERFACE_LIST", "", result);
    }

    @GetMapping("/{id}")
    public ApiResponse<InterfaceInfo> detail(@PathVariable Long id) {
        return ApiResponse.success("INTERFACE_DETAIL", "", interfaceInfoService.getById(id));
    }

    @PostMapping
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<InterfaceInfo> create(@RequestBody InterfaceInfo info, HttpServletRequest request) {
        fillSystemName(info);
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
        fillSystemName(info);
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
        String inputSchema = body.get("inputSchema");
        // 保存前校验 SQL 只读安全性
        if (inputSchema != null && !inputSchema.isEmpty()) {
            sqlSecurityValidator.validateXmlConfig(inputSchema);
        }
        String operator = body.getOrDefault("operator", getCurrentUser(request));
        InterfaceVersion version = interfaceVersionService.saveSchema(
                transno, inputSchema, body.get("outputSchema"),
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
        String applicantName = body.getOrDefault("applicantName", "");

        // 判断审批类型：如果接口有 currentVersion 且 > 0 则为修改，否则为新增
        InterfaceInfo interfaceInfo = interfaceInfoService.getByTransnoAnyStatus(transno);
        int type = (interfaceInfo != null && interfaceInfo.getCurrentVersion() != null
                && interfaceInfo.getCurrentVersion() > 0) ? 2 : 1;

        // 构建 params
        Map<String, Object> params = new HashMap<>();
        params.put("transno", transno);
        params.put("versionNo", versionNo);
        params.put("applicantName", applicantName);
        params.put("applicantDeptId", request.getAttribute("adminDeptId"));
        if (interfaceInfo != null) {
            params.put("providerSystemId", interfaceInfo.getSystemId());
        }

        approvalInfoService.submit(type, params, operator);
        return ApiResponse.success("APPROVAL_SUBMIT", "", null);
    }

    @PostMapping("/{transno}/approve")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> approveAndPublish(
            @PathVariable String transno,
            HttpServletRequest request) {
        String approver = getCurrentUser(request);
        String approverName = request.getAttribute("adminRealName") != null
                ? request.getAttribute("adminRealName").toString() : "";
        // 通过 transno 查找待审批的 ApprovalInfo 记录
        Long approvalId = findPendingApprovalId(transno);
        // 审批发布前校验待发布版本的 SQL 只读安全性
        validatePendingVersionSchema(approvalId);
        approvalInfoService.approve(approvalId, approver, approverName, getCurrentDeptId(request), getCurrentRoles(request));
        return ApiResponse.success("APPROVAL_PASS", "", null);
    }

    @PostMapping("/{transno}/reject")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> rejectApproval(
            @PathVariable String transno,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String approver = getCurrentUser(request);
        String approverName = request.getAttribute("adminRealName") != null
                ? request.getAttribute("adminRealName").toString() : "";
        // 通过 transno 查找待审批的 ApprovalInfo 记录
        Long approvalId = findPendingApprovalId(transno);
        approvalInfoService.reject(approvalId, approver, approverName, body.get("reason"), getCurrentDeptId(request), getCurrentRoles(request));
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
        String applicant = getCurrentUser(request);
        // 通过 transno 查找待审批的 ApprovalInfo 记录
        Long approvalId = findPendingApprovalId(transno);
        approvalInfoService.withdraw(approvalId, applicant);
        return ApiResponse.success("APPROVAL_WITHDRAW", "", null);
    }

    @PostMapping("/debug")
    public ApiResponse<Object> debug(@RequestBody Map<String, Object> body) {
        String transno = (String) body.get("transno");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");

        DebugContext debugContext = new DebugContext(true);
        debugContext.setTransno(transno);
        debugContext.setStartTimeMs(System.currentTimeMillis());

        try {
            String xmlConfig = interfaceInfoService.getActiveXmlConfig(transno);
            Object result = xmlEngine.execute(xmlConfig, params, debugContext);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", result);
            response.put("trace", buildTraceMap(debugContext, params));
            return ApiResponse.success("DEBUG", "", response);
        } catch (Exception e) {
            debugContext.setEndTimeMs(System.currentTimeMillis());
            debugContext.setTotalTimeMs(debugContext.getEndTimeMs() - debugContext.getStartTimeMs());
            debugContext.setSuccess(false);
            debugContext.setErrorMessage(sanitizeErrorMessage(e.getMessage()));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("data", null);
            response.put("error", sanitizeErrorMessage(e.getMessage()));
            response.put("trace", buildTraceMap(debugContext, params));
            return ApiResponse.success("DEBUG", "", response);
        }
    }

    /**
     * 将 DebugContext 转为前端可展示的 Map。
     * 仅暴露安全字段，对 SQL 参数做敏感值脱敏。
     */
    private Map<String, Object> buildTraceMap(DebugContext debugContext, Map<String, Object> requestParams) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("transno", debugContext.getTransno());
        trace.put("totalTimeMs", debugContext.getTotalTimeMs());
        trace.put("success", debugContext.isSuccess());

        // 执行阶段
        List<Map<String, Object>> steps = new ArrayList<>();
        for (DebugContext.DebugStep step : debugContext.getSteps()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", step.getName());
            s.put("status", step.getStatus());
            s.put("elapsedTimeMs", step.getElapsedTimeMs());
            s.put("errorMessage", step.getErrorMessage());
            steps.add(s);
        }
        trace.put("steps", steps);

        // 查询跟踪（参数脱敏）
        List<Map<String, Object>> queries = new ArrayList<>();
        for (DebugTrace qt : debugContext.getTraces()) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("queryId", qt.getQueryId());
            q.put("type", qt.getType());
            q.put("datasource", qt.getDatasource());
            q.put("sql", qt.getSql());
            q.put("params", sanitizeParams(qt.getParams(), requestParams));
            q.put("paginationMode", qt.getPaginationMode());
            q.put("rowCount", qt.getRowCount());
            q.put("elapsedTimeMs", qt.getElapsedTimeMs());
            q.put("status", qt.getStatus());
            q.put("errorMessage", qt.getErrorMessage());
            queries.add(q);
        }
        trace.put("queries", queries);
        return trace;
    }

    /**
     * 对 SQL 参数值做敏感脱敏：若值来自 requestData 中敏感 key（password/token/secret 等），替换为 "***"
     */
    private List<Object> sanitizeParams(List<Object> params, Map<String, Object> requestParams) {
        if (params == null || requestParams == null || requestParams.isEmpty()) {
            return params;
        }
        // 收集 requestData 中敏感 key 对应的值
        Set<Object> sensitiveValues = new HashSet<>();
        for (Map.Entry<String, Object> entry : requestParams.entrySet()) {
            if (isSensitiveKey(entry.getKey()) && entry.getValue() != null) {
                sensitiveValues.add(entry.getValue());
            }
        }
        if (sensitiveValues.isEmpty()) {
            return params;
        }
        List<Object> result = new ArrayList<>(params.size());
        for (Object param : params) {
            result.add(sensitiveValues.contains(param) ? "***" : param);
        }
        return result;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return SENSITIVE_KEYS.contains(lower);
    }

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd", "token", "secret", "appsecret",
            "authorization", "apikey", "api_key", "accesskey", "access_key",
            "privatekey", "private_key", "credential"
    ));

    /**
     * 截断过长错误信息，防止前端展示异常
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) return null;
        if (message.length() > 500) {
            return message.substring(0, 500) + "...";
        }
        return message;
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
