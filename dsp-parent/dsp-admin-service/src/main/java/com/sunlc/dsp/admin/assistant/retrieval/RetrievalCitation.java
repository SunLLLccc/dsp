package com.sunlc.dsp.admin.assistant.retrieval;

/**
 * 单条引用来源（文档片段或代码片段），用于 citations JSON 输出与前端展示。
 * <p>
 * 字段与 {@code ai-assets/retrieval-sources.json} 的 citationRules 对齐：
 * 文档展示 path/title；源码展示 path/className/methodName。
 */
public class RetrievalCitation {

    /** 来源类型：doc / source */
    private final String type;
    /** 项目相对路径 */
    private final String path;
    /** 标题（文档）或符号名（类名/方法名，源码如能识别） */
    private final String title;
    /** 命中片段摘要（已截断，避免过长） */
    private final String snippet;
    /** 相关度得分，越高越相关 */
    private final double score;
    /** 片段起始行（1-based），可空 */
    private final Integer lineStart;
    /** 片段结束行（1-based），可空 */
    private final Integer lineEnd;

    public RetrievalCitation(String type, String path, String title, String snippet,
                             double score, Integer lineStart, Integer lineEnd) {
        this.type = type;
        this.path = path;
        this.title = title;
        this.snippet = snippet;
        this.score = score;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
    }

    public static RetrievalCitation doc(String path, String title, String snippet,
                                        double score, Integer lineStart, Integer lineEnd) {
        return new RetrievalCitation(SourceType.DOC.getCode(), path, title, snippet, score, lineStart, lineEnd);
    }

    public static RetrievalCitation source(String path, String symbol, String snippet,
                                           double score, Integer lineStart, Integer lineEnd) {
        return new RetrievalCitation(SourceType.SOURCE.getCode(), path, symbol, snippet, score, lineStart, lineEnd);
    }

    public String getType() { return type; }
    public String getPath() { return path; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public double getScore() { return score; }
    public Integer getLineStart() { return lineStart; }
    public Integer getLineEnd() { return lineEnd; }
}
