package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.InterfaceInfo;
import com.sunlc.dsp.entity.InterfaceRelation;
import com.sunlc.dsp.entity.SysSystem;
import com.sunlc.dsp.mapper.InterfaceRelationMapper;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceRelationService;
import com.sunlc.dsp.service.SysSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterfaceRelationServiceImpl extends ServiceImpl<InterfaceRelationMapper, InterfaceRelation>
        implements InterfaceRelationService {

    private final SysSystemService sysSystemService;
    private final InterfaceInfoService interfaceInfoService;

    @Override
    public Page<InterfaceRelation> getByProvider(Long deptId, boolean isAdmin, String transno, Long providerSystemId,
                                                  Long applicantSystemId, String requirementNo,
                                                  Integer pageNum, Integer pageSize) {
        Page<InterfaceRelation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceRelation> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin && deptId != null) {
            wrapper.inSql(InterfaceRelation::getProviderSystemId,
                    "SELECT id FROM sys_system WHERE dept_id = " + deptId + " AND status = 1");
        }
        if (transno != null && !transno.isEmpty()) {
            wrapper.like(InterfaceRelation::getTransno, transno);
        }
        if (providerSystemId != null) {
            wrapper.eq(InterfaceRelation::getProviderSystemId, providerSystemId);
        }
        if (applicantSystemId != null) {
            wrapper.eq(InterfaceRelation::getApplicantSystemId, applicantSystemId);
        }
        if (requirementNo != null && !requirementNo.isEmpty()) {
            wrapper.like(InterfaceRelation::getRequirementNo, requirementNo);
        }
        wrapper.orderByDesc(InterfaceRelation::getCreatedTime);
        Page<InterfaceRelation> result = page(page, wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    @Override
    public Page<InterfaceRelation> getByApplicant(Long deptId, boolean isAdmin, String transno, Long providerSystemId,
                                                   Long applicantSystemId, String requirementNo,
                                                   Integer pageNum, Integer pageSize) {
        Page<InterfaceRelation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceRelation> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin && deptId != null) {
            wrapper.inSql(InterfaceRelation::getApplicantSystemId,
                    "SELECT id FROM sys_system WHERE dept_id = " + deptId + " AND status = 1");
        }
        if (transno != null && !transno.isEmpty()) {
            wrapper.like(InterfaceRelation::getTransno, transno);
        }
        if (providerSystemId != null) {
            wrapper.eq(InterfaceRelation::getProviderSystemId, providerSystemId);
        }
        if (applicantSystemId != null) {
            wrapper.eq(InterfaceRelation::getApplicantSystemId, applicantSystemId);
        }
        if (requirementNo != null && !requirementNo.isEmpty()) {
            wrapper.like(InterfaceRelation::getRequirementNo, requirementNo);
        }
        wrapper.orderByDesc(InterfaceRelation::getCreatedTime);
        Page<InterfaceRelation> result = page(page, wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    @Override
    public List<InterfaceRelation> getApplicantsByTransno(String transno) {
        LambdaQueryWrapper<InterfaceRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceRelation::getTransno, transno)
               .eq(InterfaceRelation::getStatus, 1)
               .orderByDesc(InterfaceRelation::getCreatedTime);
        List<InterfaceRelation> list = list(wrapper);
        fillDisplayFields(list);
        return list;
    }

    @Override
    @Transactional
    public void offline(Long relationId, String reason, String operator) {
        InterfaceRelation relation = getById(relationId);
        if (relation == null) {
            throw new BusinessException(ErrorCode.RELATION_NOT_FOUND);
        }
        relation.setStatus(2);
        relation.setOfflineTime(LocalDateTime.now());
        relation.setOfflineReason(reason);
        updateById(relation);
        log.info("接口关系下线: relationId={}, transno={}, operator={}", relationId, relation.getTransno(), operator);
    }

    private void fillDisplayFields(List<InterfaceRelation> records) {
        for (InterfaceRelation r : records) {
            if (r.getProviderSystemId() != null) {
                SysSystem sys = sysSystemService.getById(r.getProviderSystemId());
                if (sys != null) r.setProviderSystemName(sys.getName());
            }
            if (r.getApplicantSystemId() != null) {
                SysSystem sys = sysSystemService.getById(r.getApplicantSystemId());
                if (sys != null) r.setApplicantSystemName(sys.getName());
            }
            if (r.getTransno() != null) {
                LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InterfaceInfo::getTransno, r.getTransno()).last("LIMIT 1");
                InterfaceInfo info = interfaceInfoService.getOne(wrapper);
                if (info != null) r.setInterfaceName(info.getTransno() + " - " + info.getName());
            }
        }
    }
}
