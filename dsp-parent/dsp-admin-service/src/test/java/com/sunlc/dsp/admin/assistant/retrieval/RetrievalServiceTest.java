package com.sunlc.dsp.admin.assistant.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RetrievalService 编排单测。mock DocRetriever/SourceCodeRetriever，验证：
 * 项目相关判断、文档优先、文档不足触发源码兜底、非项目不检索、citationsJson 可解析、context 受控。
 */
class RetrievalServiceTest {

    private DocRetriever docRetriever;
    private SourceCodeRetriever sourceCodeRetriever;
    private RetrievalService service;

    @BeforeEach
    void setUp() {
        docRetriever = mock(DocRetriever.class);
        sourceCodeRetriever = mock(SourceCodeRetriever.class);
        service = new RetrievalService(docRetriever, sourceCodeRetriever);
    }

    @Test
    void projectRelatedQuestion_triggersDocRetrieval() {
        when(docRetriever.retrieve(anyList(), anyInt())).thenReturn(List.of(
                RetrievalCitation.doc("docs/x.md", "X", "snippet", 10, 1, 5)
        ));
        when(sourceCodeRetriever.retrieve(anyList(), anyInt())).thenReturn(Collections.emptyList());

        RetrievalResult result = service.retrieve("XML DSL 数据源怎么配置");

        assertTrue(result.isProjectRelated());
        assertFalse(result.getRetrievalContext().isEmpty());
        assertFalse(result.getCitations().isEmpty());
        verify(docRetriever, times(1)).retrieve(anyList(), anyInt());
    }

    @Test
    void docInsufficient_triggersSourceFallback() {
        // 文档命中数 <= 阈值（1），应触发源码兜底
        when(docRetriever.retrieve(anyList(), anyInt())).thenReturn(List.of(
                RetrievalCitation.doc("docs/x.md", "X", "snippet", 5, 1, 3)
        ));
        when(sourceCodeRetriever.retrieve(anyList(), anyInt())).thenReturn(List.of(
                RetrievalCitation.source("dsp/AssistantController.java", "AssistantController", "snippet", 8, 10, 20)
        ));

        RetrievalResult result = service.retrieve("AssistantController 的 streamEvents 实现");

        assertTrue(result.isProjectRelated());
        verify(sourceCodeRetriever, times(1)).retrieve(anyList(), anyInt());
        assertTrue(result.getCitations().stream().anyMatch(c -> "source".equals(c.getType())));
    }

    @Test
    void docSufficient_skipsSourceFallback() {
        // 文档命中数 > 阈值，不应触发源码兜底
        when(docRetriever.retrieve(anyList(), anyInt())).thenReturn(List.of(
                RetrievalCitation.doc("docs/a.md", "A", "s1", 5, 1, 3),
                RetrievalCitation.doc("docs/b.md", "B", "s2", 4, 1, 3)
        ));

        RetrievalResult result = service.retrieve("mybatis 分页 template 配置");

        assertTrue(result.isProjectRelated());
        verify(sourceCodeRetriever, never()).retrieve(anyList(), anyInt());
    }

    @Test
    void nonProjectQuestion_doesNotTriggerLocalRetrieval() {
        RetrievalResult result = service.retrieve("今天天气怎么样");

        assertFalse(result.isProjectRelated());
        assertEquals("[]", result.getCitationsJson());
        verify(docRetriever, never()).retrieve(anyList(), anyInt());
        verify(sourceCodeRetriever, never()).retrieve(anyList(), anyInt());
    }

    @Test
    void citationsJsonIsParseable() throws Exception {
        when(docRetriever.retrieve(anyList(), anyInt())).thenReturn(List.of(
                RetrievalCitation.doc("docs/x.md", "X", "snippet", 10, 1, 5)
        ));

        RetrievalResult result = service.retrieve("datasource sql 接口");

        ObjectMapper om = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = om.readTree(result.getCitationsJson());
        assertTrue(node.isArray(), "citationsJson 应是合法 JSON 数组");
        assertEquals(1, node.size());
        assertEquals("doc", node.get(0).get("type").asText());
    }

    @Test
    void contextContainsPathAndSnippet() {
        when(docRetriever.retrieve(anyList(), anyInt())).thenReturn(List.of(
                RetrievalCitation.doc("docs/x.md", "X 标题", "这里是命中片段", 10, 5, 10)
        ));

        RetrievalResult result = service.retrieve("xml dsl 接口");

        String ctx = result.getRetrievalContext();
        assertTrue(ctx.contains("docs/x.md"), "context 应含路径");
        assertTrue(ctx.contains("这里是命中片段"), "context 应含片段");
        assertTrue(ctx.contains("5-10"), "context 应含行号");
    }
}
