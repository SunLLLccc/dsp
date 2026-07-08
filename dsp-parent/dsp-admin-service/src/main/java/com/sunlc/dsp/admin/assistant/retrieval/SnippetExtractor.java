package com.sunlc.dsp.admin.assistant.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从文本中抽取命中关键词的片段（命中行 + 前后若干行），用于 snippet 展示。
 * 纯静态工具，便于单测。
 */
public final class SnippetExtractor {

    /** 单片段最大字符数。 */
    public static final int MAX_SNIPPET_CHARS = 1200;
    /** 命中行前后各保留的行数。 */
    private static final int CONTEXT_LINES = 6;

    private SnippetExtractor() {
    }

    /**
     * 抽取第一个命中 token 所在位置附近的片段。
     *
     * @param content 文件全文
     * @param tokens  拆分后的关键词
     * @return 片段（行号信息封装在 result 中）；无命中返回 null
     */
    public static Snippet extract(String content, List<String> tokens) {
        if (content == null || content.isEmpty() || tokens == null || tokens.isEmpty()) {
            return null;
        }
        List<String> lines = content.lines().collect(java.util.stream.Collectors.toList());
        if (lines.isEmpty()) {
            return null;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        // 找第一个命中的 token 的字符偏移
        int firstHitOffset = -1;
        for (String t : tokens) {
            int idx = lower.indexOf(t.toLowerCase(Locale.ROOT));
            if (idx >= 0 && (firstHitOffset < 0 || idx < firstHitOffset)) {
                firstHitOffset = idx;
            }
        }
        if (firstHitOffset < 0) {
            return null;
        }
        // 计算命中所在行
        int hitLine = offsetToLine(content, firstHitOffset);
        int startLine = Math.max(1, hitLine - CONTEXT_LINES);
        int endLine = Math.min(lines.size(), hitLine + CONTEXT_LINES);
        StringBuilder sb = new StringBuilder();
        for (int i = startLine - 1; i < endLine; i++) {
            sb.append(lines.get(i)).append('\n');
            if (sb.length() > MAX_SNIPPET_CHARS) {
                break;
            }
        }
        String snippet = sb.length() > MAX_SNIPPET_CHARS
                ? sb.substring(0, MAX_SNIPPET_CHARS) + "..."
                : sb.toString().stripTrailing();
        return new Snippet(snippet, startLine, endLine);
    }

    private static int offsetToLine(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 片段结果。 */
    public static final class Snippet {
        private final String text;
        private final int lineStart;
        private final int lineEnd;

        public Snippet(String text, int lineStart, int lineEnd) {
            this.text = text;
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
        }

        public String getText() { return text; }
        public int getLineStart() { return lineStart; }
        public int getLineEnd() { return lineEnd; }
    }
}
