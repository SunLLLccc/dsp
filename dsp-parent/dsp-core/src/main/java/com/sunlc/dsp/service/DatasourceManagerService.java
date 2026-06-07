package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.DatasourceConfig;
import java.util.List;

public interface DatasourceManagerService extends IService<DatasourceConfig> {
    DatasourceConfig getByDsName(String dsName);
    void loadAndRegisterAll();
    void registerDatasource(DatasourceConfig config);
    void removeDatasource(String dsName);
    List<DatasourceConfig> listAll();
    /**
     * 测试数据源连接，校验类型白名单和 URL 合法性，失败时返回脱敏信息
     */
    String testConnection(DatasourceConfig config);
}
