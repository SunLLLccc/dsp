package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.InterfaceRelation;

import java.util.List;

public interface InterfaceRelationService extends IService<InterfaceRelation> {

    Page<InterfaceRelation> getByProvider(Long deptId, String transno, Long applicantSystemId, String requirementNo,
                                          Integer pageNum, Integer pageSize);

    Page<InterfaceRelation> getByApplicant(Long deptId, String transno, Long providerSystemId, String requirementNo,
                                           Integer pageNum, Integer pageSize);

    List<InterfaceRelation> getApplicantsByTransno(String transno);

    void offline(Long relationId, String reason, String operator);
}
