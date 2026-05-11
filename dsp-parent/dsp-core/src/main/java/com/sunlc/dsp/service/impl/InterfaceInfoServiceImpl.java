package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.InterfaceTemplate;
import com.sunlc.dsp.enums.InterfaceStatus;
import com.sunlc.dsp.mapper.InterfaceInfoMapper;
import com.sunlc.dsp.mapper.InterfaceTemplateMapper;
import com.sunlc.dsp.service.InterfaceInfoService;
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
               .eq(InterfaceInfo::getStatus, InterfaceStatus.PUBLISHED.getCode());
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
               .eq(InterfaceTemplate::getStatus, InterfaceStatus.PUBLISHED.getCode());
        InterfaceTemplate template = interfaceTemplateMapper.selectOne(wrapper);

        if (template == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "接口模板配置不存在");
        }
        return template.getXmlContent();
    }
}
