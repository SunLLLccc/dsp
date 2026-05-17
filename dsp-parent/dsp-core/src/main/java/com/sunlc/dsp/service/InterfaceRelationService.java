package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.InterfaceRelation;

public interface InterfaceRelationService extends IService<InterfaceRelation> {

    /**
     * 分页查询接口关系
     */
    Page<InterfaceRelation> pageList(String transno, Long applicantSystemId, Long providerSystemId,
                                     Integer status, Integer pageNum, Integer pageSize);
}
