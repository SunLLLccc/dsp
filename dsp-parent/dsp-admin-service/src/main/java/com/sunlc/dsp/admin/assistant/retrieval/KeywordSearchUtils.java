package com.sunlc.dsp.admin.assistant.retrieval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 关键词检索工具：query 规范化、拆词、打分。
 * <p>
 * 策略（一期轻量、可解释）：
 * <ul>
 *   <li>query 规范化：转小写、去多余空白</li>
 *   <li>拆词：按空格/路径分隔符/驼峰/下划线/短横线拆；保留中文连续片段</li>
 *   <li>文件名/路径命中加权 > 内容命中加权 > 多词命中加权</li>
 * </ul>
 * 无外部依赖，便于单测。
 */
public final class KeywordSearchUtils {

    /** 路径/符号分隔符，用于拆词。 */
    private static final String SEPARATORS = "/\\.;,()[]{}<>:\"'";

    /** 拆词用的分隔符正则（已转义，含空白、分隔符、下划线、短横线）。 */
    private static final String SEPARATOR_REGEX =
            "[\\s" + Pattern.quote(SEPARATORS) + "_\\-]+";

    private KeywordSearchUtils() {
    }

    /** 规范化 query：转小写、合并空白。 */
    public static String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * 拆词：按空格、路径/符号分隔符、驼峰、下划线、短横线拆；保留中文连续片段。
     * 单字符且无意义（如单个 / .）的 token 被丢弃。
     */
    public static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        // 仅合并空白、保留原始大小写，以便驼峰拆分识别大小写边界
        String collapsed = query.trim().replaceAll("\\s+", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : collapsed.split(SEPARATOR_REGEX)) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            // 驼峰拆分：camelCase → camel, case（依赖原始大小写）
            for (String sub : splitCamelCase(raw)) {
                if (sub.length() >= 2) {
                    tokens.add(sub.toLowerCase(Locale.ROOT));
                }
            }
            // 原始 token（未拆驼峰）也保留，便于整词匹配
            if (raw.length() >= 2) {
                tokens.add(raw.toLowerCase(Locale.ROOT));
            }
        }
        // 过滤过短（<2）和无意义停用词
        tokens.removeIf(t -> t.length() < 2 || STOPWORDS.contains(t));
        return new ArrayList<>(tokens);
    }

    /** 驼峰拆分：保留中文连续片段，英文按大小写边界拆。 */
    private static List<String> splitCamelCase(String s) {
        List<String> result = new ArrayList<>();
        // 中文连续片段
        StringBuilder cjk = new StringBuilder();
        StringBuilder eng = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) {
                if (eng.length() > 0) {
                    result.addAll(splitEnglish(eng.toString()));
                    eng.setLength(0);
                }
                cjk.append(c);
            } else {
                if (cjk.length() > 0) {
                    result.add(cjk.toString());
                    cjk.setLength(0);
                }
                eng.append(c);
            }
        }
        if (cjk.length() > 0) {
            result.add(cjk.toString());
        }
        if (eng.length() > 0) {
            result.addAll(splitEnglish(eng.toString()));
        }
        return result;
    }

    private static List<String> splitEnglish(String s) {
        // camelCase / PascalCase：在大小写边界拆
        String[] parts = s.split("(?<=[a-z0-9])(?=[A-Z])");
        return Arrays.asList(parts);
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    /**
     * 计算单个 token 在文本中的命中得分（大小写不敏感）。
     * 出现一次得 1 分，最多累计 maxHits 次避免长文档刷分。
     */
    public static double termFrequency(String text, String token, int maxHits) {
        if (text == null || text.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String lowerToken = token.toLowerCase(Locale.ROOT);
        double score = 0;
        int idx = 0;
        int count = 0;
        while (count < maxHits) {
            int found = lower.indexOf(lowerToken, idx);
            if (found < 0) {
                break;
            }
            score += 1;
            idx = found + lowerToken.length();
            count++;
        }
        return score;
    }

    /**
     * 汇总多 token 在文本中的命中得分：每命中一个 token 得分翻倍（多词命中加权）。
     */
    public static double aggregateScore(List<String> tokens, String text, int maxHitsPerToken) {
        if (tokens == null || tokens.isEmpty() || text == null) {
            return 0;
        }
        Map<String, Double> perToken = new LinkedHashMap<>();
        int hitTokens = 0;
        for (String t : tokens) {
            double tf = termFrequency(text, t, maxHitsPerToken);
            perToken.put(t, tf);
            if (tf > 0) {
                hitTokens++;
            }
        }
        double base = perToken.values().stream().mapToDouble(Double::doubleValue).sum();
        // 多词命中加权：每多命中一个不同 token，得分 ×1.5
        double multiplier = 1.0;
        for (int i = 1; i < hitTokens; i++) {
            multiplier *= 1.5;
        }
        return base * multiplier;
    }

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "with", "that", "this", "from", "how", "what", "why",
            "怎么", "如何", "什么", "为什么", "的", "了", "在", "是", "和", "与", "请",
            "一下", "可以", "吗", "呢"));
}
