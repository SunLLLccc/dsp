package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.adminservice.service.ConfigImportService;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.*;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceTemplateService;
import com.sunlc.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/config")
@RequiredArgsConstructor
public class ConfigImportExportController {

    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final InterfaceTemplateService interfaceTemplateService;
    private final ConfigImportService configImportService;

    private String getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute("adminUser");
        return user != null ? user.toString() : "anonymous";
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportConfig(@RequestParam String transno) {
        Map<String, Object> result = new LinkedHashMap<>();

        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info == null) {
            return ApiResponse.error("CONFIG", "EXPORT", "4004", "接口不存在: " + transno);
        }
        Map<String, Object> infoMap = new LinkedHashMap<>();
        infoMap.put("transno", info.getTransno());
        infoMap.put("name", info.getName());
        infoMap.put("systemName", info.getSystemName());
        infoMap.put("systemId", info.getSystemId());
        infoMap.put("description", info.getDescription());
        result.put("interfaceInfo", infoMap);

        List<InterfaceVersion> versions = interfaceVersionService.list(
                new LambdaQueryWrapper<InterfaceVersion>()
                        .eq(InterfaceVersion::getTransno, transno)
                        .eq(InterfaceVersion::getStatus, 3)
                        .orderByDesc(InterfaceVersion::getVersionNo));
        if (!versions.isEmpty()) {
            InterfaceVersion latest = versions.get(0);
            Map<String, Object> schemaMap = new LinkedHashMap<>();
            schemaMap.put("versionNo", latest.getVersionNo());
            schemaMap.put("inputSchema", latest.getInputSchema());
            schemaMap.put("outputSchema", latest.getOutputSchema());
            schemaMap.put("changeLog", latest.getChangeLog());
            result.put("schema", schemaMap);
        }

        InterfaceTemplate template = interfaceTemplateService.getByTransno(transno);
        if (template != null) {
            Map<String, Object> templateMap = new LinkedHashMap<>();
            templateMap.put("xmlContent", template.getXmlContent());
            templateMap.put("versionNo", template.getVersionNo());
            result.put("template", templateMap);
        }

        result.put("exportTime", LocalDateTime.now().toString());
        result.put("version", "1.0");

        return ApiResponse.success("CONFIG", "EXPORT", result);
    }

    @PostMapping("/export/batch")
    public ApiResponse<Map<String, Object>> exportBatch(@RequestBody Map<String, List<String>> body) {
        List<String> transnos = body.get("transnos");
        if (transnos == null || transnos.isEmpty()) {
            return ApiResponse.error("CONFIG", "EXPORT_BATCH", "4001", "请选择要导出的接口");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> configs = new ArrayList<>();
        for (String transno : transnos) {
            ApiResponse<Map<String, Object>> single = exportConfig(transno);
            if (single.getData() != null) {
                configs.add(single.getData());
            }
        }
        result.put("configs", configs);
        result.put("exportTime", LocalDateTime.now().toString());
        result.put("version", "1.0");

        return ApiResponse.success("CONFIG", "EXPORT_BATCH", result);
    }

    /**
     * 导入配置 — 直接生效，不需要审批或发布。
     * 业务逻辑由 {@link com.sunlc.dsp.adminservice.service.ConfigImportService} 承载。
     * Controller 保留入参形状校验（interfaceInfo/transno），维持原有响应语义（code/action/module 不变）；
     * Service 内部仍保留 BAD_REQUEST 校验供 Text2API publish 复用。
     */
    @PostMapping("/import")
    @RequireRole({"IMPORTER", "ADMIN"})
    public ApiResponse<Map<String, Object>> importConfig(
            @RequestBody Map<String, Object> configData,
            HttpServletRequest request) {

        // 入参形状校验（保持原有响应语义，不改变 code/action/module）
        @SuppressWarnings("unchecked")
        Map<String, Object> infoMap = (Map<String, Object>) configData.get("interfaceInfo");
        if (infoMap == null) {
            return ApiResponse.error("CONFIG", "IMPORT", "4001", "缺少接口信息");
        }
        String transno = (String) infoMap.get("transno");
        if (transno == null || transno.isEmpty()) {
            return ApiResponse.error("CONFIG", "IMPORT", "4001", "缺少接口编码");
        }

        String operator = getCurrentUser(request);
        Map<String, Object> result = configImportService.importConfig(configData, operator);
        return ApiResponse.success("CONFIG", "IMPORT", result);
    }
}
