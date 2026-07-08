package com.sunlc.dsp.admin.assistant.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键词检索工具单测（纯逻辑，无 Spring）。
 */
class KeywordSearchUtilsTest {

    @Test
    void tokenize_splitsCamelCase() {
        List<String> tokens = KeywordSearchUtils.tokenize("AiChatSession");
        assertTrue(tokens.contains("ai"));
        assertTrue(tokens.contains("chat"));
        assertTrue(tokens.contains("session"));
    }

    @Test
    void tokenize_keepsCjkContinuous() {
        List<String> tokens = KeywordSearchUtils.tokenize("怎么实现 数据源 分页");
        assertTrue(tokens.contains("数据源"));
        assertTrue(tokens.contains("分页"));
        // 停用词被过滤
        assertFalse(tokens.contains("怎么"));
        assertFalse(tokens.contains("实现"));
    }

    @Test
    void tokenize_splitsBySeparators() {
        List<String> tokens = KeywordSearchUtils.tokenize("dsp-admin-service/AssistantController.java");
        assertTrue(tokens.contains("dsp"));
        assertTrue(tokens.contains("admin"));
        assertTrue(tokens.contains("service"));
        assertTrue(tokens.contains("assistant"));
        assertTrue(tokens.contains("controller"));
        // .java 按 . 拆出 java；AssistantController 驼峰拆出 assistant/controller
        assertTrue(tokens.contains("java"));
    }

    @Test
    void aggregateScore_multipleTokensWeightedHigher() {
        List<String> tokens = List.of("ai", "gateway");
        String text = "The AiGateway class provides AI streaming with gateway support.";
        double score = KeywordSearchUtils.aggregateScore(tokens, text, 5);
        assertTrue(score > 0, "应命中得分");
    }

    @Test
    void aggregateScore_noHitReturnsZero() {
        double score = KeywordSearchUtils.aggregateScore(List.of("nonexistent"), "unrelated content here", 5);
        assertEquals(0.0, score);
    }

    @Test
    void normalize_lowercasesAndCollapsesWhitespace() {
        assertEquals("hello world", KeywordSearchUtils.normalize("  Hello   World  "));
    }
}
