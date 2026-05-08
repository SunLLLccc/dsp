package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fintechervision.dsp.entity.InterfaceTemplate;
import com.fintechervision.dsp.entity.InterfaceTemplateHistory;

import java.util.List;

public interface InterfaceTemplateService extends IService<InterfaceTemplate> {

    Page<InterfaceTemplate> listTemplates(String transno, String systemName, Integer status,
                                          Integer pageNum, Integer pageSize);

    InterfaceTemplate createTemplate(String transno, String xmlContent, String changeLog, String operator);

    InterfaceTemplate updateTemplate(Long id, String xmlContent, String changeLog, String operator);

    void publishTemplate(Long id, String operator);

    void offlineTemplate(Long id);

    String generateXmlFromSchema(String transno);

    Page<InterfaceTemplateHistory> historyList(Long templateId, Integer pageNum, Integer pageSize);

    List<InterfaceTemplateHistory> getHistoryByTransno(String transno);

    InterfaceTemplate getByTransno(String transno);
}
