package com.sunlc.dsp.admin.assistant.template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模板选择器（Text2API T2）。
 * <p>
 * 基于 SQL 特征（数量/依赖/分页/动态条件）从 {@code template-index.json} 选择模板。
 * 一期只选 {@code appliesTo} 含 {@code sql} 的模板；非 SQL 模板不被选中。
 * 无匹配时返回明确「不支持，需人工扩展模板」，AI 不自由编 XML。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSelector {

    private final Text2ApiAssetLoader assetLoader;

    /**
     * 选择模板。
     *
     * @param sqlCount       SQL 段数量（1=单查询，>1=多查询）
     * @param hasDependency  是否存在 SQL 依赖（后续查询用前序结果）
     * @param hasPagination  是否需要分页（cursor/optimized）
     * @param hasDynamic     是否有动态条件（if/foreach）
     * @return 模板选择结果
     */
    public TemplateSelectionResult select(int sqlCount, boolean hasDependency,
                                          boolean hasPagination, boolean hasDynamic) {
        List<TemplateIndexConfig.TemplateEntry> templates = assetLoader.getConfig().getTemplates();

        // 一期只选 SQL 类模板
        List<TemplateIndexConfig.TemplateEntry> sqlTemplates = templates.stream()
                .filter(t -> t.getAppliesTo().contains("sql"))
                .toList();

        if (sqlTemplates.isEmpty()) {
            return TemplateSelectionResult.unmatched("模板索引中无 SQL 类模板");
        }

        // 按特征优先级匹配
        TemplateIndexConfig.TemplateEntry best = null;
        String reason = null;

        if (sqlCount > 1 && hasDependency) {
            // 多查询 + 依赖 → 10-dependency-orchestration
            best = findById(sqlTemplates, "10");
            reason = "多查询存在依赖关系，使用依赖编排模板";
        } else if (sqlCount > 1) {
            // 多查询无依赖 → 09-parallel-orchestration
            best = findById(sqlTemplates, "09");
            reason = "多查询无依赖，使用并行编排模板";
        } else if (hasDynamic && hasPagination) {
            // 动态条件 + 分页 → 一期优先 02（动态 SQL），由 XML 生成阶段/用户复核补分页或提示需人工扩展
            best = findById(sqlTemplates, "02");
            reason = "动态条件查询（含分页），一期优先动态 SQL 模板，分页可在复核阶段补充或提示人工扩展";
        } else if (hasDynamic) {
            // 仅动态条件 → 02-dynamic-sql-query
            best = findById(sqlTemplates, "02");
            reason = "多条件可选筛选，使用动态 SQL 模板";
        } else if (hasPagination) {
            // 仅分页 → 03-cursor（一期默认游标，如为 PC 页码分页可改选 04）
            best = findById(sqlTemplates, "03");
            reason = "需要分页，使用游标分页模板（适合移动端/大数据量）；如为 PC 端页码分页，可改选 04-optimized-pagination";
        } else {
            // 默认 → 01-simple-sql-query
            best = findById(sqlTemplates, "01");
            reason = "简单单表查询，使用基础 SQL 模板";
        }

        if (best == null) {
            return TemplateSelectionResult.unmatched(
                    "现有 SQL 模板无法覆盖该场景（sqlCount=" + sqlCount
                            + ", dependency=" + hasDependency
                            + ", pagination=" + hasPagination
                            + ", dynamic=" + hasDynamic + "），需人工扩展模板");
        }

        log.debug("模板选择: templateId={}, reason={}", best.getId(), reason);
        return TemplateSelectionResult.matched(best, reason);
    }

    private TemplateIndexConfig.TemplateEntry findById(
            List<TemplateIndexConfig.TemplateEntry> templates, String id) {
        return templates.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);
    }
}
