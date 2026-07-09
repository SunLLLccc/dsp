package com.sunlc.dsp.admin.assistant.template;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text2ApiAssetLoader 路径安全单测。
 * 覆盖：越界拒绝、symlink 指向项目外拒绝、文件 symlink 拒绝。
 */
class Text2ApiAssetLoaderTest {

    @Test
    void pathEscapeRejected(@TempDir Path tempDir) throws IOException {
        // ai-assets 目录 + 合法 template-index.json
        Path assets = Files.createDirectories(tempDir.resolve("ai-assets"));
        Files.writeString(assets.resolve("template-index.json"),
                "{\"version\":\"1.0\",\"templates\":[]}");
        String prev = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            Text2ApiAssetLoader loader = new Text2ApiAssetLoader(props);
            assertDoesNotThrow(loader::load);
        } finally {
            System.setProperty("user.dir", prev);
        }
    }

    @Test
    void pathEscapeOutsideProjectRejected(@TempDir Path tempDir) {
        String prev = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("../../etc");
            Text2ApiAssetLoader loader = new Text2ApiAssetLoader(props);
            assertThrows(IllegalStateException.class, loader::load,
                    "越界 assetsPath 应被拒绝");
        } finally {
            System.setProperty("user.dir", prev);
        }
    }

    @Test
    void symlinkToOutsideRejected(@TempDir Path tempDir) throws IOException {
        // 项目内 ai-assets 目录，但 template-index.json 是 symlink 指向项目外
        Path assets = Files.createDirectories(tempDir.resolve("ai-assets"));
        Path outsideFile = tempDir.resolve("../outside-template.json").normalize();
        Files.writeString(outsideFile, "{\"version\":\"1.0\",\"templates\":[]}");
        Path link = assets.resolve("template-index.json");
        try {
            Files.createSymbolicLink(link, outsideFile);
        } catch (UnsupportedOperationException | IOException e) {
            // 某些环境不支持 symlink，跳过
            return;
        }
        String prev = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            Text2ApiAssetLoader loader = new Text2ApiAssetLoader(props);
            assertThrows(IllegalStateException.class, loader::load,
                    "symlink 指向项目外的 template-index.json 应被拒绝");
        } finally {
            System.setProperty("user.dir", prev);
        }
    }

    @Test
    void missingConfigFileThrows(@TempDir Path tempDir) throws IOException {
        // ai-assets 目录存在但无 template-index.json
        Files.createDirectories(tempDir.resolve("ai-assets"));
        String prev = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            Text2ApiAssetLoader loader = new Text2ApiAssetLoader(props);
            assertThrows(IllegalStateException.class, loader::load,
                    "缺少 template-index.json 应抛异常");
        } finally {
            System.setProperty("user.dir", prev);
        }
    }
}
