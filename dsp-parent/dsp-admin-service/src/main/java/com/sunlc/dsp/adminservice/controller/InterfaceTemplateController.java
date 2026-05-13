package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.entity.InterfaceTemplate;
import com.sunlc.dsp.entity.InterfaceTemplateHistory;
import com.sunlc.dsp.service.InterfaceTemplateService;
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
        InterfaceTemplate template = interfaceTemplateService.createTemplate(
                body.get("transno"), body.get("xmlContent"),
                body.get("changeLog"), body.get("operator"));
        return ApiResponse.success("TEMPLATE_CREATE", "", template);
    }

    @PutMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<InterfaceTemplate> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        InterfaceTemplate template = interfaceTemplateService.updateTemplate(
                id, body.get("xmlContent"), body.get("changeLog"), body.get("operator"));
        return ApiResponse.success("TEMPLATE_UPDATE", "", template);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"USER", "DEPT_MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interfaceTemplateService.removeById(id);
        return ApiResponse.success("TEMPLATE_DELETE", "", null);
    }

    @PostMapping("/{id}/publish")
    @RequireRole({"DEPT_MANAGER"})
    public ApiResponse<Void> publish(@PathVariable Long id, @RequestBody Map<String, String> body) {
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
