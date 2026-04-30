package com.fintechervision.dsp.dataservice;

import com.fintechervision.dsp.engine.DataSourceRegistrar;
import com.fintechervision.dsp.engine.XmlEngine;
import com.fintechervision.dsp.engine.model.DataSourceConfig;
import com.fintechervision.dsp.entity.DatasourceConfig;
import com.fintechervision.dsp.service.DatasourceManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 将 XML 内联数据源注册到 DatasourceManagerService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InlineDataSourceRegistrar implements DataSourceRegistrar {

    private final XmlEngine xmlEngine;
    private final DatasourceManagerService datasourceManagerService;

    @PostConstruct
    public void init() {
        xmlEngine.setDataSourceRegistrar(this);
    }

    @Override
    public void register(DataSourceConfig config) {
        // 先检查是否已注册（数据库中已有同名数据源）
        DatasourceConfig existing = datasourceManagerService.getByDsName(config.getName());
        if (existing != null) {
            log.debug("数据源已存在，跳过XML内联注册: name={}", config.getName());
            return;
        }

        // 动态注册数据源
        DatasourceConfig dsConfig = new DatasourceConfig();
        dsConfig.setDsName(config.getName());
        dsConfig.setDsType(config.getType() != null ? config.getType() : "MYSQL");
        dsConfig.setJdbcUrl(config.getUrl());
        dsConfig.setUsername(config.getUsername());
        dsConfig.setPassword(config.getPassword());
        dsConfig.setExtraConfig(config.getExtraConfig());
        dsConfig.setStatus(1);

        datasourceManagerService.registerDatasource(dsConfig);
        log.info("XML内联数据源已动态注册: name={}, type={}", config.getName(), config.getType());
    }
}
