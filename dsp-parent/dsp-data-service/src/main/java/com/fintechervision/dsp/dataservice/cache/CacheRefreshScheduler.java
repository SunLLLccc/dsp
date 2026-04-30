package com.fintechervision.dsp.dataservice.cache;

import com.fintechervision.dsp.engine.cache.XmlConfigCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据服务缓存定时刷新调度器
 * 缓存范围：所有已发布接口（status=1）
 * 仅在 dsp.cache.xml.refresh-enabled=true 时启用
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dsp.cache.xml.refresh-enabled", havingValue = "true", matchIfMissing = false)
public class CacheRefreshScheduler {

    private final XmlConfigCacheManager cacheManager;
    private final CacheLoadStrategy cacheLoadStrategy;

    @Scheduled(fixedRateString = "${dsp.cache.xml.refresh-interval:300000}")
    public void refreshAll() {
        log.debug("开始定时刷新XML配置缓存");
        List<String> activeTransnos = cacheLoadStrategy.loadActiveTransnos();
        cacheManager.refreshAll(activeTransnos);
    }
}
