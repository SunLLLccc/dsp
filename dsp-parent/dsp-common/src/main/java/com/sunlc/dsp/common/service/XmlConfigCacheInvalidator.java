package com.sunlc.dsp.common.service;

/**
 * XML配置缓存失效接口
 * 解耦 dsp-core 对 dsp-engine 缓存实现的直接依赖
 */
public interface XmlConfigCacheInvalidator {

    /**
     * 使指定 transno 的缓存失效
     */
    void invalidate(String transno);
}
