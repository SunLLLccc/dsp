package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.common.service.XmlConfigCacheInvalidator;
import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.entity.InterfaceTemplate;
import com.fintechervision.dsp.entity.InterfaceTemplateHistory;
import com.fintechervision.dsp.entity.InterfaceVersion;
import com.fintechervision.dsp.enums.InterfaceStatus;
import com.fintechervision.dsp.mapper.InterfaceTemplateHistoryMapper;
import com.fintechervision.dsp.mapper.InterfaceTemplateMapper;
import com.fintechervision.dsp.service.InterfaceInfoService;
import com.fintechervision.dsp.service.InterfaceTemplateService;
import com.fintechervision.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterfaceTemplateServiceImpl extends ServiceImpl<InterfaceTemplateMapper, InterfaceTemplate>
        implements InterfaceTemplateService {

    private final InterfaceTemplateHistoryMapper historyMapper;
    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final XmlConfigCacheInvalidator xmlConfigCacheInvalidator;

    @Override
    public Page<InterfaceTemplate> listTemplates(String transno, String systemName, Integer status,
                                                  Integer pageNum, Integer pageSize) {
        Page<InterfaceTemplate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceTemplate> wrapper = new LambdaQueryWrapper<>();
        if (transno != null && !transno.isEmpty()) {
            wrapper.like(InterfaceTemplate::getTransno, transno);
        }
        if (systemName != null && !systemName.isEmpty()) {
            wrapper.like(InterfaceTemplate::getSystemName, systemName);
        }
        if (status != null) {
            wrapper.eq(InterfaceTemplate::getStatus, status);
        }
        wrapper.orderByDesc(InterfaceTemplate::getUpdatedTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    public InterfaceTemplate createTemplate(String transno, String xmlContent, String changeLog, String operator) {
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "接口不存在");
        }

        InterfaceTemplate template = new InterfaceTemplate();
        template.setTransno(transno);
        template.setSystemName(info.getSystemName());
        template.setInterfaceName(info.getName());
        template.setXmlContent(xmlContent);
        template.setVersionNo(1);
        template.setStatus(InterfaceStatus.DRAFT.getCode());
        template.setCreatedBy(operator);
        template.setCreatedTime(LocalDateTime.now());
        template.setUpdatedBy(operator);
        template.setUpdatedTime(LocalDateTime.now());
        save(template);

        saveHistory(template, changeLog, operator);
        return template;
    }

    @Override
    @Transactional
    public InterfaceTemplate updateTemplate(Long id, String xmlContent, String changeLog, String operator) {
        InterfaceTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "模板不存在");
        }

        // 先保存历史
        saveHistory(template, changeLog, operator);

        template.setXmlContent(xmlContent);
        template.setVersionNo(template.getVersionNo() + 1);
        template.setUpdatedBy(operator);
        template.setUpdatedTime(LocalDateTime.now());
        updateById(template);

        // 同步更新系统名和接口名（可能接口信息已变更）
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(template.getTransno());
        if (info != null) {
            template.setSystemName(info.getSystemName());
            template.setInterfaceName(info.getName());
            updateById(template);
        }

        if (template.getStatus() == InterfaceStatus.PUBLISHED.getCode()) {
            xmlConfigCacheInvalidator.invalidate(template.getTransno());
        }
        return template;
    }

    @Override
    @Transactional
    public void publishTemplate(Long id, String operator) {
        InterfaceTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "模板不存在");
        }
        template.setStatus(InterfaceStatus.PUBLISHED.getCode());
        template.setUpdatedBy(operator);
        template.setUpdatedTime(LocalDateTime.now());
        updateById(template);

        // 同时将接口状态设为已发布
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(template.getTransno());
        if (info != null && info.getStatus() != InterfaceStatus.PUBLISHED.getCode()) {
            info.setStatus(InterfaceStatus.PUBLISHED.getCode());
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }

        xmlConfigCacheInvalidator.invalidate(template.getTransno());
        log.info("XML模板发布: transno={}, version={}", template.getTransno(), template.getVersionNo());
    }

    @Override
    @Transactional
    public void offlineTemplate(Long id) {
        InterfaceTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "模板不存在");
        }
        template.setStatus(InterfaceStatus.OFFLINE.getCode());
        template.setUpdatedTime(LocalDateTime.now());
        updateById(template);
        xmlConfigCacheInvalidator.invalidate(template.getTransno());
        log.info("XML模板下线: transno={}", template.getTransno());
    }

    @Override
    public String generateXmlFromSchema(String transno) {
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "接口不存在");
        }

        // 获取最新版本的 schema
        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno)
               .orderByDesc(InterfaceVersion::getVersionNo)
               .last("LIMIT 1");
        InterfaceVersion version = interfaceVersionService.getOne(wrapper);

        String inputFields = parseSchemaFields(version != null ? version.getInputSchema() : null);
        String outputFields = parseSchemaFields(version != null ? version.getOutputSchema() : null);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<interface transno=\"").append(transno).append("\">\n");
        xml.append("  <request>\n");
        xml.append(inputFields);
        xml.append("  </request>\n");
        xml.append("  <queries>\n");
        xml.append("    <!-- 请在此处添加查询配置 -->\n");
        xml.append("  </queries>\n");
        xml.append("  <result-map>\n");
        xml.append(outputFields);
        xml.append("  </result-map>\n");
        xml.append("</interface>");
        return xml.toString();
    }

    @Override
    public Page<InterfaceTemplateHistory> historyList(Long templateId, Integer pageNum, Integer pageSize) {
        Page<InterfaceTemplateHistory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceTemplateHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceTemplateHistory::getTemplateId, templateId)
               .orderByDesc(InterfaceTemplateHistory::getVersionNo);
        return historyMapper.selectPage(page, wrapper);
    }

    @Override
    public List<InterfaceTemplateHistory> getHistoryByTransno(String transno) {
        LambdaQueryWrapper<InterfaceTemplateHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceTemplateHistory::getTransno, transno)
               .orderByDesc(InterfaceTemplateHistory::getVersionNo);
        return historyMapper.selectList(wrapper);
    }

    @Override
    public InterfaceTemplate getByTransno(String transno) {
        LambdaQueryWrapper<InterfaceTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceTemplate::getTransno, transno);
        return getOne(wrapper);
    }

    private void saveHistory(InterfaceTemplate template, String changeLog, String operator) {
        InterfaceTemplateHistory history = new InterfaceTemplateHistory();
        history.setTemplateId(template.getId());
        history.setTransno(template.getTransno());
        history.setSystemName(template.getSystemName());
        history.setInterfaceName(template.getInterfaceName());
        history.setXmlContent(template.getXmlContent());
        history.setVersionNo(template.getVersionNo());
        history.setChangeLog(changeLog);
        history.setCreatedBy(operator);
        history.setCreatedTime(LocalDateTime.now());
        historyMapper.insert(history);
    }

    /**
     * 从 JSON Schema 中解析字段，生成 XML param 标签
     */
    private String parseSchemaFields(String schemaJson) {
        if (schemaJson == null || schemaJson.isEmpty()) {
            return "    <!-- 暂无字段定义 -->\n";
        }
        StringBuilder sb = new StringBuilder();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode schema = mapper.readTree(schemaJson);
            com.fasterxml.jackson.databind.JsonNode properties = schema.get("properties");
            com.fasterxml.jackson.databind.JsonNode required = schema.get("required");

            if (properties != null) {
                java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = properties.fields();
                while (fields.hasNext()) {
                    java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry = fields.next();
                    String fieldName = entry.getKey();
                    com.fasterxml.jackson.databind.JsonNode fieldDef = entry.getValue();
                    String type = fieldDef.has("type") ? fieldDef.get("type").asText() : "string";
                    String title = fieldDef.has("title") ? fieldDef.get("title").asText() : "";
                    boolean isRequired = required != null && required.isArray() && contains(required, fieldName);

                    sb.append("    <param name=\"").append(fieldName).append("\"")
                      .append(" type=\"").append(type).append("\"");
                    if (isRequired) {
                        sb.append(" required=\"true\"");
                    }
                    if (!title.isEmpty()) {
                        sb.append(" description=\"").append(title).append("\"");
                    }
                    sb.append("/>\n");
                }
            }
        } catch (Exception e) {
            log.warn("解析JSON Schema失败: {}", e.getMessage());
            sb.append("    <!-- Schema解析失败，请手动维护 -->\n");
        }
        return sb.toString();
    }

    private boolean contains(com.fasterxml.jackson.databind.JsonNode array, String value) {
        for (com.fasterxml.jackson.databind.JsonNode node : array) {
            if (node.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
