package com.sunlc.dsp.admin.assistant.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * TemplateSelector 单测。
 * mock Text2ApiAssetLoader 返回构造的模板索引，验证各特征组合选择正确模板。
 */
@ExtendWith(MockitoExtension.class)
class TemplateSelectorTest {

    @Mock private Text2ApiAssetLoader assetLoader;
    private TemplateSelector selector;

    @BeforeEach
    void setUp() {
        selector = new TemplateSelector(assetLoader);
        when(assetLoader.getConfig()).thenReturn(buildTestIndex());
    }

    @Test
    void select_singleSimpleSql_returns01() {
        TemplateSelectionResult r = selector.select(1, false, false, false);
        assertTrue(r.isMatched());
        assertEquals("01", r.getTemplateId());
        assertTrue(r.getSelectionReason().contains("简单"));
    }

    @Test
    void select_dynamicConditions_returns02() {
        TemplateSelectionResult r = selector.select(1, false, false, true);
        assertTrue(r.isMatched());
        assertEquals("02", r.getTemplateId());
    }

    @Test
    void select_cursorPagination_returns03() {
        TemplateSelectionResult r = selector.select(1, false, true, false);
        assertTrue(r.isMatched());
        assertEquals("03", r.getTemplateId());
    }

    @Test
    void select_multiQueryNoDependency_returns09() {
        TemplateSelectionResult r = selector.select(3, false, false, false);
        assertTrue(r.isMatched());
        assertEquals("09", r.getTemplateId());
    }

    @Test
    void select_multiQueryWithDependency_returns10() {
        TemplateSelectionResult r = selector.select(2, true, false, false);
        assertTrue(r.isMatched());
        assertEquals("10", r.getTemplateId());
    }

    @Test
    void select_onlySqlTemplatesConsidered() {
        // 模板索引含非 SQL 模板（05-http），不应被选中
        TemplateSelectionResult r = selector.select(1, false, false, false);
        assertTrue(r.isMatched());
        assertEquals("01", r.getTemplateId());
        // 验证选的是 SQL 类，不是 HTTP/Dubbo/Mongo
        assertFalse(r.getTemplateFile().contains("http"));
    }

    @Test
    void select_dynamicWithPagination_prefersDynamicTemplate() {
        // 动态+分页 → 02（一期简化，动态优先）
        TemplateSelectionResult r = selector.select(1, false, true, true);
        assertTrue(r.isMatched());
        assertEquals("02", r.getTemplateId());
    }

    @Test
    void select_matchedResultContainsFillHintsAndReviewPoints() {
        TemplateSelectionResult r = selector.select(1, false, false, false);
        assertTrue(r.isMatched());
        assertTrue(r.getFillHints().size() > 0);
        assertTrue(r.getReviewPoints().size() > 0);
    }

    @Test
    void select_emptyIndexReturnsUnmatched() {
        TemplateIndexConfig empty = new TemplateIndexConfig();
        empty.setTemplates(Collections.emptyList());
        when(assetLoader.getConfig()).thenReturn(empty);

        TemplateSelectionResult r = selector.select(1, false, false, false);
        assertFalse(r.isMatched());
        assertTrue(r.getUnmatchedMessage().contains("无 SQL 类模板"));
    }

    @Test
    void select_missingTemplateIdReturnsUnmatched() {
        // 模板索引有 SQL 类但缺 id=01（模拟索引不完整）
        TemplateIndexConfig partial = new TemplateIndexConfig();
        TemplateIndexConfig.TemplateEntry onlyMulti = entry("09", "template/09.xml", "sql",
                Arrays.asList("多查询并行"));
        partial.setTemplates(Collections.singletonList(onlyMulti));
        when(assetLoader.getConfig()).thenReturn(partial);

        // 单查询找不到 01 → unmatched
        TemplateSelectionResult r = selector.select(1, false, false, false);
        assertFalse(r.isMatched());
        assertTrue(r.getUnmatchedMessage().contains("需人工扩展模板"));
    }

    /** 构造测试模板索引（含 SQL + 非 SQL 模板）。 */
    private TemplateIndexConfig buildTestIndex() {
        TemplateIndexConfig config = new TemplateIndexConfig();
        config.setTemplates(Arrays.asList(
                entry("01", "template/01-simple-sql-query.xml", "sql",
                        Arrays.asList("单数据源、单 SQL、无动态条件、无分页")),
                entry("02", "template/02-dynamic-sql-query.xml", "sql",
                        Arrays.asList("条件可选", "foreach IN")),
                entry("03", "template/03-cursor-pagination.xml", "sql",
                        Arrays.asList("游标分页")),
                entry("04", "template/04-optimized-pagination.xml", "sql",
                        Arrays.asList("优化分页")),
                entry("09", "template/09-parallel-orchestration.xml", "sql",
                        Arrays.asList("多查询并行")),
                entry("10", "template/10-dependency-orchestration.xml", "sql",
                        Arrays.asList("多查询依赖")),
                // 非 SQL 模板（不应被选中）
                entry("05", "template/05-http-query.xml", "http",
                        Arrays.asList("HTTP GET")),
                entry("07", "template/07-dubbo-query.xml", "dubbo",
                        Arrays.asList("Dubbo 泛化"))));
        return config;
    }

    private TemplateIndexConfig.TemplateEntry entry(String id, String file, String appliesTo,
                                                     java.util.List<String> signals) {
        TemplateIndexConfig.TemplateEntry e = new TemplateIndexConfig.TemplateEntry();
        e.setId(id);
        e.setFile(file);
        e.setAppliesTo(Collections.singletonList(appliesTo));
        e.setSelectionSignals(signals);
        e.setRequiresUserConfirmation(Collections.singletonList("SQL 文本"));
        return e;
    }
}
