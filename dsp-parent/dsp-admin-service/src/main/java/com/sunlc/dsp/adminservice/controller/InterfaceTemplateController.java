package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.entity.InterfaceTemplate;
import com.sunlc.dsp.entity.InterfaceTemplateHistory;
import com.sunlc.dsp.service.InterfaceTemplateService;
import com.sunlc.dsp.engine.validator.SqlSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/template")
@RequiredArgsConstructor
public class InterfaceTemplateController {

    private final InterfaceTemplateService interfaceTemplateService;
    private final SqlSecurityValidator sqlSecurityValidator;

    @GetMapping("/list")
    public ApiResponse<Page<InterfaceTemplate>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String transno,
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success("TEMPLATE_LIST", "",
                interfaceTemplateService.listTemplates(transno, systemName, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterfaceTemplate> detail(@PathVariable Long id) {
        return ApiResponse.success("TEMPLATE_DETAIL", "", interfaceTemplateService.getById(id));
    }

    @PostMapping
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<InterfaceTemplate> create(@RequestBody Map<String, String> body) {
        String xmlContent = body.get("xmlContent");
        // 保存前校验 XML 中的 SQL 只读安全性
        if (xmlContent != null && !xmlContent.isEmpty()) {
            sqlSecurityValidator.validateXmlConfig(xmlContent);
        }
        InterfaceTemplate template = interfaceTemplateService.createTemplate(
                body.get("transno"), xmlContent,
                body.get("changeLog"), body.get("operator"));
        return ApiResponse.success("TEMPLATE_CREATE", "", template);
    }

    @PutMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<InterfaceTemplate> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String xmlContent = body.get("xmlContent");
        // 保存前校验 XML 中的 SQL 只读安全性
        if (xmlContent != null && !xmlContent.isEmpty()) {
            sqlSecurityValidator.validateXmlConfig(xmlContent);
        }
        InterfaceTemplate template = interfaceTemplateService.updateTemplate(
                id, xmlContent, body.get("changeLog"), body.get("operator"));
        return ApiResponse.success("TEMPLATE_UPDATE", "", template);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interfaceTemplateService.removeById(id);
        return ApiResponse.success("TEMPLATE_DELETE", "", null);
    }

    @PostMapping("/{id}/publish")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> publish(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // 发布前对当前模板的 xmlContent 再做一次安全校验
        InterfaceTemplate template = interfaceTemplateService.getById(id);
        if (template != null && template.getXmlContent() != null && !template.getXmlContent().isEmpty()) {
            sqlSecurityValidator.validateXmlConfig(template.getXmlContent());
        }
        interfaceTemplateService.publishTemplate(id, body.get("operator"));
        return ApiResponse.success("TEMPLATE_PUBLISH", "", null);
    }

    @PostMapping("/{id}/offline")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> offline(@PathVariable Long id) {
        interfaceTemplateService.offlineTemplate(id);
        return ApiResponse.success("TEMPLATE_OFFLINE", "", null);
    }

    @GetMapping("/generate")
    public ApiResponse<String> generateXml(@RequestParam String transno) {
        return ApiResponse.success("TEMPLATE_GENERATE", "",
                interfaceTemplateService.generateXmlFromSchema(transno));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<Page<InterfaceTemplateHistory>> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success("TEMPLATE_HISTORY", "",
                interfaceTemplateService.historyList(id, pageNum, pageSize));
    }

    @GetMapping("/transno/{transno}/history")
    public ApiResponse<List<InterfaceTemplateHistory>> historyByTransno(@PathVariable String transno) {
        return ApiResponse.success("TEMPLATE_HISTORY_BY_TRANSNO", "",
                interfaceTemplateService.getHistoryByTransno(transno));
    }
}
