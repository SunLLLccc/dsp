package com.sunlc.dsp.admin.assistant.retrieval;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AssetSourceLoader 单测。用 @TempDir 构造资产目录与 retrieval-sources.json。
 */
class AssetSourceLoaderTest {

    @Test
    void loadsConfigSuccessfully(@TempDir Path tempDir) throws IOException {
        // 临时 assets 目录 + retrieval-sources.json
        Path assets = Files.createDirectories(tempDir.resolve("ai-assets"));
        Files.writeString(assets.resolve("retrieval-sources.json"), VALID_CONFIG);
        // 设置 user.dir 为 tempDir（AssetSourceLoader 用 user.dir 解析相对路径）
        String prevDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            AssetSourceLoader loader = new AssetSourceLoader(props);
            loader.load();

            RetrievalSourcesConfig config = loader.getConfig();
            assertNotNull(config);
            assertEquals("1.0", config.getVersion());
            assertEquals(512, config.getSourceCodeFallback().getIgnore().getMaxFileSizeKB());
            assertEquals(1, config.getDocs().getSources().size());
            assertTrue(loader.getProjectRoot().startsWith(tempDir));
        } finally {
            System.setProperty("user.dir", prevDir);
        }
    }

    @Test
    void missingConfigThrowsClearException(@TempDir Path tempDir) {
        String prevDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            AssetSourceLoader loader = new AssetSourceLoader(props);
            assertThrows(IllegalStateException.class, loader::load,
                    "配置文件不存在应抛明确异常");
        } finally {
            System.setProperty("user.dir", prevDir);
        }
    }

    @Test
    void pathEscapeRejected(@TempDir Path tempDir) {
        // assetsPath 用 ../ 尝试越界
        String prevDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("../../etc");
            AssetSourceLoader loader = new AssetSourceLoader(props);
            assertThrows(IllegalStateException.class, loader::load,
                    "越界路径应被拒绝");
        } finally {
            System.setProperty("user.dir", prevDir);
        }
    }

    private static final String VALID_CONFIG = """
            {
              "version": "1.0",
              "docs": {
                "sources": [
                  {"path": "docs/", "type": "directory", "title": "项目文档"}
                ]
              },
              "sourceCodeFallback": {
                "whitelist": [
                  {"path": "src/main/java"}
                ],
                "ignore": {
                  "paths": ["**/target/"],
                  "filePatterns": ["*.class"],
                  "sensitive": ["application*.yml"],
                  "maxFileSizeKB": 512
                }
              }
            }
            """;
}
