package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.common.service.XmlConfigCacheInvalidator;
import com.sunlc.dsp.entity.*;
import com.sunlc.dsp.enums.InterfaceStatus;
import com.sunlc.dsp.enums.VersionStatus;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceTemplateService;
import com.sunlc.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    private final XmlConfigCacheInvalidator xmlConfigCacheInvalidator;

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
     * 导入配置 — 直接生效，不需要审批或发布
     * 新建/覆盖接口，版本和模板直接设为已发布状态，维护历史记录
     */
    @PostMapping("/import")
    @RequireRole({"IMPORTER", "ADMIN"})
    @Transactional
    public ApiResponse<Map<String, Object>> importConfig(
            @RequestBody Map<String, Object> configData,
            HttpServletRequest request) {

        String operator = getCurrentUser(request);
        String changeLog = (String) configData.getOrDefault("changeLog", "配置导入");

        @SuppressWarnings("unchecked")
        Map<String, Object> infoMap = (Map<String, Object>) configData.get("interfaceInfo");
        if (infoMap == null) {
            return ApiResponse.error("CONFIG", "IMPORT", "4001", "缺少接口信息");
        }

        String transno = (String) infoMap.get("transno");
        if (transno == null || transno.isEmpty()) {
            return ApiResponse.error("CONFIG", "IMPORT", "4001", "缺少接口编码");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        boolean isNew = false;

        // 1. 新建或更新接口基础信息
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);

        if (info == null) {
            isNew = true;
            info = new InterfaceInfo();
            info.setTransno(transno);
            info.setName((String) infoMap.get("name"));
            info.setSystemName((String) infoMap.get("systemName"));
            Object systemIdVal = infoMap.get("systemId");
            if (systemIdVal instanceof Number) {
                info.setSystemId(((Number) systemIdVal).longValue());
            }
            info.setDescription((String) infoMap.get("description"));
            info.setStatus(InterfaceStatus.PUBLISHED.getCode());
            info.setCurrentVersion(1);
            info.setCreatedBy(operator);
            info.setCreatedTime(LocalDateTime.now());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.save(info);
            result.put("interface", "新建接口 " + transno);
        } else {
            info.setName((String) infoMap.get("name"));
            info.setSystemName((String) infoMap.get("systemName"));
            Object systemIdVal = infoMap.get("systemId");
            if (systemIdVal instanceof Number) {
                info.setSystemId(((Number) systemIdVal).longValue());
            }
            info.setDescription((String) infoMap.get("description"));
            info.setStatus(InterfaceStatus.PUBLISHED.getCode());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
            result.put("interface", "覆盖接口 " + transno);
        }

        // 2. 导入Schema版本 — 直接发布
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = (Map<String, Object>) configData.get("schema");
        if (schemaMap != null) {
            InterfaceVersion version = interfaceVersionService.saveSchema(
                    transno,
                    (String) schemaMap.get("inputSchema"),
                    (String) schemaMap.get("outputSchema"),
                    changeLog,
                    operator);
            // 直接设为已发布状态
            version.setStatus(VersionStatus.PUBLISHED.getCode());
            version.setPublishedTime(LocalDateTime.now());
            interfaceVersionService.updateById(version);

            // 更新接口的当前生效版本号
            info.setCurrentVersion(version.getVersionNo());
            interfaceInfoService.updateById(info);

            result.put("schema", "导入Schema V" + version.getVersionNo() + " 并直接发布");
        }

        // 3. 导入模板XML — 直接发布，维护历史记录
        @SuppressWarnings("unchecked")
        Map<String, Object> templateMap = (Map<String, Object>) configData.get("template");
        if (templateMap != null) {
            String xmlContent = (String) templateMap.get("xmlContent");
            InterfaceTemplate existingTemplate = interfaceTemplateService.getByTransno(transno);

            if (existingTemplate == null) {
                // 新建模板 — 使用 service 方法（自动保存历史）
                InterfaceTemplate created = interfaceTemplateService.createTemplate(
                        transno, xmlContent, changeLog, operator);
                // 直接发布
                interfaceTemplateService.publishTemplate(created.getId(), operator);
                result.put("template", "新建模板 V1 并直接发布");
            } else {
                // 覆盖模板 — updateTemplate 自动保存历史并递增版本号
                InterfaceTemplate updated = interfaceTemplateService.updateTemplate(
                        existingTemplate.getId(), xmlContent, changeLog, operator);
                // 确保已发布状态
                if (updated.getStatus() != InterfaceStatus.PUBLISHED.getCode()) {
                    interfaceTemplateService.publishTemplate(updated.getId(), operator);
                }
                result.put("template", "更新模板 V" + updated.getVersionNo() + " 并直接发布");
            }
        }

        // 4. 刷新缓存使配置立即生效
        xmlConfigCacheInvalidator.invalidate(transno);

        log.info("配置导入完成并生效: transno={}, operator={}, isNew={}", transno, operator, isNew);
        return ApiResponse.success("CONFIG", "IMPORT", result);
    }
}
