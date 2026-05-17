package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.InterfaceRelation;

public interface InterfaceRelationService extends IService<InterfaceRelation> {

    Page<InterfaceRelation> getByProvider(Long deptId, Integer pageNum, Integer pageSize);

    Page<InterfaceRelation> getByApplicant(Long deptId, Integer pageNum, Integer pageSize);

    void offline(Long relationId, String reason, String operator);
}
