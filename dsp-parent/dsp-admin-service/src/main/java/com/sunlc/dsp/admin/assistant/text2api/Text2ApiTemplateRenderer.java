package com.sunlc.dsp.admin.assistant.text2api;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Text2API 模板渲染器（T3-C）。
 * <p>
 * 读取 templateSelection 指定的模板文件，基于 InterfaceDraft + SqlDraft 填充 XML。
 * <p>
 * 安全约束：
 * <ul>
 *   <li>模板文件必须在项目工作区的 template/ 目录下（normalize + projectRoot + realPath）</li>
 *   <li>禁止 ../ 越界、禁止 symlink 指向项目外</li>
 *   <li>缺模板文件拒绝</li>
 *   <li>不做自由 XML 生成，只基于模板替换可定位的节点/属性</li>
 * </ul>
 */
@Slf4j
@Component
public class Text2ApiTemplateRenderer {

    /** 填充结果。 */
    public static class RenderResult {
        private final boolean success;
        private final String xml;
        private final String errorMessage;

        private RenderResult(boolean success, String xml, String errorMessage) {
            this.success = success;
            this.xml = xml;
            this.errorMessage = errorMessage;
        }

        public static RenderResult ok(String xml) {
            return new RenderResult(true, xml, null);
        }

        public static RenderResult failed(String message) {
            return new RenderResult(false, null, message);
        }

        public boolean isSuccess() { return success; }
        public String getXml() { return xml; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 渲染 XML。
     *
     * @param templateFile   模板文件相对路径（如 template/01-simple-sql-query.xml）
     * @param interfaceDraft 接口定义（JSON 字符串）
     * @param sqlDraft       SQL 草稿（结构化 JSON 字符串）
     * @return 渲染结果
     */
    public RenderResult render(String templateFile, String interfaceDraftJson, String sqlDraftJson) {
        // 1. 路径安全校验
        Path resolved = resolveAndValidate(templateFile);
        if (resolved == null) {
            return RenderResult.failed("模板文件路径非法或越界: " + templateFile);
        }
        if (!Files.isRegularFile(resolved)) {
            return RenderResult.failed("模板文件不存在: " + templateFile);
        }

        // 2. 读取模板
        String templateContent;
        try {
            templateContent = Files.readString(resolved);
        } catch (IOException e) {
            return RenderResult.failed("模板文件读取失败: " + e.getMessage());
        }

        // 3. 解析 interfaceDraft / sqlDraft（简单解析，提取关键字段）
        InterfaceDraft interfaceDraft = parseInterfaceDraft(interfaceDraftJson);
        SqlDraft sqlDraft = parseSqlDraft(sqlDraftJson);
        if (interfaceDraft == null || sqlDraft == null) {
            return RenderResult.failed("接口定义或 SQL 草稿解析失败");
        }

        // 4. DOM4J 解析模板并填充
        try {
            SAXReader reader = new SAXReader();
            Document doc = reader.read(new java.io.StringReader(templateContent));
            Element root = doc.getRootElement();

            // 填充 interface 属性
            fillInterfaceAttributes(root, interfaceDraft);

            // 填充 query SQL
            String fillError = fillQueries(root, sqlDraft, templateFile);
            if (fillError != null) {
                return RenderResult.failed(fillError);
            }

            // 输出 XML（美化）
            String xml = doc.asXML();
            return RenderResult.ok(xml);
        } catch (Exception e) {
            log.warn("模板渲染失败: {}", e.getMessage());
            return RenderResult.failed("模板渲染失败: " + e.getMessage());
        }
    }

    /**
     * 路径安全：templateFile 必须在项目工作区的 template/ 目录下。
     * normalize + templateRoot + realPath 校验，防 ../ 越界和 symlink 指向 template 外。
     */
    Path resolveAndValidate(String templateFile) {
        if (templateFile == null || templateFile.isBlank()) {
            return null;
        }
        // 必须以 template/ 开头（相对路径）
        String normalized = templateFile.replace('\\', '/');
        if (!normalized.startsWith("template/") || normalized.contains("..")) {
            log.warn("模板路径必须以 template/ 开头且不含 ..: {}", templateFile);
            return null;
        }
        // 必须是 .xml 文件
        if (!normalized.endsWith(".xml")) {
            log.warn("模板文件必须是 .xml: {}", templateFile);
            return null;
        }

        Path projectRoot = Paths.get(System.getProperty("user.dir")).normalize();
        Path templateRoot = projectRoot.resolve("template").normalize();
        Path templateRootReal = toRealPathSafe(templateRoot);

        Path resolved = projectRoot.resolve(templateFile).normalize();
        // 必须在 templateRoot 下（非 projectRoot 下任意位置）
        if (!resolved.startsWith(templateRoot)) {
            log.warn("模板路径不在 template/ 目录下: {}", templateFile);
            return null;
        }
        // realPath 防 symlink（必须在 templateRootReal 下）
        if (Files.exists(resolved)) {
            Path real = toRealPathSafe(resolved);
            if (real != null && templateRootReal != null && !real.startsWith(templateRootReal)) {
                log.warn("模板经 symlink 解析后越界 template/: {}", templateFile);
                return null;
            }
        }
        return resolved;
    }

    /** 填充 interface 节点的 transno/name/description 属性。 */
    private void fillInterfaceAttributes(Element root, InterfaceDraft draft) {
        if (draft.getTransno() != null && !draft.getTransno().isBlank()) {
            root.addAttribute("transno", draft.getTransno());
        }
        if (draft.getName() != null && !draft.getName().isBlank()) {
            root.addAttribute("name", draft.getName());
        }
        if (draft.getDescription() != null && !draft.getDescription().isBlank()) {
            root.addAttribute("description", draft.getDescription());
        }
    }

    /**
     * 填充 query 节点的 SQL 文本。
     * <p>
     * 一期保守策略：SQL 段数必须精确匹配模板 query 节点数，不残留样例 SQL。
     * <ul>
     *   <li>单 SQL 模板（1 个 query）：sqlItems 必须 == 1</li>
     *   <li>多 SQL 模板（N 个 query）：sqlItems 必须 == N</li>
     * </ul>
     * 不匹配返回失败，不落库，提示用户调整。
     *
     * @return null=成功；非 null=失败信息
     */
    @SuppressWarnings("unchecked")
    private String fillQueries(Element root, SqlDraft sqlDraft, String templateFile) {
        List<Element> queries = root.elements("query");
        List<SqlItem> sqlItems = sqlDraft.getSqlItems();

        if (sqlItems == null || sqlItems.isEmpty()) {
            return "SQL 草稿为空，无法填充";
        }

        // 精确匹配：query 节点数必须 == sqlItems 数，不残留样例 SQL
        if (queries.size() != sqlItems.size()) {
            return "模板 query 节点数（" + queries.size() + "）与 SQL 段数（" + sqlItems.size()
                    + "）不匹配。请调整 SQL 段数或选择匹配的模板。";
        }

        // 逐个替换 query SQL（全部替换，不留样例）
        for (int i = 0; i < queries.size(); i++) {
            Element query = queries.get(i);
            SqlItem item = sqlItems.get(i);
            query.setText("\n        " + item.getSql().trim() + "\n    ");
            if (item.getSqlId() != null && !item.getSqlId().isBlank()) {
                query.addAttribute("id", item.getSqlId());
            }
        }
        return null;
    }

    // ===== 简单 JSON 解析（避免 Jackson 依赖循环，提取关键字段）=====

    private InterfaceDraft parseInterfaceDraft(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, InterfaceDraft.class);
        } catch (Exception e) {
            return null;
        }
    }

    private SqlDraft parseSqlDraft(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, SqlDraft.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Path toRealPathSafe(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }
}
