package com.sunlc.dsp.admin.assistant.template;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;

/**
 * 真实 ai-assets/template-index.json 解析回归测试。
 * <p>
 * 验证 Text2ApiAssetLoader 能正确解析仓库内的真实 template-index.json，
 * 防止 JSON 字段漂移导致 T2 在真实环境失效。
 * <p>
 * 注意：此测试依赖 user.dir 指向仓库根目录（ai-assets/ 的父目录）。
 * 在 IDE / mvn 从仓库根运行时通过；若从子目录运行则跳过。
 */
class Text2ApiAssetLoaderIntegrationTest {

    @Test
    void parseRealTemplateIndex() {
        // 向上查找含 ai-assets/ 的祖先目录作为仓库根（兼容从 dsp-parent 或仓库根运行）
        Path candidate = Paths.get(System.getProperty("user.dir"));
        Path repoRoot = null;
        Path indexFile = null;
        for (int i = 0; i < 5 && candidate != null; i++) {
            Path f = candidate.resolve("ai-assets/template-index.json");
            if (f.toFile().exists()) {
                repoRoot = candidate;
                indexFile = f;
                break;
            }
            candidate = candidate.getParent();
        }
        Assumptions.assumeTrue(repoRoot != null && indexFile != null,
                "未找到 ai-assets/template-index.json，跳过（非仓库环境）");

        String prev = System.getProperty("user.dir");
        System.setProperty("user.dir", repoRoot.toString());
        try {
            AssistantProperties props = new AssistantProperties();
            props.setAssetsPath("ai-assets");
            Text2ApiAssetLoader loader = new Text2ApiAssetLoader(props);
            loader.load();

            TemplateIndexConfig config = loader.getConfig();
            assertNotNull(config.getVersion());
            assertTrue(config.getTemplates().size() > 0, "模板数量应 > 0");

            // 验证关键 SQL 模板存在
            assertTrue(findById(config, "01") != null, "应有模板 01（简单 SQL）");
            assertTrue(findById(config, "02") != null, "应有模板 02（动态 SQL）");
            assertTrue(findById(config, "03") != null, "应有模板 03（游标分页）");
            assertTrue(findById(config, "09") != null, "应有模板 09（并行编排）");
            assertTrue(findById(config, "10") != null, "应有模板 10（依赖编排）");

            // 验证非 SQL 模板存在但 appliesTo 不含 sql
            TemplateIndexConfig.TemplateEntry http = findById(config, "05");
            assertNotNull(http, "应有模板 05（HTTP）");
            assertTrue(!http.getAppliesTo().contains("sql"), "05 是 HTTP 模板，appliesTo 不含 sql");

            // 验证 01 的 requiresUserConfirmation / selectionSignals 非空
            TemplateIndexConfig.TemplateEntry t01 = findById(config, "01");
            assertTrue(t01.getSelectionSignals().size() > 0, "01 的 selectionSignals 非空");
            assertTrue(t01.getRequiresUserConfirmation().size() > 0, "01 的 requiresUserConfirmation 非空");
        } finally {
            System.setProperty("user.dir", prev);
        }
    }

    private TemplateIndexConfig.TemplateEntry findById(TemplateIndexConfig config, String id) {
        return config.getTemplates().stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElse(null);
    }
}
