package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fintechervision.dsp.entity.DatasourceConfig;
import com.fintechervision.dsp.mapper.DatasourceConfigMapper;
import com.fintechervision.dsp.service.DatasourceManagerService;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceManagerServiceImpl extends ServiceImpl<DatasourceConfigMapper, DatasourceConfig>
        implements DatasourceManagerService {

    private final DynamicRoutingDataSource dynamicRoutingDataSource;
    private final DefaultDataSourceCreator defaultDataSourceCreator;

    @Override
    public DatasourceConfig getByDsName(String dsName) {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceConfig::getDsName, dsName)
               .eq(DatasourceConfig::getStatus, 1);
        return getOne(wrapper);
    }

    @Override
    public void loadAndRegisterAll() {
        List<DatasourceConfig> configs = listEnabled();
        for (DatasourceConfig config : configs) {
            try {
                registerDatasource(config);
            } catch (Exception e) {
                log.error("数据源注册失败: dsName={}, error={}", config.getDsName(), e.getMessage());
            }
        }
        log.info("数据源加载完成，共注册 {} 个", configs.size());
    }

    @Override
    public void registerDatasource(DatasourceConfig config) {
        String dsType = config.getDsType().toUpperCase();
        if ("HTTP".equals(dsType) || "DUBBO".equals(dsType)) {
            log.info("非JDBC数据源[{}]，跳过Dynamic-DS注册", config.getDsName());
            return;
        }

        Map<String, DataSource> currentDataSources = dynamicRoutingDataSource.getDataSources();
        if (currentDataSources.containsKey(config.getDsName())) {
            dynamicRoutingDataSource.removeDataSource(config.getDsName());
        }

        DataSourceProperty property = new DataSourceProperty();
        property.setUrl(config.getJdbcUrl());
        property.setUsername(config.getUsername());
        property.setPassword(config.getPassword());

        switch (dsType) {
            case "MYSQL": case "DORIS":
                property.setDriverClassName("com.mysql.cj.jdbc.Driver"); break;
            case "ORACLE":
                property.setDriverClassName("oracle.jdbc.OracleDriver"); break;
            case "POSTGRESQL":
                property.setDriverClassName("org.postgresql.Driver"); break;
            default:
                property.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        property.setType(com.alibaba.druid.pool.DruidDataSource.class);
        DataSource dataSource = defaultDataSourceCreator.createDataSource(property);
        dynamicRoutingDataSource.addDataSource(config.getDsName(), dataSource);
        log.info("数据源注册成功: dsName={}, type={}, url={}", config.getDsName(), dsType, config.getJdbcUrl());
    }

    @Override
    public void removeDatasource(String dsName) {
        dynamicRoutingDataSource.removeDataSource(dsName);
        log.info("数据源注销成功: dsName={}", dsName);
    }

    @Override
    public List<DatasourceConfig> listAll() { return list(); }

    private List<DatasourceConfig> listEnabled() {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceConfig::getStatus, 1);
        return list(wrapper);
    }
}
