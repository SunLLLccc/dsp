package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.entity.InterfaceVersion;
import com.fintechervision.dsp.mapper.InterfaceInfoMapper;
import com.fintechervision.dsp.mapper.InterfaceVersionMapper;
import com.fintechervision.dsp.service.InterfaceInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo>
        implements InterfaceInfoService {

    private final InterfaceVersionMapper interfaceVersionMapper;

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

        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno)
               .eq(InterfaceVersion::getVersionNo, info.getCurrentVersion())
               .eq(InterfaceVersion::getStatus, 3);
        InterfaceVersion version = interfaceVersionMapper.selectOne(wrapper);

        if (version == null) {
            throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "接口版本配置不存在");
        }
        return version.getXmlConfig();
    }
}
