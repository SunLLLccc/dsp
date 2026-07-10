package com.sunlc.dsp.admin.assistant.text2api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Text2API 导入 JSON 构造器（T3-C）。
 * <p>
 * 根据 InterfaceDraft + xmlDraft 构造兼容 {@code ConfigImportService.importConfig} 的导入 JSON。
 * <p>
 * 结构：
 * <pre>
 * {
 *   "interfaceInfo": { transno, name, systemName, systemId, description },
 *   "schema": {
 *     "inputSchema": xmlDraft,       // XML 配置字符串（一期与 xmlContent 同源）
 *     "outputSchema": interfaceDraft.outputSchema,
 *     "changeLog": "Text2API 生成"
 *   },
 *   "template": { xmlContent: xmlDraft, versionNo: 1 },
 *   "changeLog": "Text2API 生成"
 * }
 * </pre>
 * 注意：schema.inputSchema 与 template.xmlContent 一期保持一致（同源 XML），
 * 运行时引擎以 template.xmlContent 为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Text2ApiImportJsonBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造导入 JSON。
     *
     * @param interfaceDraftJson 接口定义 JSON 字符串
     * @param xmlDraft           生成的 XML
     * @return 导入 JSON 字符串；失败返回 null
     */
    public String build(String interfaceDraftJson, String xmlDraft) {
        try {
            InterfaceDraft draft = objectMapper.readValue(interfaceDraftJson, InterfaceDraft.class);
            Map<String, Object> importJson = new LinkedHashMap<>();

            // interfaceInfo
            Map<String, Object> interfaceInfo = new LinkedHashMap<>();
            interfaceInfo.put("transno", draft.getTransno());
            interfaceInfo.put("name", draft.getName());
            interfaceInfo.put("systemName", draft.getSystem() != null ? draft.getSystem() : "Text2API");
            interfaceInfo.put("description", draft.getDescription() != null ? draft.getDescription() : "");
            importJson.put("interfaceInfo", interfaceInfo);

            // schema（inputSchema = XML 配置字符串，一期与 xmlContent 同源）
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("inputSchema", xmlDraft);
            schema.put("outputSchema", draft.getOutputSchema() != null ? draft.getOutputSchema() : "{}");
            schema.put("changeLog", "Text2API 生成");
            importJson.put("schema", schema);

            // template（xmlContent = XML 配置字符串，运行时以此为准）
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("xmlContent", xmlDraft);
            template.put("versionNo", 1);
            importJson.put("template", template);

            // changeLog
            importJson.put("changeLog", "Text2API 生成");

            return objectMapper.writeValueAsString(importJson);
        } catch (Exception e) {
            log.warn("导入 JSON 构造失败: {}", e.getMessage());
            return null;
        }
    }
}
