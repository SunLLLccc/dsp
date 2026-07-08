package com.sunlc.dsp.admin.assistant.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 加载并缓存 {@code retrieval-sources.json}，并提供统一的项目根目录安全路径解析。
 * <p>
 * 路径安全（核心职责，所有 retriever 必须通过本类解析路径）：
 * <ul>
 *   <li>{@code assetsPath} 默认 {@code ai-assets}（相对 {@code user.dir}）</li>
 *   <li>所有配置的检索路径（docs/sources、whitelist）经 {@link #resolveProjectPath} /
 *       {@link #resolveProjectGlobPaths} 解析，必须位于项目工作区内</li>
 *   <li>对存在的路径用 {@code toRealPath()} 解析符号链接，防止 symlink 指向项目外</li>
 *   <li>越界路径抛 {@link IllegalStateException}（加载期）或跳过并 warn（运行期）</li>
 * </ul>
 */
@Slf4j
@Component
public class AssetSourceLoader {

    private static final String CONFIG_FILE = "retrieval-sources.json";

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile RetrievalSourcesConfig cached;
    private volatile Path assetsRoot;
    private volatile Path projectRoot;
    private volatile Path projectRootReal;

    public AssetSourceLoader(AssistantProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void load() {
        this.projectRoot = Paths.get(System.getProperty("user.dir")).normalize();
        this.projectRootReal = toRealPathSafe(projectRoot);
        Path resolved = resolveAssetsRoot();
        validateWithinProject(resolved);
        this.assetsRoot = resolved;
        this.cached = parseConfig();
        validateConfigNotNull(cached);
        log.info("已加载检索资产配置：assetsRoot={}, version={}, docSources={}, whitelist={}",
                assetsRoot, cached.getVersion(),
                cached.getDocs().getSources().size(),
                cached.getSourceCodeFallback().getWhitelist().size());
    }

    /** 手动刷新。 */
    public void refresh() {
        load();
    }

    public RetrievalSourcesConfig getConfig() {
        if (cached == null) {
            load();
        }
        return cached;
    }

    public Path getAssetsRoot() {
        return assetsRoot;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 解析单个配置路径为项目内绝对路径，并做安全校验。
     * 越界路径返回 null（调用方应跳过并 warn，不读取）。
     *
     * @param configuredPath 配置中的相对/绝对路径
     * @return 校验通过的项目内路径；越界或解析失败返回 null
     */
    public Path resolveProjectPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path p = projectRoot.resolve(configuredPath).normalize();
        if (!isWithinProject(p)) {
            log.warn("配置路径越界，已拒绝：{}（解析为 {}）", configuredPath, p);
            return null;
        }
        return p;
    }

    /**
     * 解析含单层星号的 glob 路径，展开为项目内全部匹配目录。
     * 用于 whitelist 形如 dsp-parent 下所有模块的 src/main/java。
     * 越界的匹配项被剔除。
     *
     * @param configuredPath 含星号通配的路径
     * @return 所有匹配且位于项目内的目录路径（可能为空）
     */
    public List<Path> resolveProjectGlobPaths(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Collections.emptyList();
        }
        if (!configuredPath.contains("*")) {
            Path single = resolveProjectPath(configuredPath);
            return single == null ? Collections.emptyList() : Collections.singletonList(single);
        }
        String[] parts = configuredPath.split("/");
        List<Path> currents = new ArrayList<>();
        currents.add(projectRoot);
        for (String part : parts) {
            if ("*".equals(part)) {
                // 展开所有子目录
                List<Path> expanded = new ArrayList<>();
                for (Path current : currents) {
                    if (!Files.isDirectory(current)) {
                        continue;
                    }
                    try (Stream<Path> stream = Files.list(current)) {
                        stream.filter(Files::isDirectory).forEach(expanded::add);
                    } catch (IOException e) {
                        log.warn("展开 glob 子目录失败：{}", current, e);
                    }
                }
                currents = expanded;
            } else {
                List<Path> next = new ArrayList<>();
                for (Path current : currents) {
                    next.add(current.resolve(part));
                }
                currents = next;
            }
        }
        // 安全校验 + normalize + 仅保留存在的目录
        List<Path> result = new ArrayList<>();
        for (Path c : currents) {
            Path normalized = c.normalize();
            if (!isWithinProject(normalized)) {
                log.warn("glob 匹配路径越界，已剔除：{}", normalized);
                continue;
            }
            if (Files.isDirectory(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    /** 安全校验：normalized 路径必须在 projectRoot 下（同时检查 realPath 以防 symlink）。 */
    private boolean isWithinProject(Path path) {
        Path normalized = path.normalize();
        if (!normalized.startsWith(projectRoot)) {
            return false;
        }
        // 对存在的路径检查 realPath，防止 symlink 指向项目外
        if (Files.exists(normalized)) {
            Path real = toRealPathSafe(normalized);
            if (real != null && !real.startsWith(projectRootReal)) {
                log.warn("路径经 symlink 解析后越界：{} → {}", normalized, real);
                return false;
            }
        }
        return true;
    }

    private Path toRealPathSafe(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            // 路径不存在或无法解析时，返回 null，由调用方按 normalized 判断
            return null;
        }
    }

    private Path resolveAssetsRoot() {
        String configured = properties.getAssetsPath();
        if (configured == null || configured.isBlank()) {
            configured = "ai-assets";
        }
        Path p = Paths.get(configured);
        if (!p.isAbsolute()) {
            p = projectRoot.resolve(configured);
        }
        return p.normalize();
    }

    private void validateWithinProject(Path path) {
        if (!isWithinProject(path)) {
            throw new IllegalStateException(
                    "资产路径越界，必须位于项目工作区内：" + path + "（projectRoot=" + projectRoot + "）");
        }
    }

    private RetrievalSourcesConfig parseConfig() {
        Path configFile = assetsRoot.resolve(CONFIG_FILE);
        File file = configFile.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new IllegalStateException(
                    "检索资产配置文件不存在：" + configFile + "（assetsPath=" + assetsRoot + "）");
        }
        try {
            String content = Files.readString(file.toPath());
            return objectMapper.readValue(content, RetrievalSourcesConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("检索资产配置解析失败：" + configFile, e);
        }
    }

    /** 校验关键字段非空，避免下游 NPE。 */
    private void validateConfigNotNull(RetrievalSourcesConfig config) {
        if (config.getDocs() == null) {
            throw new IllegalStateException("retrieval-sources.json 缺少 docs 字段");
        }
        if (config.getSourceCodeFallback() == null) {
            throw new IllegalStateException("retrieval-sources.json 缺少 sourceCodeFallback 字段");
        }
        if (config.getSourceCodeFallback().getIgnore() == null) {
            throw new IllegalStateException("retrieval-sources.json 缺少 sourceCodeFallback.ignore 字段");
        }
    }
}
