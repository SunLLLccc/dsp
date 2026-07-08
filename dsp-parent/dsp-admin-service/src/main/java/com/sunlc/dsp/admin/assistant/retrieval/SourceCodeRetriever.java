package com.sunlc.dsp.admin.assistant.retrieval;

import com.sunlc.dsp.admin.assistant.retrieval.RetrievalSourcesConfig.Ignore;
import com.sunlc.dsp.admin.assistant.retrieval.RetrievalSourcesConfig.Source;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 源码兜底检索器。仅在文档检索结果不足时由 {@link RetrievalService} 调用。
 * <p>
 * 安全约束（严格执行）：
 * <ul>
 *   <li>路径解析全部走 {@link AssetSourceLoader#resolveProjectGlobPaths}，越界路径被剔除</li>
 *   <li>{@code ignore.paths}（target/node_modules 等）整目录跳过</li>
 *   <li>{@code ignore.filePatterns}（.class/.jar 等）文件跳过</li>
 *   <li>{@code ignore.sensitive}（application*.yml/.env/*.key 等）在遍历阶段与评分阶段双重校验，
 *       内容绝不进入检索与引用</li>
 *   <li>超过 {@code ignore.maxFileSizeKB} 的文件跳过</li>
 * </ul>
 */
@Slf4j
@Component
public class SourceCodeRetriever {

    /** 源码支持的扩展名（仅索引文本类源码）。 */
    private static final String SOURCE_EXTENSIONS =
            ".java,.kt,.scala,.js,.ts,.jsx,.tsx,.vue,.py,.go,.rs,.c,.cpp,.h,.hpp,.cs,.rb,.php";

    private final AssetSourceLoader assetSourceLoader;

    public SourceCodeRetriever(AssetSourceLoader assetSourceLoader) {
        this.assetSourceLoader = assetSourceLoader;
    }

    /**
     * 源码兜底检索。
     *
     * @param tokens 已拆分的关键词
     * @param topK   返回的最大片段数
     */
    public List<RetrievalCitation> retrieve(List<String> tokens, int topK) {
        if (tokens == null || tokens.isEmpty() || topK <= 0) {
            return new ArrayList<>();
        }
        RetrievalSourcesConfig config = assetSourceLoader.getConfig();
        Ignore ignore = config.getSourceCodeFallback().getIgnore();
        int maxBytes = ignore.getMaxFileSizeKB() * 1024;
        Path projectRoot = assetSourceLoader.getProjectRoot();

        List<ScoredFile> scored = new ArrayList<>();
        for (Source whitelist : config.getSourceCodeFallback().getWhitelist()) {
            // 通过统一安全方法展开 glob（含单层 * 展开为所有匹配目录）
            List<Path> roots = assetSourceLoader.resolveProjectGlobPaths(whitelist.getPath());
            if (roots.isEmpty()) {
                log.debug("源码 whitelist 无有效目录，跳过：{}", whitelist.getPath());
                continue;
            }
            List<Path> candidates = new ArrayList<>();
            for (Path root : roots) {
                collectSourceFiles(root, ignore, candidates);
            }
            for (Path file : candidates) {
                ScoredFile sf = scoreFile(file, tokens, ignore, maxBytes, projectRoot);
                if (sf != null && sf.score > 0) {
                    scored.add(sf);
                }
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream().limit(topK).map(ScoredFile::toCitation).collect(Collectors.toList());
    }

    private void collectSourceFiles(Path root, Ignore ignore, List<Path> out) {
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 10,
                    new SourceCollector(root, ignore, out));
        } catch (IOException e) {
            log.warn("收集源码候选失败：root={}, err={}", root, e.getMessage());
        }
    }

    private ScoredFile scoreFile(Path file, List<String> tokens, Ignore ignore,
                                 int maxBytes, Path projectRoot) {
        String relPath = projectRoot.relativize(file).toString().replace('\\', '/');
        if (!isSourceFile(file)) {
            return null;
        }
        // 敏感文件二次校验（第一道在遍历阶段，这里是第二道，真正双重校验）
        if (isSensitive(relPath, ignore)) {
            log.debug("源码检索跳过敏感文件（评分阶段）：{}", relPath);
            return null;
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return null;
        }
        if (size > maxBytes) {
            log.debug("源码检索跳过大文件（{}KB > {}KB）：{}", size / 1024, maxBytes / 1024, relPath);
            return null;
        }
        try {
            String content = Files.readString(file);
            double pathScore = KeywordSearchUtils.aggregateScore(tokens, relPath, 3) * 3.0;
            double contentScore = KeywordSearchUtils.aggregateScore(tokens, content, 5);
            double total = pathScore + contentScore;
            if (total <= 0) {
                return null;
            }
            SnippetExtractor.Snippet snippet = SnippetExtractor.extract(content, tokens);
            if (snippet == null) {
                return null;
            }
            String symbol = inferSymbol(file, content);
            return new ScoredFile(relPath, symbol, snippet.getText(), total,
                    snippet.getLineStart(), snippet.getLineEnd());
        } catch (IOException e) {
            log.debug("读取源码失败：{}", relPath);
            return null;
        }
    }

    /** 简单符号识别：Java 取 package + 主类名；否则取文件名。 */
    private String inferSymbol(Path file, String content) {
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) {
            String pkg = "";
            String cls = name.substring(0, name.length() - 5);
            for (String line : content.lines().toList()) {
                String trimmed = line.trim();
                if (trimmed.startsWith("package ")) {
                    pkg = trimmed.replaceAll("package\\s+", "").replace(";", "").trim();
                    break;
                }
            }
            return pkg.isEmpty() ? cls : pkg + "." + cls;
        }
        return name;
    }

    private boolean isSourceFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        for (String ext : SOURCE_EXTENSIONS.split(",")) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** 敏感文件判断（支持含 * 的简单通配）。 */
    static boolean isSensitive(String relPath, Ignore ignore) {
        String lower = relPath.toLowerCase();
        for (String pattern : ignore.getSensitive()) {
            String p = pattern.toLowerCase();
            if (p.contains("*")) {
                String regex = p.replace(".", "\\.").replace("*", ".*");
                if (lower.matches(regex)) {
                    return true;
                }
            } else if (lower.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** 遍历源码目录，执行 ignore.paths / filePatterns / sensitive 三道跳过。 */
    private static class SourceCollector extends SimpleFileVisitor<Path> {
        private final Ignore ignore;
        private final List<Path> out;

        SourceCollector(Path base, Ignore ignore, List<Path> out) {
            this.ignore = ignore;
            this.out = out;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String dirName = dir.getFileName().toString();
            // ignore.paths（**/target/ 等）：目录名匹配则跳过整个子树
            for (String pattern : ignore.getPaths()) {
                String normalized = pattern.replace("**/", "").replace("/", "");
                if (dirName.equals(normalized)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!attrs.isRegularFile()) {
                return FileVisitResult.CONTINUE;
            }
            String name = file.getFileName().toString();
            // 第一道：filePatterns（*.class 等）
            for (String pattern : ignore.getFilePatterns()) {
                String suffix = pattern.replace("*", "");
                if (name.endsWith(suffix)) {
                    return FileVisitResult.CONTINUE;
                }
            }
            // 第一道：sensitive（application*.yml 等）—— 真正的双重校验之前置
            String relForSensitive = name; // 敏感判断用文件名即可命中 application.yml 等
            if (isSensitive(relForSensitive, ignore)) {
                return FileVisitResult.CONTINUE;
            }
            out.add(file);
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class ScoredFile {
        final String path;
        final String symbol;
        final String snippet;
        final double score;
        final int lineStart;
        final int lineEnd;

        ScoredFile(String path, String symbol, String snippet, double score, int lineStart, int lineEnd) {
            this.path = path;
            this.symbol = symbol;
            this.snippet = snippet;
            this.score = score;
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
        }

        RetrievalCitation toCitation() {
            return RetrievalCitation.source(path, symbol, snippet, score, lineStart, lineEnd);
        }
    }
}
