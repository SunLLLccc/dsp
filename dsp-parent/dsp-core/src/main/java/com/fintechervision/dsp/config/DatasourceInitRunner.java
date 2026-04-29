package com.fintechervision.dsp.config;

import com.fintechervision.dsp.service.DatasourceManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceInitRunner implements ApplicationRunner {
    private final DatasourceManagerService datasourceManagerService;
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始加载数据源配置...");
        try { datasourceManagerService.loadAndRegisterAll(); } catch (Exception e) { log.error("数据源初始化加载失败", e); }
    }
}
