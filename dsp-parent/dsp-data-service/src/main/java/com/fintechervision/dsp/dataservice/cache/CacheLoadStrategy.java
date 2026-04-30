package com.fintechervision.dsp.dataservice.cache;

import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.service.InterfaceInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据服务缓存加载策略 — 定义缓存范围
 * 当前：所有已发布接口（status=1）
 * 后续可按业务条线拆分，只加载特定范围的接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheLoadStrategy {

    private final InterfaceInfoService interfaceInfoService;

    /**
     * 加载当前应缓存的 transno 列表
     */
    public List<String> loadActiveTransnos() {
        try {
            return interfaceInfoService.list().stream()
                    .filter(info -> info.getStatus() != null && info.getStatus() == 1)
                    .map(InterfaceInfo::getTransno)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("加载活跃接口列表失败", e);
            return Collections.emptyList();
        }
    }
}
