package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.adminservice.service.ConfigImportService;
import com.sunlc.dsp.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ConfigImportExportController 回归测试。
 * 重点验证 importConfig 入参校验的响应语义不变（code/action/module），
 * 不因 ConfigImportService 抽取而改变缺 interfaceInfo/transno 时的行为。
 */
class ConfigImportExportControllerTest {

    private ConfigImportService configImportService;
    private ConfigImportExportController controller;

    @BeforeEach
    void setUp() {
        configImportService = mock(ConfigImportService.class);
        controller = new ConfigImportExportController(
                mock(com.sunlc.dsp.service.InterfaceInfoService.class),
                mock(com.sunlc.dsp.service.InterfaceVersionService.class),
                mock(com.sunlc.dsp.service.InterfaceTemplateService.class),
                configImportService);
    }

    @Test
    void importConfig_missingInterfaceInfo_returnsOldErrorResponse() {
        Map<String, Object> body = new HashMap<>(); // 不含 interfaceInfo
        HttpServletRequest req = mockRequest();

        ApiResponse<Map<String, Object>> resp = controller.importConfig(body, req);

        // 旧语义：code=4001, module=CONFIG, action=IMPORT
        assertEquals("4001", resp.getCode());
        assertEquals("CONFIG", resp.getHead().getTransno());
        assertNotNull(resp.getMessage());
        // Service 不应被调用
        verify(configImportService, never()).importConfig(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void importConfig_missingTransno_returnsOldErrorResponse() {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("name", "测试"); // 不含 transno
        body.put("interfaceInfo", infoMap);
        HttpServletRequest req = mockRequest();

        ApiResponse<Map<String, Object>> resp = controller.importConfig(body, req);

        assertEquals("4001", resp.getCode());
        assertEquals("CONFIG", resp.getHead().getTransno());
        verify(configImportService, never()).importConfig(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private HttpServletRequest mockRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("adminUser", "admin");
        return req;
    }
}
