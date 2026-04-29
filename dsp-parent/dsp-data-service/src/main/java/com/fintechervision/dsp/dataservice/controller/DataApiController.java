package com.fintechervision.dsp.dataservice.controller;

import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.common.model.ApiRequest;
import com.fintechervision.dsp.common.model.ApiResponse;
import com.fintechervision.dsp.engine.XmlEngine;
import com.fintechervision.dsp.service.InterfaceInfoService;
import com.fintechervision.dsp.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static com.fintechervision.dsp.common.enums.ErrorCode.TOKEN_MISSING;

@Slf4j
@RestController
@RequestMapping("/dsp/api")
@RequiredArgsConstructor
public class DataApiController {

    private final InterfaceInfoService interfaceInfoService;
    private final XmlEngine xmlEngine;
    private final ExportService exportService;

    @PostMapping("/{transno}")
    public ApiResponse<Object> query(
            @PathVariable String transno,
            @RequestBody ApiRequest<Map<String, Object>> request) {

        if (request.getHead() != null && request.getHead().getTransno() != null) {
            if (!transno.equals(request.getHead().getTransno())) {
                throw new BusinessException(TOKEN_MISSING, "transno不一致，疑似篡改");
            }
        }

        String traceId = getTraceId(request);
        long startTime = System.currentTimeMillis();

        try {
            log.info("数据查询: transno={}, appId={}, traceId={}", transno, getAppId(request), traceId);

            String xmlConfig = interfaceInfoService.getActiveXmlConfig(transno);
            Object result = xmlEngine.execute(xmlConfig, request.getRequestData());

            log.info("数据查询完成: transno={}, 耗时={}ms", transno, System.currentTimeMillis() - startTime);
            return ApiResponse.success(transno, traceId, result);

        } catch (Exception e) {
            log.error("数据查询异常: transno={}, 耗时={}ms", transno, System.currentTimeMillis() - startTime, e);
            throw e;
        }
    }

    /**
     * 在线导出
     */
    @PostMapping("/{transno}/export")
    public void onlineExport(@PathVariable String transno,
                             @RequestBody Map<String, Object> requestBody,
                             HttpServletResponse response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) requestBody.get("params");
        String format = (String) requestBody.getOrDefault("format", "xlsx");

        log.info("在线导出请求: transno={}, format={}", transno, format);
        exportService.onlineExport(transno, params != null ? params : new java.util.HashMap<>(), format, response);
    }

    private String getTraceId(ApiRequest<?> request) {
        return request.getHead() != null && request.getHead().getTraceId() != null
                ? request.getHead().getTraceId() : "";
    }

    private String getAppId(ApiRequest<?> request) {
        return request.getHead() != null && request.getHead().getAppId() != null
                ? request.getHead().getAppId() : "";
    }
}
