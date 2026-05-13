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
}
