package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fintechervision.dsp.entity.AppAuth;

import java.util.List;
import java.util.Map;

/**
 * 应用授权服务
 * 从 AppAuthAdminController 中提取授权管理逻辑
 */
public interface AppAuthService extends IService<AppAuth> {

    /**
     * 查询应用列表
     */
    List<AppAuth> listAll();

    /**
     * 为应用签发JWT Token
     */
    Map<String, Object> generateToken(String appId);
}
