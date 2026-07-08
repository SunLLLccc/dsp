package com.sunlc.dsp.admin.assistant.retrieval;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocRetriever 单测。构造临时项目结构，验证文档路径命中、内容命中、不存在路径不致失败。
 */
class DocRetrieverTest {

    @Test
    void retrievesByContentAndPath(@TempDir Path tempDir) throws IOException {
        buildProject(tempDir);
        AssetSourceLoader loader = loader(tempDir);
        DocRetriever retriever = new DocRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("xml", "dsl", "datasource"), 5);

        assertFalse(hits.isEmpty(), "应命中 docs/xml-dsl-reference.md");
        boolean hitDsl = hits.stream().anyMatch(c -> c.getPath().contains("xml-dsl-reference"));
        assertTrue(hitDsl, "应返回 xml-dsl-reference 文档");
    }

    @Test
    void missingSourcePathDoesNotFail(@TempDir Path tempDir) throws IOException {
        // 配置一个不存在的目录，检索应返回空而不抛异常
        buildProject(tempDir);
        AssetSourceLoader loader = loaderWithMissingDocSource(tempDir);
        DocRetriever retriever = new DocRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("datasource"), 5);
        assertTrue(hits.isEmpty(), "不存在的 source 路径应返回空");
    }

    @Test
    void sensitiveFileNotRetrieved(@TempDir Path tempDir) throws IOException {
        // docs 目录里放 application.yml（含敏感内容），不应被检索
        Path docs = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(docs.resolve("xml-dsl-reference.md"), "datasource 配置说明\n");
        Files.writeString(docs.resolve("application.yml"),
                "spring:\n  datasource:\n    password: secret-key-datasource\n");
        AssetSourceLoader loader = loader(tempDir);
        DocRetriever retriever = new DocRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("datasource"), 5);
        boolean leakedSecret = hits.stream().anyMatch(c -> c.getPath().contains("application.yml")
                || c.getSnippet().contains("secret-key"));
        assertFalse(leakedSecret, "docs 目录下的敏感文件不应被检索");
    }

    @Test
    void largeFileSkipped(@TempDir Path tempDir) throws IOException {
        // docs 目录里放超过 maxFileSizeKB 的 md，应跳过。用小阈值 maxFileSizeKB=1（1KB）
        Path docs = Files.createDirectories(tempDir.resolve("docs"));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            big.append("datasource 第").append(i).append("行 大文件填充内容填充填充填充\n");
        }
        Files.writeString(docs.resolve("big.md"), big.toString());
        assertTrue(Files.size(docs.resolve("big.md")) > 1024, "测试文件应超过 1KB");

        AssetSourceLoader loader = loaderWithMaxSize(tempDir, 1);
        DocRetriever retriever = new DocRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("datasource"), 5);
        boolean leakedBig = hits.stream().anyMatch(c -> c.getPath().contains("big.md"));
        assertFalse(leakedBig, "超过 maxFileSizeKB 的文档应被跳过");
    }

    @Test
    void escapedDocPathRejected(@TempDir Path tempDir) throws IOException {
        // docs source 配越界路径，不应读取项目外文件
        Path outside = Files.createDirectories(tempDir.resolve("../outside-docs"));
        Files.writeString(outside.resolve("leaked.md"), "datasource secret content leaked\n");
        AssetSourceLoader loader = loaderWithEscapedDocSource(tempDir);
        DocRetriever retriever = new DocRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("datasource"), 5);
        boolean leaked = hits.stream().anyMatch(c -> c.getSnippet().contains("secret content leaked"));
        assertFalse(leaked, "越界 docs 路径不应被读取");
    }

    private void buildProject(Path root) throws IOException {
        Path docs = Files.createDirectories(root.resolve("docs"));
        Files.writeString(docs.resolve("xml-dsl-reference.md"),
                "# XML DSL 参考\n\n## 数据源\n<datasource name=\"ds\" />\n## 分页\ncursor pagination\n");
        Files.writeString(docs.resolve("engine-architecture.md"),
                "# 引擎架构\n\nXML 引擎执行流程，datasource 引用规则。\n");
    }

    private AssetSourceLoader loader(Path root) throws IOException {
        return loaderWithDocSource(root,
                "{\"path\":\"docs/\",\"type\":\"directory\",\"title\":\"项目文档\"}");
    }

    private AssetSourceLoader loaderWithMissingDocSource(Path root) throws IOException {
        return loaderWithDocSource(root,
                "{\"path\":\"nonexistent/\",\"type\":\"directory\",\"title\":\"不存在\"}");
    }

    private AssetSourceLoader loaderWithEscapedDocSource(Path root) throws IOException {
        return loaderWithDocSource(root,
                "{\"path\":\"../../outside-docs\",\"type\":\"directory\",\"title\":\"越界\"}");
    }

    private AssetSourceLoader loaderWithDocSource(Path root, String docSourceJson) throws IOException {
        return loaderWithDocSource(root, docSourceJson, 512);
    }

    private AssetSourceLoader loaderWithMaxSize(Path root, int maxFileSizeKB) throws IOException {
        return loaderWithDocSource(root,
                "{\"path\":\"docs/\",\"type\":\"directory\",\"title\":\"项目文档\"}", maxFileSizeKB);
    }

    private AssetSourceLoader loaderWithDocSource(Path root, String docSourceJson, int maxFileSizeKB)
            throws IOException {
        Path assets = Files.createDirectories(root.resolve("ai-assets"));
        String ignoreBlock =
                "\"ignore\":{\"paths\":[\"**/target/\",\"**/node_modules/\"],\"filePatterns\":[\"*.class\"]," +
                        "\"sensitive\":[\"application*.yml\",\"*.env\",\"*.key\"],\"maxFileSizeKB\":" + maxFileSizeKB + "}";
        Files.writeString(assets.resolve("retrieval-sources.json"),
                "{ \"version\":\"1.0\", \"docs\":{ \"sources\":[" + docSourceJson +
                        "] }, \"sourceCodeFallback\":{ \"whitelist\":[], " + ignoreBlock + " } }");
        return loadWithUserDir(root);
    }

    private AssetSourceLoader loadWithUserDir(Path root) {
        String prev = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            AssetSourceLoader loader = new AssetSourceLoader(props);
            loader.load();
            return loader;
        } finally {
            System.setProperty("user.dir", prev);
        }
    }
}
