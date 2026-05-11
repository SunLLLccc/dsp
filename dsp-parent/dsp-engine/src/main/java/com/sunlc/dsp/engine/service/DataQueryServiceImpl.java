package com.sunlc.dsp.engine.service;

import com.sunlc.dsp.common.model.ApiRequest;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.common.service.DataQueryService;
import com.sunlc.dsp.engine.XmlEngine;
import com.sunlc.dsp.service.InterfaceInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 数据查询服务实现
 * 桥接 dsp-common 的 DataQueryService 接口和 dsp-engine 的 XmlEngine
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {

    private final XmlEngine xmlEngine;
    private final InterfaceInfoService interfaceInfoService;

    @Override
    public ApiResponse<Object> execute(ApiRequest<Map<String, Object>> request, String transno) {
        String traceId = request.getHead() != null && request.getHead().getTraceId() != null
                ? request.getHead().getTraceId() : "";

        try {
            String xmlConfig = interfaceInfoService.getActiveXmlConfig(transno);
            Object result = xmlEngine.execute(xmlConfig, request.getRequestData());
            return ApiResponse.success(transno, traceId, result);
        } catch (Exception e) {
            log.error("数据查询异常: transno={}", transno, e);
            throw e;
        }
    }
}
