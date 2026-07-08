package com.sunlc.dsp.admin.assistant.retrieval;

import java.util.Collections;
import java.util.List;

/**
 * 检索结果。由 {@link RetrievalService#retrieve} 返回，供 P4 业务编排使用。
 */
public class RetrievalResult {

    /** 是否项目相关问题（一期基于关键词规则判断）。 */
    private final boolean projectRelated;

    /** 给 AI prompt 的检索上下文（按 doc/source 分组的片段摘要）。非项目相关或无命中时为提示性短文本。 */
    private final String retrievalContext;

    /** citations JSON 字符串（供 P2 ChatRequest.citations 透传 → SSE citations 事件）。 */
    private final String citationsJson;

    /** 结构化引用列表（与 citationsJson 同源）。 */
    private final List<RetrievalCitation> citations;

    public RetrievalResult(boolean projectRelated, String retrievalContext,
                           String citationsJson, List<RetrievalCitation> citations) {
        this.projectRelated = projectRelated;
        this.retrievalContext = retrievalContext;
        this.citationsJson = citationsJson;
        this.citations = citations == null ? Collections.emptyList() : Collections.unmodifiableList(citations);
    }

    /** 非项目相关问题的空结果（不触发本地检索）。 */
    public static RetrievalResult notProjectRelated() {
        return new RetrievalResult(false, "", "[]", Collections.emptyList());
    }

    public boolean isProjectRelated() { return projectRelated; }
    public String getRetrievalContext() { return retrievalContext; }
    public String getCitationsJson() { return citationsJson; }
    public List<RetrievalCitation> getCitations() { return citations; }
}
