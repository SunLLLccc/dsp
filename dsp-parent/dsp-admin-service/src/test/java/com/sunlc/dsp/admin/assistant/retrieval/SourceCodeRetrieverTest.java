package com.sunlc.dsp.admin.assistant.retrieval;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SourceCodeRetriever 单测。验证 whitelist 外不读、ignore（target/node_modules）、
 * 敏感文件（application.yml）跳过、超 maxFileSizeKB 跳过。
 */
class SourceCodeRetrieverTest {

    @Test
    void whitelistFiltersAndIgnoresWork(@TempDir Path tempDir) throws IOException {
        buildProject(tempDir);
        AssetSourceLoader loader = loader(tempDir, 512);
        SourceCodeRetriever retriever = new SourceCodeRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("aichat", "session"), 5);

        // 应命中 src/main/java 下的 AiChatSession，不命中 target/node_modules/application.yml
        assertFalse(hits.isEmpty(), "应命中源码");
        boolean hitSession = hits.stream().anyMatch(c -> c.getPath().contains("AiChatSession"));
        assertTrue(hitSession, "应命中 AiChatSession.java");

        // 不应出现敏感文件
        boolean leakedSecret = hits.stream().anyMatch(c -> c.getPath().contains("application.yml")
                || c.getPath().contains(".env") || c.getSnippet().contains("secret-key"));
        assertFalse(leakedSecret, "敏感文件内容绝不进入检索结果");

        // 不应出现 target 下的 class（被 ignore.paths 跳过）
        boolean leakedTarget = hits.stream().anyMatch(c -> c.getPath().contains("target/"));
        assertFalse(leakedTarget, "target 目录应被跳过");
    }

    @Test
    void largeFileSkipped(@TempDir Path tempDir) throws IOException {
        buildProject(tempDir);
        // 构造一个超过 1KB 的源码文件，maxFileSizeKB=1 应跳过它
        Path bigSrc = Files.createDirectories(tempDir.resolve("dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/entity"))
                .resolve("BigAiChatSession.java");
        StringBuilder sb = new StringBuilder("package com.sunlc.dsp.entity;\npublic class BigAiChatSession {\n");
        for (int i = 0; i < 80; i++) {
            sb.append("  private String field").append(i).append(" = \"aichat session padding\";\n");
        }
        sb.append("}\n");
        Files.writeString(bigSrc, sb.toString());
        assertTrue(Files.size(bigSrc) > 1024, "测试文件应超过 1KB");

        AssetSourceLoader loader = loader(tempDir, 1);
        SourceCodeRetriever retriever = new SourceCodeRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("aichat", "session"), 5);
        boolean leakedLarge = hits.stream().anyMatch(c -> c.getPath().contains("BigAiChatSession"));
        assertFalse(leakedLarge, "超过 maxFileSizeKB 的文件应被跳过");
    }

    @Test
    void whitelistOutsideNotRead(@TempDir Path tempDir) throws IOException {
        buildProject(tempDir);
        AssetSourceLoader loader = loader(tempDir, 512);
        SourceCodeRetriever retriever = new SourceCodeRetriever(loader);

        // 用一个明显不在 whitelist 的词检索，应不命中
        List<RetrievalCitation> hits = retriever.retrieve(List.of("zzznotexist"), 5);
        assertTrue(hits.isEmpty());
    }

    @Test
    void globExpandsAllModules(@TempDir Path tempDir) throws IOException {
        // 两个模块都放源码，whitelist = dsp-parent/*/src/main/java，应都命中
        Path m1 = Files.createDirectories(tempDir.resolve("dsp-parent/dsp-common/src/main/java/com/sunlc/dsp"));
        Files.writeString(m1.resolve("CommonUtil.java"),
                "package com.sunlc.dsp;\npublic class CommonUtil {\n  void aiSession() {}\n}\n");
        Path m2 = Files.createDirectories(tempDir.resolve("dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp"));
        Files.writeString(m2.resolve("AdminService.java"),
                "package com.sunlc.dsp;\npublic class AdminService {\n  void aiSession() {}\n}\n");
        AssetSourceLoader loader = loader(tempDir, 512);
        // 先确认 glob 展开为两个模块目录
        List<Path> globRoots = loader.resolveProjectGlobPaths("dsp-parent/*/src/main/java");
        assertTrue(globRoots.size() >= 2, "glob * 应展开为至少 2 个模块目录，实际：" + globRoots.size());

        SourceCodeRetriever retriever = new SourceCodeRetriever(loader);
        List<RetrievalCitation> hits = retriever.retrieve(List.of("aiSession"), 5);

        assertTrue(hits.size() >= 2, "glob * 应展开所有模块，实际命中数：" + hits.size());
        boolean hitCommon = hits.stream().anyMatch(c -> c.getPath().contains("dsp-common"));
        boolean hitAdmin = hits.stream().anyMatch(c -> c.getPath().contains("dsp-admin-service"));
        assertTrue(hitCommon, "应命中 dsp-common 模块");
        assertTrue(hitAdmin, "应命中 dsp-admin-service 模块");
    }

    @Test
    void whitelistPathEscapeRejected(@TempDir Path tempDir) throws IOException {
        buildProject(tempDir);
        // whitelist 配越界路径 ../../outside，应被拒绝不读取
        Path outside = Files.createDirectories(tempDir.resolve("../outside/src"));
        Files.writeString(outside.resolve("Leaked.java"),
                "class Leaked { void aiSessionSecret() {} }");
        AssetSourceLoader loader = loaderWithEscapedWhitelist(tempDir);
        SourceCodeRetriever retriever = new SourceCodeRetriever(loader);

        List<RetrievalCitation> hits = retriever.retrieve(List.of("aiSessionSecret"), 5);
        boolean leaked = hits.stream().anyMatch(c -> c.getPath().contains("Leaked")
                || c.getSnippet().contains("aiSessionSecret"));
        assertFalse(leaked, "越界 whitelist 路径不应被读取");
    }

    private void buildProject(Path root) throws IOException {
        // src/main/java 下放正常源码
        Path src = Files.createDirectories(root.resolve("dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/entity"));
        Files.writeString(src.resolve("AiChatSession.java"),
                "package com.sunlc.dsp.entity;\npublic class AiChatSession {\n  private String sessionId;\n}\n");
        // target 下放 class（应被 ignore.paths 跳过）
        Path target = Files.createDirectories(root.resolve("dsp-parent/dsp-core/target/classes/com/sunlc/dsp/entity"));
        Files.writeString(target.resolve("AiChatSession.class"), "fake binary content");
        // 敏感配置（应被 sensitive 跳过）
        Path res = Files.createDirectories(root.resolve("dsp-parent/dsp-core/src/main/resources"));
        Files.writeString(res.resolve("application.yml"), "spring:\n  datasource:\n    password: secret-key\n");
        // node_modules（应被 ignore.paths 跳过）
        Path nm = Files.createDirectories(root.resolve("dsp-admin-web/node_modules/pkg"));
        Files.writeString(nm.resolve("index.js"), "var x = 'aichat session';\n");
    }

    private AssetSourceLoader loader(Path root, int maxFileSizeKB) throws IOException {
        return loaderWithWhitelist(root, maxFileSizeKB,
                "{\"path\":\"dsp-parent/*/src/main/java\",\"scope\":\"后端源码\"}");
    }

    private AssetSourceLoader loaderWithEscapedWhitelist(Path root) throws IOException {
        return loaderWithWhitelist(root, 512,
                "{\"path\":\"../../outside/src\",\"scope\":\"越界\"}");
    }

    private AssetSourceLoader loaderWithWhitelist(Path root, int maxFileSizeKB, String whitelistJson)
            throws IOException {
        Path assets = Files.createDirectories(root.resolve("ai-assets"));
        Files.writeString(assets.resolve("retrieval-sources.json"),
                "{ \"version\":\"1.0\", \"docs\":{\"sources\":[]}, " +
                        "\"sourceCodeFallback\":{ \"whitelist\":[" + whitelistJson + "], \"ignore\":{" +
                        "\"paths\":[\"**/target/\",\"**/node_modules/\"]," +
                        "\"filePatterns\":[\"*.class\"]," +
                        "\"sensitive\":[\"application*.yml\",\"*.env\",\"*.key\"]," +
                        "\"maxFileSizeKB\":" + maxFileSizeKB +
                        "} } }");
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
