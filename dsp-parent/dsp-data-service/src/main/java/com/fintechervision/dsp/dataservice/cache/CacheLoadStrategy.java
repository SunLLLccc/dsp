package com.fintechervision.dsp.dataservice.cache;

import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.service.InterfaceInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据服务缓存加载策略 — 定义缓存范围
 * 当前：所有已发布接口（status=1）+ 可选的 template 目录模板
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheLoadStrategy {

    private final InterfaceInfoService interfaceInfoService;

    @Value("${dsp.cache.xml.load-templates:false}")
    private boolean loadTemplates;

    @Value("${dsp.cache.xml.template-path:template}")
    private String templatePath;

    /**
     * 加载当前应缓存的 transno 列表
     */
    public List<String> loadActiveTransnos() {
        List<String> transnos = new ArrayList<>();
        try {
            List<String> dbTransnos = interfaceInfoService.list().stream()
                    .filter(info -> info.getStatus() != null && info.getStatus() == 1)
                    .map(InterfaceInfo::getTransno)
                    .collect(Collectors.toList());
            transnos.addAll(dbTransnos);
        } catch (Exception e) {
            log.error("加载活跃接口列表失败", e);
        }

        if (loadTemplates) {
            transnos.addAll(loadTemplateTransnos());
        }

        return transnos;
    }

    /**
     * 从 classpath 下的模板目录扫描 XML 文件，提取 transno
     */
    private List<String> loadTemplateTransnos() {
        List<String> templateTransnos = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:" + templatePath + "/*.xml");
            SAXReader reader = new SAXReader();
            for (Resource resource : resources) {
                try {
                    Document doc = reader.read(resource.getInputStream());
                    Element root = doc.getRootElement();
                    String transno = root.attributeValue("transno");
                    if (transno != null && !transno.isEmpty()) {
                        templateTransnos.add(transno);
                    }
                } catch (Exception e) {
                    log.warn("解析模板文件失败: {}", resource.getFilename(), e);
                }
            }
            if (!templateTransnos.isEmpty()) {
                log.info("从模板目录加载了 {} 个模板接口", templateTransnos.size());
            }
        } catch (Exception e) {
            log.error("扫描模板目录失败", e);
        }
        return templateTransnos;
    }
}
