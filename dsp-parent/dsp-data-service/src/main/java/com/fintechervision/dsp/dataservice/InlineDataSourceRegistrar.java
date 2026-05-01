package com.fintechervision.dsp.dataservice;

import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
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
 * 校验 XML 中引用的数据源是否已在 datasource_config 表中配置
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
        String dsName = config.getName();
        DatasourceConfig dsConfig = datasourceManagerService.getByDsName(dsName);
        if (dsConfig == null) {
            throw new BusinessException(ErrorCode.DATASOURCE_NOT_CONFIGURED,
                    "数据源未配置: " + dsName + "，请先在管理后台添加该数据源");
        }
        // 确保该数据源已注册到 Dynamic-DS 运行时
        datasourceManagerService.registerDatasource(dsConfig);
        log.debug("数据源校验通过并已注册: name={}", dsName);
    }
}
