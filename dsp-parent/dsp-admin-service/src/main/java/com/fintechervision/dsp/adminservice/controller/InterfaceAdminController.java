package com.fintechervision.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fintechervision.dsp.common.model.ApiResponse;
import com.fintechervision.dsp.engine.XmlEngine;
import com.fintechervision.dsp.entity.ApprovalRecord;
import com.fintechervision.dsp.entity.InterfaceDatasource;
import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.entity.InterfaceVersion;
import com.fintechervision.dsp.service.ApprovalRecordService;
import com.fintechervision.dsp.service.InterfaceDatasourceService;
import com.fintechervision.dsp.service.InterfaceInfoService;
import com.fintechervision.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    private final InterfaceDatasourceService interfaceDatasourceService;
    private final XmlEngine xmlEngine;

    @GetMapping("/list")
    public ApiResponse<Page<InterfaceInfo>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {

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
        wrapper.orderByDesc(InterfaceInfo::getUpdatedTime);

        return ApiResponse.success("INTERFACE_LIST", "", interfaceInfoService.page(page, wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterfaceInfo> detail(@PathVariable Long id) {
        return ApiResponse.success("INTERFACE_DETAIL", "", interfaceInfoService.getById(id));
    }

    @PostMapping
    public ApiResponse<InterfaceInfo> create(@RequestBody InterfaceInfo info) {
        info.setStatus(0);
        info.setCurrentVersion(0);
        info.setCreatedTime(LocalDateTime.now());
        info.setUpdatedTime(LocalDateTime.now());
        interfaceInfoService.save(info);
        return ApiResponse.success("INTERFACE_CREATE", "", info);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody InterfaceInfo info) {
        info.setId(id);
        info.setUpdatedTime(LocalDateTime.now());
        interfaceInfoService.updateById(info);
        return ApiResponse.success("INTERFACE_UPDATE", "", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interfaceInfoService.removeById(id);
        return ApiResponse.success("INTERFACE_DELETE", "", null);
    }

    @PostMapping("/{transno}/version")
    public ApiResponse<InterfaceVersion> saveSchema(
            @PathVariable String transno,
            @RequestBody Map<String, String> body) {
        InterfaceVersion version = interfaceVersionService.saveSchema(
                transno, body.get("inputSchema"), body.get("outputSchema"),
                body.get("changeLog"), body.get("operator"));
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

    @PostMapping("/{transno}/version/{versionNo}/submit")
    public ApiResponse<Void> submitApproval(
            @PathVariable String transno,
            @PathVariable Integer versionNo,
            @RequestBody Map<String, String> body) {
        interfaceVersionService.submitApproval(transno, versionNo, body.get("operator"));
        return ApiResponse.success("APPROVAL_SUBMIT", "", null);
    }

    @PostMapping("/{transno}/version/{versionNo}/approve")
    public ApiResponse<Void> approveAndPublish(
            @PathVariable String transno,
            @PathVariable Integer versionNo,
            @RequestBody Map<String, String> body) {
        interfaceVersionService.approveAndPublish(transno, versionNo, body.get("approver"));
        return ApiResponse.success("APPROVAL_PASS", "", null);
    }

    @PostMapping("/{transno}/version/{versionNo}/reject")
    public ApiResponse<Void> rejectApproval(
            @PathVariable String transno,
            @PathVariable Integer versionNo,
            @RequestBody Map<String, String> body) {
        interfaceVersionService.rejectApproval(transno, versionNo, body.get("reason"));
        return ApiResponse.success("APPROVAL_REJECT", "", null);
    }

    @PostMapping("/{transno}/offline")
    public ApiResponse<Void> offline(@PathVariable String transno) {
        interfaceVersionService.offline(transno);
        return ApiResponse.success("INTERFACE_OFFLINE", "", null);
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

    // ==================== 接口-数据源关联 ====================

    @GetMapping("/{transno}/datasources")
    public ApiResponse<List<InterfaceDatasource>> listDatasources(@PathVariable String transno) {
        return ApiResponse.success("INTERFACE_DATASOURCES", "",
                interfaceDatasourceService.listByTransno(transno));
    }

    @PostMapping("/{transno}/datasources")
    public ApiResponse<Void> bindDatasources(
            @PathVariable String transno,
            @RequestBody Map<String, List<String>> body) {
        interfaceDatasourceService.bindDatasources(transno, body.get("dsNames"));
        return ApiResponse.success("INTERFACE_DATASOURCE_BIND", "", null);
    }

    @PostMapping("/{transno}/datasource/{dsName}")
    public ApiResponse<Void> addDatasource(
            @PathVariable String transno,
            @PathVariable String dsName) {
        interfaceDatasourceService.addDatasource(transno, dsName);
        return ApiResponse.success("INTERFACE_DATASOURCE_ADD", "", null);
    }

    @DeleteMapping("/{transno}/datasource/{dsName}")
    public ApiResponse<Void> removeDatasource(
            @PathVariable String transno,
            @PathVariable String dsName) {
        interfaceDatasourceService.removeDatasource(transno, dsName);
        return ApiResponse.success("INTERFACE_DATASOURCE_REMOVE", "", null);
    }
}
