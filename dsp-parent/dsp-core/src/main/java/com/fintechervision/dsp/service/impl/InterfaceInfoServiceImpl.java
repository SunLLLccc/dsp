package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.entity.InterfaceTemplate;
import com.fintechervision.dsp.mapper.InterfaceInfoMapper;
import com.fintechervision.dsp.mapper.InterfaceTemplateMapper;
import com.fintechervision.dsp.service.InterfaceInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo>
        implements InterfaceInfoService {

    private final InterfaceTemplateMapper interfaceTemplateMapper;

    @Override
    public InterfaceInfo getByTransno(String transno) {
        LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceInfo::getTransno, transno)
               .eq(InterfaceInfo::getStatus, 1);
        return getOne(wrapper);
    }

    @Override
    public InterfaceInfo getByTransnoAnyStatus(String transno) {
        LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceInfo::getTransno, transno);
        return getOne(wrapper);
    }

    @Override
    public String getActiveXmlConfig(String transno) {
        InterfaceInfo info = getByTransno(transno);
        if (info == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND);
        }

        LambdaQueryWrapper<InterfaceTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceTemplate::getTransno, transno)
               .eq(InterfaceTemplate::getStatus, 1);
        InterfaceTemplate template = interfaceTemplateMapper.selectOne(wrapper);

        if (template == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "接口模板配置不存在");
        }
        return template.getXmlContent();
    }
}
