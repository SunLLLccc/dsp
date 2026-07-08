package com.sunlc.dsp.adminservice.service;

import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.service.XmlConfigCacheInvalidator;
import com.sunlc.dsp.engine.validator.SqlSecurityValidator;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.InterfaceTemplate;
import com.sunlc.dsp.entity.InterfaceVersion;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceTemplateService;
import com.sunlc.dsp.service.InterfaceVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConfigImportService 单测。
 * 覆盖：正常导入（新建路径）、operator 透传、result 内容、缺少入参抛异常。
 */
@ExtendWith(MockitoExtension.class)
class ConfigImportServiceTest {

    @Mock private InterfaceInfoService interfaceInfoService;
    @Mock private InterfaceVersionService interfaceVersionService;
    @Mock private InterfaceTemplateService interfaceTemplateService;
    @Mock private XmlConfigCacheInvalidator xmlConfigCacheInvalidator;
    @Mock private SqlSecurityValidator sqlSecurityValidator;

    @InjectMocks
    private ConfigImportService configImportService;

    @Test
    void importConfig_newInterface_executesFullChain() {
        // 准备：接口不存在（新建路径）
        when(interfaceInfoService.getByTransnoAnyStatus("T001")).thenReturn(null);
        when(interfaceInfoService.save(any())).thenReturn(true);
        // schema 导入
        InterfaceVersion version = new InterfaceVersion();
        version.setVersionNo(1);
        when(interfaceVersionService.saveSchema(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(version);
        // template 导入（不存在 → 新建）
        when(interfaceTemplateService.getByTransno("T001")).thenReturn(null);
        InterfaceTemplate created = new InterfaceTemplate();
        created.setId(100L);
        when(interfaceTemplateService.createTemplate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(created);

        // 执行
        Map<String, Object> configData = buildFullConfig("T001");
        Map<String, Object> result = configImportService.importConfig(configData, "admin");

        // 断言 result 内容
        assertTrue(result.containsKey("interface"));
        assertTrue(result.get("interface").toString().contains("新建接口 T001"));
        assertTrue(result.containsKey("schema"));
        assertTrue(result.containsKey("template"));

        // operator 透传
        ArgumentCaptor<String> operatorCaptor = ArgumentCaptor.forClass(String.class);
        verify(interfaceTemplateService).createTemplate(anyString(), anyString(), anyString(), operatorCaptor.capture());
        assertEquals("admin", operatorCaptor.getValue());

        // 链路完整性：校验 → saveSchema → updateById(version) → createTemplate → publishTemplate → invalidate
        verify(sqlSecurityValidator, times(2)).validateXmlConfig(anyString());
        verify(interfaceInfoService, times(1)).save(any());
        verify(interfaceVersionService, times(1)).saveSchema(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(interfaceVersionService, times(1)).updateById(any());
        verify(interfaceTemplateService, times(1)).createTemplate(anyString(), anyString(), anyString(), anyString());
        verify(interfaceTemplateService, times(1)).publishTemplate(any(), anyString());
        verify(xmlConfigCacheInvalidator, times(1)).invalidate("T001");
    }

    @Test
    void importConfig_overwriteInterface_updatesExisting() {
        // 准备：接口已存在（覆盖路径）
        InterfaceInfo existing = new InterfaceInfo();
        existing.setTransno("T002");
        when(interfaceInfoService.getByTransnoAnyStatus("T002")).thenReturn(existing);
        // schema 导入
        InterfaceVersion version = new InterfaceVersion();
        version.setVersionNo(2);
        when(interfaceVersionService.saveSchema(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(version);
        // template 已存在 → update
        InterfaceTemplate existingTpl = new InterfaceTemplate();
        existingTpl.setId(50L);
        existingTpl.setStatus(0); // 未发布
        when(interfaceTemplateService.getByTransno("T002")).thenReturn(existingTpl);
        InterfaceTemplate updated = new InterfaceTemplate();
        updated.setId(50L);
        updated.setVersionNo(2);
        updated.setStatus(0);
        when(interfaceTemplateService.updateTemplate(any(), anyString(), anyString(), anyString()))
                .thenReturn(updated);

        Map<String, Object> configData = buildFullConfig("T002");
        Map<String, Object> result = configImportService.importConfig(configData, "admin");

        assertTrue(result.get("interface").toString().contains("覆盖接口 T002"));
        // updateById 调 2 次：接口信息更新 + schema 后更新 currentVersion
        verify(interfaceInfoService, times(2)).updateById(any());
        verify(interfaceTemplateService, times(1)).updateTemplate(any(), anyString(), anyString(), anyString());
        // status 未发布 → 应调 publishTemplate
        verify(interfaceTemplateService, times(1)).publishTemplate(any(), anyString());
    }

    @Test
    void importConfig_missingInterfaceInfo_throws() {
        Map<String, Object> configData = new HashMap<>();
        // 不含 interfaceInfo
        assertThrows(BusinessException.class, () -> configImportService.importConfig(configData, "admin"));
        verify(interfaceInfoService, never()).getByTransnoAnyStatus(any());
    }

    @Test
    void importConfig_missingTransno_throws() {
        Map<String, Object> configData = new HashMap<>();
        Map<String, Object> infoMap = new HashMap<>();
        // 不含 transno
        infoMap.put("name", "测试");
        configData.put("interfaceInfo", infoMap);

        assertThrows(BusinessException.class, () -> configImportService.importConfig(configData, "admin"));
        verify(interfaceInfoService, never()).getByTransnoAnyStatus(any());
    }

    /** 构造完整配置（interfaceInfo + schema + template + changeLog）。 */
    private Map<String, Object> buildFullConfig(String transno) {
        Map<String, Object> configData = new LinkedHashMap<>();
        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("transno", transno);
        infoMap.put("name", "测试接口");
        infoMap.put("systemName", "测试系统");
        configData.put("interfaceInfo", infoMap);
        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("inputSchema", "<interface></interface>");
        schemaMap.put("outputSchema", "{}");
        configData.put("schema", schemaMap);
        Map<String, Object> templateMap = new HashMap<>();
        templateMap.put("xmlContent", "<interface></interface>");
        configData.put("template", templateMap);
        configData.put("changeLog", "测试导入");
        return configData;
    }
}
