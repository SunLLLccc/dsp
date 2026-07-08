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
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档检索器：在 {@code retrieval-sources.json} 的 {@code docs.sources} 范围内做关键词检索。
 * <p>
 * 支持 source.type = file / directory / glob。忽略不存在的路径（记录 debug 日志，不致整体失败）。
 * 安全防护（与源码检索一致）：
 * <ul>
 *   <li>路径解析走 {@link AssetSourceLoader#resolveProjectPath}，越界路径被拒绝</li>
 *   <li>复用 {@code ignore.sensitive}：application*.yml / *.env / *.key 等不读取（遍历 + 评分双重校验）</li>
 *   <li>超过 {@code ignore.maxFileSizeKB} 的文件跳过</li>
 * </ul>
 * 命中打分：路径命中加权 + 内容命中加权 + 多词命中加权（见 {@link KeywordSearchUtils}）。
 */
@Slf4j
@Component
public class DocRetriever {

    /** 文档支持的扩展名。 */
    private static final String DOC_EXTENSIONS = ".md,.html,.htm,.xml,.txt,.markdown,.adoc";

    private final AssetSourceLoader assetSourceLoader;

    public DocRetriever(AssetSourceLoader assetSourceLoader) {
        this.assetSourceLoader = assetSourceLoader;
    }

    /**
     * 检索文档。
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

        for (Source source : config.getDocs().getSources()) {
            List<Path> candidates = collectCandidateFiles(source, ignore);
            for (Path file : candidates) {
                ScoredFile sf = scoreFile(file, source, tokens, ignore, maxBytes, projectRoot);
                if (sf != null && sf.score > 0) {
                    scored.add(sf);
                }
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream().limit(topK).map(ScoredFile::toCitation).collect(Collectors.toList());
    }

    private List<Path> collectCandidateFiles(Source source, Ignore ignore) {
        List<Path> files = new ArrayList<>();
        String sp = source.getPath();
        if (sp == null || sp.isBlank()) {
            return files;
        }
        String type = source.getType() == null ? "file" : source.getType();
        switch (type) {
            case "directory" -> {
                // 统一安全路径解析：越界返回 null
                Path dir = assetSourceLoader.resolveProjectPath(sp);
                if (dir == null || !Files.isDirectory(dir)) {
                    log.debug("文档 source 目录不存在或越界，跳过：{}", sp);
                    return files;
                }
                try {
                    Files.walkFileTree(dir, EnumSet.noneOf(FileVisitOption.class), 5,
                            new DocCollector(ignore, files));
                } catch (IOException e) {
                    log.warn("遍历文档目录失败：{}, err={}", dir, e.getMessage());
                }
            }
            case "glob" -> {
                Path resolved = assetSourceLoader.resolveProjectPath(sp);
                if (resolved == null) {
                    return files;
                }
                Path parent = resolved.getParent();
                String pattern = resolved.getFileName().toString();
                if (parent != null && Files.isDirectory(parent)) {
                    PathMatcher matcher = parent.getFileSystem().getPathMatcher("glob:" + pattern);
                    try {
                        Files.list(parent)
                                .filter(p -> Files.isRegularFile(p) && matcher.matches(p.getFileName()))
                                .forEach(f -> maybeCollect(f, ignore, files));
                    } catch (IOException e) {
                        log.warn("glob 匹配文档失败：{}, err={}", sp, e.getMessage());
                    }
                }
            }
            default -> {
                Path f = assetSourceLoader.resolveProjectPath(sp);
                if (f != null && Files.isRegularFile(f)) {
                    maybeCollect(f, ignore, files);
                } else {
                    log.debug("文档 source 文件不存在或越界，跳过：{}", sp);
                }
            }
        }
        return files;
    }

    /** 收集前先过 sensitive 与扩展名过滤（第一道校验）。 */
    private void maybeCollect(Path file, Ignore ignore, List<Path> out) {
        String name = file.getFileName().toString();
        if (SourceCodeRetriever.isSensitive(name, ignore)) {
            log.debug("文档检索跳过敏感文件（遍历阶段）：{}", name);
            return;
        }
        if (!isDocFile(file)) {
            return;
        }
        out.add(file);
    }

    private ScoredFile scoreFile(Path file, Source source, List<String> tokens,
                                 Ignore ignore, int maxBytes, Path projectRoot) {
        String relPath = projectRoot.relativize(file).toString().replace('\\', '/');
        // 敏感文件二次校验（第二道，真正双重校验）
        if (SourceCodeRetriever.isSensitive(relPath, ignore)) {
            log.debug("文档检索跳过敏感文件（评分阶段）：{}", relPath);
            return null;
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return null;
        }
        if (size > maxBytes) {
            log.debug("文档检索跳过大文件（{}KB > {}KB）：{}", size / 1024, maxBytes / 1024, relPath);
            return null;
        }
        try {
            String content = Files.readString(file);
            // 路径/文件名命中加权（×3）
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
            String title = source.getTitle() != null ? source.getTitle() : file.getFileName().toString();
            return new ScoredFile(relPath, title, snippet.getText(), total,
                    snippet.getLineStart(), snippet.getLineEnd());
        } catch (IOException e) {
            log.debug("读取文档失败：{}", relPath);
            return null;
        }
    }

    private boolean isDocFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        for (String ext : DOC_EXTENSIONS.split(",")) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** 遍历目录收集文档文件，执行 sensitive/扩展名/忽略目录过滤。 */
    private static class DocCollector extends SimpleFileVisitor<Path> {
        private final Ignore ignore;
        private final List<Path> out;

        DocCollector(Ignore ignore, List<Path> out) {
            this.ignore = ignore;
            this.out = out;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) {
                // sensitive + 扩展名过滤在 maybeCollect 中统一处理
                String name = file.getFileName().toString();
                if (!SourceCodeRetriever.isSensitive(name, ignore) && hasDocExtension(name)) {
                    out.add(file);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String name = dir.getFileName().toString();
            if (name.equals(".git") || name.equals("node_modules") || name.equals("target")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        private boolean hasDocExtension(String name) {
            String lower = name.toLowerCase();
            for (String ext : DOC_EXTENSIONS.split(",")) {
                if (lower.endsWith(ext)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ScoredFile {
        final String path;
        final String title;
        final String snippet;
        final double score;
        final int lineStart;
        final int lineEnd;

        ScoredFile(String path, String title, String snippet, double score, int lineStart, int lineEnd) {
            this.path = path;
            this.title = title;
            this.snippet = snippet;
            this.score = score;
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
        }

        RetrievalCitation toCitation() {
            return RetrievalCitation.doc(path, title, snippet, score, lineStart, lineEnd);
        }
    }
}
