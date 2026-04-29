package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fintechervision.dsp.entity.InterfaceInfo;

public interface InterfaceInfoService extends IService<InterfaceInfo> {
    InterfaceInfo getByTransno(String transno);
    InterfaceInfo getByTransnoAnyStatus(String transno);
    String getActiveXmlConfig(String transno);
}
