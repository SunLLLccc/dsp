package com.sunlc.dsp.admin.assistant.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Text2API 资产加载器（不复用 retrieval 的 AssetSourceLoader）。
 * 读取 {@code DSP_ASSISTANT_ASSETS_PATH/template-index.json}，
 * 复用同样的路径安全校验策略（normalize + projectRoot + toRealPath + symlink 防护）。
 */
@Slf4j
@Component
public class Text2ApiAssetLoader {

    private static final String TEMPLATE_INDEX_FILE = "template-index.json";

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile TemplateIndexConfig cached;
    private volatile Path assetsRoot;
    private volatile Path projectRoot;
    private volatile Path projectRootReal;

    public Text2ApiAssetLoader(AssistantProperties properties) {
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
        log.info("已加载模板索引：assetsRoot={}, version={}, templates={}",
                assetsRoot, cached.getVersion(), cached.getTemplates().size());
    }

    public void refresh() {
        load();
    }

    public TemplateIndexConfig getConfig() {
        if (cached == null) {
            load();
        }
        return cached;
    }

    public Path getAssetsRoot() {
        return assetsRoot;
    }

    // ===== 路径安全（normalize + projectRoot + toRealPath + symlink 防护）=====

    /** 安全校验：normalized 在 projectRoot 下，存在时 realPath 在 projectRootReal 下。 */
    private boolean isWithinProject(Path path) {
        Path normalized = path.normalize();
        if (!normalized.startsWith(projectRoot)) {
            return false;
        }
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
            return null;
        }
    }

    private void validateWithinProject(Path path) {
        if (!isWithinProject(path)) {
            throw new IllegalStateException(
                    "资产路径越界，必须位于项目工作区内：" + path + "（projectRoot=" + projectRoot + "）");
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

    private TemplateIndexConfig parseConfig() {
        Path configFile = assetsRoot.resolve(TEMPLATE_INDEX_FILE);
        // template-index.json 文件自身也校验 realPath（防 symlink 指向项目外）
        validateWithinProject(configFile);
        if (!configFile.toFile().exists()) {
            throw new IllegalStateException(
                    "模板索引文件不存在：" + configFile + "（assetsPath=" + assetsRoot + "）");
        }
        try {
            String content = Files.readString(configFile);
            return objectMapper.readValue(content, TemplateIndexConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("模板索引解析失败：" + configFile, e);
        }
    }
}
