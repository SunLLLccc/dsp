package com.fintechervision.dsp.engine;

import com.fintechervision.dsp.engine.model.DataSourceConfig;

/**
 * 数据源注册回调接口
 * 由使用引擎的服务（如 dsp-data-service）实现，将 XML 中定义的内联数据源注册到运行时
 */
public interface DataSourceRegistrar {
    void register(DataSourceConfig config);
}
