package com.sunlc.dsp.common.service;

import com.sunlc.dsp.common.model.ApiRequest;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.common.model.PaginationExportInfo;

import java.util.Map;

/**
 * 数据查询服务接口
 * 解耦其他模块对 engine 的直接依赖
 */
public interface DataQueryService {

    /**
     * 执行数据查询
     *
     * @param request 统一请求报文
     * @param transno 接口编码
     * @return 统一响应报文
     */
    ApiResponse<Object> execute(ApiRequest<Map<String, Object>> request, String transno);

    /**
     * 获取接口主导出查询的分页配置信息
     *
     * @param transno 接口编码
     * @return 分页配置信息，无分页配置时返回 null
     */
    PaginationExportInfo getExportPaginationInfo(String transno);
}
