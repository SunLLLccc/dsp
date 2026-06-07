package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.AuditLog;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.service.AuditLogService;
import com.sunlc.dsp.service.InterfaceInfoService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 接口市场与健康看板。
 * 复用 interface_info 列表 + audit_log 聚合统计。
 */
@Slf4j
@RestController
@RequestMapping("/dsp/admin/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final InterfaceInfoService interfaceInfoService;
    private final AuditLogService auditLogService;

    /**
     * 接口市场目录列表，包含健康指标。
     * 健康指标从 audit_log 聚合，最近 7 天。
     */
    @GetMapping("/list")
    public ApiResponse<Page<MarketplaceItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String tag) {

        // 0. 标签筛选：当前无标签数据源，传入 tag 时返回空页，避免忽略筛选导致误命中
        if (tag != null && !tag.isEmpty()) {
            Page<MarketplaceItem> emptyPage = new Page<>(pageNum, pageSize, 0);
            emptyPage.setRecords(Collections.emptyList());
            return ApiResponse.success("MARKETPLACE_LIST", "", emptyPage);
        }

        // 1. 查询接口列表
        LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(InterfaceInfo::getTransno, keyword)
                    .or().like(InterfaceInfo::getName, keyword)
                    .or().like(InterfaceInfo::getDescription, keyword));
        }
        if (systemId != null) {
            wrapper.eq(InterfaceInfo::getSystemId, systemId);
        }
        if (status != null) {
            wrapper.eq(InterfaceInfo::getStatus, status);
        }
        wrapper.orderByDesc(InterfaceInfo::getUpdatedTime);
        Page<InterfaceInfo> page = interfaceInfoService.page(
                new Page<>(pageNum, pageSize), wrapper);

        // 2. 提取 transno 列表用于批量聚合
        List<String> transnos = page.getRecords().stream()
                .map(InterfaceInfo::getTransno).collect(Collectors.toList());

        // 3. 从 audit_log 聚合健康指标（最近 7 天）
        Map<String, HealthStats> statsMap = aggregateHealthStats(transnos);

        // 4. 组装返回
        Page<MarketplaceItem> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(info -> {
            MarketplaceItem item = new MarketplaceItem();
            item.setId(info.getId());
            item.setTransno(info.getTransno());
            item.setName(info.getName());
            item.setDescription(info.getDescription());
            item.setSystemId(info.getSystemId());
            item.setSystemName(info.getSystemName());
            item.setStatus(info.getStatus());
            item.setCurrentVersion(info.getCurrentVersion());
            item.setUpdatedTime(info.getUpdatedTime());
            item.setCreatedBy(info.getCreatedBy());

            // 健康指标
            HealthStats stats = statsMap.getOrDefault(info.getTransno(), new HealthStats());
            item.setCallCount(stats.callCount);
            item.setSuccessRate(stats.successRate);
            item.setAvgCostMs(stats.avgCostMs);
            item.setLastErrorTime(stats.lastErrorTime);
            item.setLastErrorMessage(stats.lastErrorMessage);
            return item;
        }).collect(Collectors.toList()));

        return ApiResponse.success("MARKETPLACE_LIST", "", result);
    }

    /**
     * 从 audit_log 聚合每个 transno 的健康统计。
     * 使用 Java 内存聚合，避免手写 XML mapper。
     * 只查最近 7 天数据。
     */
    private Map<String, HealthStats> aggregateHealthStats(List<String> transnos) {
        if (transnos.isEmpty()) return Collections.emptyMap();

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AuditLog::getTransno, transnos)
               .ge(AuditLog::getCreatedTime, sevenDaysAgo);
        List<AuditLog> logs = auditLogService.list(wrapper);

        // 按 transno 分组统计
        Map<String, HealthStats> statsMap = new HashMap<>();
        for (AuditLog logEntry : logs) {
            HealthStats stats = statsMap.computeIfAbsent(
                    logEntry.getTransno(), k -> new HealthStats());
            stats.callCount++;
            if ("0000".equals(logEntry.getResponseCode())) {
                stats.successCount++;
            } else {
                // 记录最近一次错误
                if (stats.lastErrorTime == null ||
                        logEntry.getCreatedTime() != null && logEntry.getCreatedTime().isAfter(stats.lastErrorTime)) {
                    stats.lastErrorTime = logEntry.getCreatedTime();
                    stats.lastErrorMessage = logEntry.getResponseCode();
                }
            }
            if (logEntry.getCostTime() != null) {
                stats.totalCostMs += logEntry.getCostTime();
            }
        }

        // 计算成功率和平均耗时
        for (HealthStats stats : statsMap.values()) {
            if (stats.callCount > 0) {
                stats.successRate = Math.round(stats.successCount * 10000.0 / stats.callCount) / 100.0;
                stats.avgCostMs = stats.totalCostMs / stats.callCount;
            }
        }

        return statsMap;
    }

    // ==================== 内部类 ====================

    /** 市场列表项 */
    @Data
    public static class MarketplaceItem {
        private Long id;
        private String transno;
        private String name;
        private String description;
        private Long systemId;
        private String systemName;
        private Integer status;
        private Integer currentVersion;
        private LocalDateTime updatedTime;
        private String createdBy;
        /** 标签列表，当前无标签表时统一返回空数组 */
        private List<String> tags = Collections.emptyList();
        // 健康指标
        private long callCount;
        private double successRate;
        private long avgCostMs;
        private LocalDateTime lastErrorTime;
        private String lastErrorMessage;
    }

    /** 内部聚合结构 */
    @Data
    private static class HealthStats {
        long callCount;
        long successCount;
        long totalCostMs;
        double successRate;
        long avgCostMs;
        LocalDateTime lastErrorTime;
        String lastErrorMessage;
    }
}
