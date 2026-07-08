package com.sunlc.dsp.adminservice.service;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.InterfaceTemplate;
import com.sunlc.dsp.entity.InterfaceVersion;
import com.sunlc.dsp.enums.InterfaceStatus;
import com.sunlc.dsp.enums.VersionStatus;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceTemplateService;
import com.sunlc.dsp.service.InterfaceVersionService;
import com.sunlc.dsp.engine.validator.SqlSecurityValidator;
import com.sunlc.dsp.common.service.XmlConfigCacheInvalidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置导入业务服务。
 * <p>
 * 从 {@code ConfigImportExportController.importConfig} 抽取的业务逻辑，
 * 供 Controller（HTTP 入口）和 Text2API publish（07-08-text2api-web-flow）复用。
 * <p>
 * 放在 dsp-admin-service（非 dsp-core），因为导入逻辑依赖 admin/service/engine/cache 能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigImportService {

    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final InterfaceTemplateService interfaceTemplateService;
    private final XmlConfigCacheInvalidator xmlConfigCacheInvalidator;
    private final SqlSecurityValidator sqlSecurityValidator;

    /**
     * 导入配置 — 直接生效，不需要审批或发布。
     * 新建/覆盖接口，版本和模板直接设为已发布状态，维护历史记录。
     *
     * @param configData 配置数据，结构：interfaceInfo / schema / template / changeLog
     * @param operator   操作人
     * @return 导入结果摘要（interface/schema/template 各一条说明）
     * @throws BusinessException 缺少接口信息/接口编码，或 SQL 安全校验失败
     */
    @Transactional
    public Map<String, Object> importConfig(Map<String, Object> configData, String operator) {
        String changeLog = (String) configData.getOrDefault("changeLog", "配置导入");

        @SuppressWarnings("unchecked")
        Map<String, Object> infoMap = (Map<String, Object>) configData.get("interfaceInfo");
        if (infoMap == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少接口信息");
        }

        String transno = (String) infoMap.get("transno");
        if (transno == null || transno.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少接口编码");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        boolean isNew = false;

        // 1. 新建或更新接口基础信息
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);

        if (info == null) {
            isNew = true;
            info = new InterfaceInfo();
            info.setTransno(transno);
            info.setName((String) infoMap.get("name"));
            info.setSystemName((String) infoMap.get("systemName"));
            Object systemIdVal = infoMap.get("systemId");
            if (systemIdVal instanceof Number) {
                info.setSystemId(((Number) systemIdVal).longValue());
            }
            info.setDescription((String) infoMap.get("description"));
            info.setStatus(InterfaceStatus.PUBLISHED.getCode());
            info.setCurrentVersion(1);
            info.setCreatedBy(operator);
            info.setCreatedTime(LocalDateTime.now());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.save(info);
            result.put("interface", "新建接口 " + transno);
        } else {
            info.setName((String) infoMap.get("name"));
            info.setSystemName((String) infoMap.get("systemName"));
            Object systemIdVal = infoMap.get("systemId");
            if (systemIdVal instanceof Number) {
                info.setSystemId(((Number) systemIdVal).longValue());
            }
            info.setDescription((String) infoMap.get("description"));
            info.setStatus(InterfaceStatus.PUBLISHED.getCode());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
            result.put("interface", "覆盖接口 " + transno);
        }

        // 2. 导入Schema版本 — 直接发布
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = (Map<String, Object>) configData.get("schema");
        if (schemaMap != null) {
            String inputSchema = (String) schemaMap.get("inputSchema");
            if (inputSchema != null && !inputSchema.isEmpty()) {
                sqlSecurityValidator.validateXmlConfig(inputSchema);
            }
            InterfaceVersion version = interfaceVersionService.saveSchema(
                    transno,
                    inputSchema,
                    (String) schemaMap.get("outputSchema"),
                    changeLog,
                    operator);
            version.setStatus(VersionStatus.PUBLISHED.getCode());
            version.setPublishedTime(LocalDateTime.now());
            interfaceVersionService.updateById(version);

            info.setCurrentVersion(version.getVersionNo());
            interfaceInfoService.updateById(info);

            result.put("schema", "导入Schema V" + version.getVersionNo() + " 并直接发布");
        }

        // 3. 导入模板XML — 直接发布，维护历史记录
        @SuppressWarnings("unchecked")
        Map<String, Object> templateMap = (Map<String, Object>) configData.get("template");
        if (templateMap != null) {
            String xmlContent = (String) templateMap.get("xmlContent");
            if (xmlContent != null && !xmlContent.isEmpty()) {
                sqlSecurityValidator.validateXmlConfig(xmlContent);
            }
            InterfaceTemplate existingTemplate = interfaceTemplateService.getByTransno(transno);

            if (existingTemplate == null) {
                InterfaceTemplate created = interfaceTemplateService.createTemplate(
                        transno, xmlContent, changeLog, operator);
                interfaceTemplateService.publishTemplate(created.getId(), operator);
                result.put("template", "新建模板 V1 并直接发布");
            } else {
                InterfaceTemplate updated = interfaceTemplateService.updateTemplate(
                        existingTemplate.getId(), xmlContent, changeLog, operator);
                if (updated.getStatus() != InterfaceStatus.PUBLISHED.getCode()) {
                    interfaceTemplateService.publishTemplate(updated.getId(), operator);
                }
                result.put("template", "更新模板 V" + updated.getVersionNo() + " 并直接发布");
            }
        }

        // 4. 刷新缓存使配置立即生效
        xmlConfigCacheInvalidator.invalidate(transno);

        log.info("配置导入完成并生效: transno={}, operator={}, isNew={}", transno, operator, isNew);
        return result;
    }
}
