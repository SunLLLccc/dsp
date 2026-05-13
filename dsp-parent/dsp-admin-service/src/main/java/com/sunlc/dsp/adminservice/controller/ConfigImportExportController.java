package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.*;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceTemplateService;
import com.sunlc.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/config")
@RequiredArgsConstructor
public class ConfigImportExportController {

    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final InterfaceTemplateService interfaceTemplateService;

    private String getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute("adminUser");
        return user != null ? user.toString() : "anonymous";
    }

    /**
     * 导出指定接口的完整配置（接口信息 + 版本Schema + 模板XML）
     */
    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportConfig(@RequestParam String transno) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 接口基本信息
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info == null) {
            return ApiResponse.error("CONFIG", "EXPORT", "4004", "接口不存在: " + transno);
        }
        Map<String, Object> infoMap = new LinkedHashMap<>();
        infoMap.put("transno", info.getTransno());
        infoMap.put("name", info.getName());
        infoMap.put("systemName", info.getSystemName());
        infoMap.put("description", info.getDescription());
        result.put("interfaceInfo", infoMap);

        // 最新已发布版本的Schema
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

        // 模板XML
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

    /**
     * 批量导出多个接口配置
     */
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
     * 导入配置（版本化导入）
     */
    @PostMapping("/import")
    @RequireRole({"IMPORTER", "ADMIN"})
    public ApiResponse<Map<String, Object>> importConfig(
            @RequestBody Map<String, Object> configData,
            HttpServletRequest request) {

        String operator = getCurrentUser(request);
        String changeLog = (String) configData.getOrDefault("changeLog", "从测试环境导入");

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

        // 检查接口是否存在
        InterfaceInfo existingInfo = interfaceInfoService.getByTransnoAnyStatus(transno);

        if (existingInfo == null) {
            // 新建接口
            isNew = true;
            InterfaceInfo newInfo = new InterfaceInfo();
            newInfo.setTransno(transno);
            newInfo.setName((String) infoMap.get("name"));
            newInfo.setSystemName((String) infoMap.get("systemName"));
            newInfo.setDescription((String) infoMap.get("description"));
            newInfo.setStatus(0);
            newInfo.setCurrentVersion(0);
            newInfo.setCreatedBy(operator);
            newInfo.setCreatedTime(LocalDateTime.now());
            newInfo.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.save(newInfo);
            result.put("interface", "新建接口 " + transno);
        } else {
            result.put("interface", "接口已存在: " + transno);
        }

        // 导入Schema版本
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = (Map<String, Object>) configData.get("schema");
        if (schemaMap != null) {
            InterfaceVersion version = new InterfaceVersion();
            version.setTransno(transno);
            version.setInputSchema((String) schemaMap.get("inputSchema"));
            version.setOutputSchema((String) schemaMap.get("outputSchema"));
            version.setChangeLog(changeLog);
            version.setStatus(0);
            version.setCreatedBy(operator);
            version.setCreatedTime(LocalDateTime.now());
            interfaceVersionService.saveSchema(transno, version.getInputSchema(), version.getOutputSchema(), changeLog, operator);
            result.put("schema", "导入Schema版本成功");
        }

        // 导入模板XML
        @SuppressWarnings("unchecked")
        Map<String, Object> templateMap = (Map<String, Object>) configData.get("template");
        if (templateMap != null) {
            InterfaceTemplate existingTemplate = interfaceTemplateService.getByTransno(transno);
            String xmlContent = (String) templateMap.get("xmlContent");

            if (existingTemplate == null) {
                InterfaceTemplate newTemplate = new InterfaceTemplate();
                newTemplate.setTransno(transno);
                newTemplate.setXmlContent(xmlContent);
                newTemplate.setVersionNo(1);
                newTemplate.setStatus(0);
                newTemplate.setCreatedBy(operator);
                newTemplate.setCreatedTime(LocalDateTime.now());
                newTemplate.setUpdatedTime(LocalDateTime.now());
                InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
                if (info != null) {
                    newTemplate.setInterfaceName(info.getName());
                    newTemplate.setSystemName(info.getSystemName());
                }
                interfaceTemplateService.save(newTemplate);
                result.put("template", "新建模板成功");
            } else {
                existingTemplate.setXmlContent(xmlContent);
                existingTemplate.setUpdatedBy(operator);
                existingTemplate.setUpdatedTime(LocalDateTime.now());
                interfaceTemplateService.updateById(existingTemplate);
                result.put("template", "更新模板成功，版本号: " + existingTemplate.getVersionNo());
            }
        }

        log.info("配置导入完成: transno={}, operator={}, isNew={}", transno, operator, isNew);
        return ApiResponse.success("CONFIG", "IMPORT", result);
    }
}
