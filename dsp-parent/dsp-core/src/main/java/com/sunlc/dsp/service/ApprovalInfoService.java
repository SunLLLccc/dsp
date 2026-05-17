package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.ApprovalFlow;
import com.sunlc.dsp.entity.ApprovalInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ApprovalInfoService extends IService<ApprovalInfo> {

    /**
     * 提交审批
     */
    ApprovalInfo submit(Integer type, Map<String, Object> params, String applicant);

    /**
     * 审批通过
     */
    void approve(Long approvalId, String approver, String approverName);

    /**
     * 审批驳回
     */
    void reject(Long approvalId, String approver, String approverName, String reason);

    /**
     * 撤回审批（仅申请人、待审批状态可撤回）
     */
    void withdraw(Long approvalId, String applicant);

    /**
     * 我的提交记录
     */
    Page<ApprovalInfo> mySubmissions(String applicant, Integer type, Integer status,
                                      LocalDateTime startDate, LocalDateTime endDate,
                                      Integer pageNum, Integer pageSize);

    /**
     * 待我审批
     */
    Page<ApprovalInfo> pendingApproval(Long deptId, List<String> roles,
                                        Integer pageNum, Integer pageSize);

    /**
     * 已审批历史
     */
    Page<ApprovalInfo> approvedHistory(Long deptId, List<String> roles,
                                        LocalDateTime startDate, LocalDateTime endDate,
                                        Integer pageNum, Integer pageSize);

    /**
     * 获取审批流程明细
     */
    List<ApprovalFlow> getFlowDetail(Long approvalId);

    /**
     * 获取审批单详情（含流程步骤、系统名称等展示字段）
     */
    ApprovalInfo getDetail(Long approvalId);

    /**
     * 按条件查询流程步骤
     */
    List<ApprovalFlow> listFlows(LambdaQueryWrapper<ApprovalFlow> wrapper);
}
