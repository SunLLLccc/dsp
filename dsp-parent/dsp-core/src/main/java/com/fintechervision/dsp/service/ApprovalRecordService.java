package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fintechervision.dsp.entity.ApprovalRecord;

/**
 * 审批记录服务
 */
public interface ApprovalRecordService extends IService<ApprovalRecord> {

    /**
     * 提交审批申请，创建审批记录
     */
    ApprovalRecord submitApproval(String transno, Integer versionNo, String applicant);

    /**
     * 审批通过
     */
    void approve(Long recordId, String approver);

    /**
     * 审批驳回
     */
    void reject(Long recordId, String approver, String reason);

    /**
     * 查询审批记录列表
     */
    Page<ApprovalRecord> listByTransno(String transno, Integer pageNum, Integer pageSize);

    /**
     * 查询待审批记录列表
     */
    Page<ApprovalRecord> listPending(Integer pageNum, Integer pageSize);
}
