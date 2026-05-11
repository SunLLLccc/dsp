package com.sunlc.dsp.engine.cache;

import com.sunlc.dsp.common.service.XmlConfigCacheInvalidator;
import com.sunlc.dsp.engine.model.InterfaceConfig;
import com.sunlc.dsp.engine.parser.XmlConfigParser;
import com.sunlc.dsp.service.InterfaceInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XML配置本地缓存 — 缓存 transno → InterfaceConfig
 * 核心缓存操作：get/invalidate/refreshAll
 * 缓存范围和定时调度由各服务自行配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmlConfigCacheManager implements XmlConfigCacheInvalidator {

    private final InterfaceInfoService interfaceInfoService;
    private final XmlConfigParser xmlConfigParser;

    /** transno → InterfaceConfig */
    private final Map<String, InterfaceConfig> cache = new ConcurrentHashMap<>();

    /**
     * 获取缓存的 InterfaceConfig，缓存未命中则从DB加载并解析
     */
    public InterfaceConfig get(String transno) {
        InterfaceConfig config = cache.get(transno);
        if (config != null) {
            return config;
        }
        return loadAndCache(transno);
    }

    /**
     * 使指定 transno 的缓存失效
     */
    @Override
    public void invalidate(String transno) {
        cache.remove(transno);
        log.info("缓存失效: transno={}", transno);
    }

    /**
     * 按指定 transno 列表全量刷新缓存，不在列表中的缓存将被清除
     *
     * @param activeTransnos 当前应缓存的 transno 列表（由各服务自行定义查询逻辑）
     */
    public void refreshAll(List<String> activeTransnos) {
        try {
            int refreshCount = 0;
            for (String transno : activeTransnos) {
                try {
                    loadAndCache(transno);
                    refreshCount++;
                } catch (Exception e) {
                    cache.remove(transno);
                    log.warn("缓存刷新失败: transno={}, error={}", transno, e.getMessage());
                }
            }
            // 清除已下线/删除的接口缓存
            cache.keySet().retainAll(activeTransnos);

            log.info("缓存刷新完成: 总数={}, 已刷新接口数={}", cache.size(), refreshCount);
        } catch (Exception e) {
            log.error("缓存全量刷新异常", e);
        }
    }

    private InterfaceConfig loadAndCache(String transno) {
        String xmlConfig = interfaceInfoService.getActiveXmlConfig(transno);
        InterfaceConfig config = xmlConfigParser.parse(xmlConfig);
        cache.put(transno, config);
        return config;
    }
}
