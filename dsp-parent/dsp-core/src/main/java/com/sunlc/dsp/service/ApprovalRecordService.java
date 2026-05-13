package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.ApprovalRecord;

import java.util.Map;

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

    /**
     * 提交接口申请
     */
    ApprovalRecord submitApplication(Map<String, Object> params, String applicant, Long applicantDeptId);

    /**
     * 我的申请列表
     */
    Page<ApprovalRecord> myApplications(String applicant, Integer pageNum, Integer pageSize);

    /**
     * 待我审批列表（根据当前用户的部门经理角色匹配）
     */
    Page<ApprovalRecord> pendingApproval(String username, java.util.List<String> roles, Long deptId, Integer pageNum, Integer pageSize);

    /**
     * 已审批列表
     */
    Page<ApprovalRecord> approvedList(String username, Integer pageNum, Integer pageSize);

    /**
     * 审批通过接口申请（两步审批）
     */
    void approveApplication(Long recordId, String approver);

    /**
     * 驳回接口申请
     */
    void rejectApplication(Long recordId, String approver, String reason);
}
