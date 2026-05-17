package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.InterfaceRelation;
import com.sunlc.dsp.entity.SysSystem;
import com.sunlc.dsp.mapper.InterfaceRelationMapper;
import com.sunlc.dsp.service.InterfaceRelationService;
import com.sunlc.dsp.service.SysSystemService;
import com.sunlc.dsp.service.InterfaceInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterfaceRelationServiceImpl extends ServiceImpl<InterfaceRelationMapper, InterfaceRelation>
        implements InterfaceRelationService {

    private final SysSystemService sysSystemService;
    private final InterfaceInfoService interfaceInfoService;

    @Override
    public Page<InterfaceRelation> pageList(String transno, Long applicantSystemId, Long providerSystemId,
                                             Integer status, Integer pageNum, Integer pageSize) {
        Page<InterfaceRelation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceRelation> wrapper = new LambdaQueryWrapper<>();
        if (transno != null && !transno.isEmpty()) {
            wrapper.eq(InterfaceRelation::getTransno, transno);
        }
        if (applicantSystemId != null) {
            wrapper.eq(InterfaceRelation::getApplicantSystemId, applicantSystemId);
        }
        if (providerSystemId != null) {
            wrapper.eq(InterfaceRelation::getProviderSystemId, providerSystemId);
        }
        if (status != null) {
            wrapper.eq(InterfaceRelation::getStatus, status);
        }
        wrapper.orderByDesc(InterfaceRelation::getCreatedTime);

        Page<InterfaceRelation> result = page(page, wrapper);
        // 填充展示字段
        for (InterfaceRelation relation : result.getRecords()) {
            fillDisplayFields(relation);
        }
        return result;
    }

    private void fillDisplayFields(InterfaceRelation relation) {
        if (relation.getProviderSystemId() != null) {
            SysSystem system = sysSystemService.getById(relation.getProviderSystemId());
            if (system != null) {
                relation.setProviderSystemName(system.getName());
            }
        }
        if (relation.getApplicantSystemId() != null) {
            SysSystem system = sysSystemService.getById(relation.getApplicantSystemId());
            if (system != null) {
                relation.setApplicantSystemName(system.getName());
            }
        }
        if (relation.getTransno() != null) {
            InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(relation.getTransno());
            if (info != null) {
                relation.setInterfaceName(info.getName());
            }
        }
    }
}
