package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.*;
import com.sunlc.dsp.enums.ApprovalStatus;
import com.sunlc.dsp.enums.VersionStatus;
import com.sunlc.dsp.mapper.ApprovalRecordMapper;
import com.sunlc.dsp.service.ApprovalRecordService;
import com.sunlc.dsp.service.InterfaceInfoService;
import com.sunlc.dsp.service.InterfaceVersionService;
import com.sunlc.dsp.service.SysSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审批记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRecordServiceImpl extends ServiceImpl<ApprovalRecordMapper, ApprovalRecord>
        implements ApprovalRecordService {

    @Lazy
    @Autowired
    private InterfaceVersionService interfaceVersionService;

    @Lazy
    @Autowired
    private SysSystemService sysSystemService;

    @Lazy
    @Autowired
    private InterfaceInfoService interfaceInfoService;

    @Override
    @Transactional
    public ApprovalRecord submitApproval(String transno, Integer versionNo, String applicant) {
        InterfaceVersion version = interfaceVersionService.getVersion(transno, versionNo);
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        }
        if (version.getStatus() != VersionStatus.DRAFT.getCode()) {
            throw new BusinessException(ErrorCode.VERSION_STATUS_INVALID,
                    "只有草稿状态的版本才能提交审批，当前状态: " + version.getStatus());
        }

        // 检查是否已有待审批记录
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTransno, transno)
                .eq(ApprovalRecord::getVersionNo, versionNo)
                .eq(ApprovalRecord::getStatus, ApprovalStatus.PENDING.getCode());
        if (count(wrapper) > 0) {
            throw new BusinessException(ErrorCode.APPROVAL_DUPLICATE);
        }

        // 更新版本状态为待审批
        version.setStatus(VersionStatus.PENDING.getCode());
        interfaceVersionService.updateById(version);

        // 创建审批记录
        ApprovalRecord record = new ApprovalRecord();
        record.setTransno(transno);
        record.setVersionNo(versionNo);
        record.setType(0);
        record.setStatus(ApprovalStatus.PENDING.getCode());
        record.setApplicant(applicant);
        record.setApplyTime(LocalDateTime.now());
        record.setCreatedTime(LocalDateTime.now());
        save(record);

        log.info("审批申请已提交: transno={}, versionNo={}, applicant={}", transno, versionNo, applicant);
        return record;
    }

    @Override
    @Transactional
    public void approve(Long recordId, String approver) {
        ApprovalRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND);
        }
        if (record.getStatus() != ApprovalStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED,
                    "该审批记录已处理，当前状态: " + record.getStatus());
        }

        // 更新审批记录
        record.setStatus(ApprovalStatus.APPROVED.getCode());
        record.setApprover(approver);
        record.setApproveTime(LocalDateTime.now());
        updateById(record);

        // 更新版本状态为已发布
        InterfaceVersion version = interfaceVersionService.getVersion(record.getTransno(), record.getVersionNo());
        if (version != null) {
            version.setStatus(VersionStatus.PUBLISHED.getCode());
            version.setPublishedTime(LocalDateTime.now());
            interfaceVersionService.updateById(version);
        }

        log.info("审批通过: recordId={}, transno={}, versionNo={}, approver={}",
                recordId, record.getTransno(), record.getVersionNo(), approver);
    }

    @Override
    @Transactional
    public void reject(Long recordId, String approver, String reason) {
        ApprovalRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND);
        }
        if (record.getStatus() != ApprovalStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED,
                    "该审批记录已处理，当前状态: " + record.getStatus());
        }

        // 更新审批记录
        record.setStatus(ApprovalStatus.REJECTED.getCode());
        record.setApprover(approver);
        record.setApproveTime(LocalDateTime.now());
        record.setRejectReason(reason);
        updateById(record);

        // 更新版本状态为已驳回
        InterfaceVersion version = interfaceVersionService.getVersion(record.getTransno(), record.getVersionNo());
        if (version != null) {
            version.setStatus(VersionStatus.REJECTED.getCode());
            interfaceVersionService.updateById(version);
        }

        log.info("审批驳回: recordId={}, transno={}, versionNo={}, reason={}",
                recordId, record.getTransno(), record.getVersionNo(), reason);
    }

    @Override
    public Page<ApprovalRecord> listByTransno(String transno, Integer pageNum, Integer pageSize) {
        Page<ApprovalRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTransno, transno).orderByDesc(ApprovalRecord::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    public Page<ApprovalRecord> listPending(Integer pageNum, Integer pageSize) {
        Page<ApprovalRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getStatus, ApprovalStatus.PENDING.getCode()).orderByAsc(ApprovalRecord::getApplyTime);
        return page(page, wrapper);
    }

    // ==================== 接口申请流程 ====================

    @Override
    @Transactional
    public ApprovalRecord submitApplication(Map<String, Object> params, String applicant, Long applicantDeptId) {
        Long providerSystemId = toLong(params.get("providerSystemId"));
        Long reqSystemId = toLong(params.get("reqSystemId"));

        // 查找服务方系统
        SysSystem providerSystem = sysSystemService.getById(providerSystemId);
        if (providerSystem == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务方系统不存在");
        }

        // 查找请求方系统
        SysSystem reqSystem = sysSystemService.getById(reqSystemId);
        if (reqSystem == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求方系统不存在");
        }

        // 获取接口信息
        Long interfaceId = toLong(params.get("interfaceId"));
        String transno = (String) params.get("transno");
        if (interfaceId == null && (transno == null || transno.isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择接口");
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setType(1); // 接口申请
        record.setStatus(ApprovalStatus.PENDING.getCode());
        record.setApplicant(applicant);
        record.setApplicantDeptId(applicantDeptId);
        record.setApplicantSystemId(reqSystemId);
        record.setProviderSystemId(providerSystemId);
        record.setCurrentStep(1); // 第一步：服务方部门经理审批

        if (interfaceId != null) {
            record.setTransno(transno != null ? transno : "");
        }

        record.setRequirementNo((String) params.get("reqNo"));
        record.setRequirementDesc((String) params.get("reqDesc"));
        record.setApplyReason((String) params.get("applyReason"));
        record.setDownstreamInfo((String) params.get("downstreamInfo"));
        record.setApplyTime(LocalDateTime.now());
        record.setCreatedTime(LocalDateTime.now());

        save(record);

        log.info("接口申请已提交: applicant={}, reqSystem={}, providerSystem={}, reqNo={}",
                applicant, reqSystem.getName(), providerSystem.getName(), record.getRequirementNo());
        return record;
    }

    @Override
    public Page<ApprovalRecord> myApplications(String applicant, Integer pageNum, Integer pageSize) {
        Page<ApprovalRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getType, 1)
                .eq(ApprovalRecord::getApplicant, applicant)
                .orderByDesc(ApprovalRecord::getCreatedTime);
        Page<ApprovalRecord> result = page(page, wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    @Override
    public Page<ApprovalRecord> pendingApproval(String username, List<String> roles, Long deptId, Integer pageNum, Integer pageSize) {
        Page<ApprovalRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getType, 1)
                .eq(ApprovalRecord::getStatus, ApprovalStatus.PENDING.getCode());

        boolean isAdmin = roles != null && roles.contains("ADMIN");
        if (isAdmin) {
            // admin 能看到所有待审批
        } else {
            // 部门经理只能看到与本部门相关的审批
            wrapper.and(w -> {
                // 第一步：服务方系统属于本部门
                w.inSql(ApprovalRecord::getProviderSystemId,
                        "SELECT id FROM sys_system WHERE dept_id = " + deptId + " AND (deleted IS NULL OR deleted = 0)")
                        .eq(ApprovalRecord::getCurrentStep, 1)
                        .or(sub -> sub
                                // 第二步：申请方系统属于本部门
                                .inSql(ApprovalRecord::getApplicantSystemId,
                                        "SELECT id FROM sys_system WHERE dept_id = " + deptId + " AND (deleted IS NULL OR deleted = 0)")
                                .eq(ApprovalRecord::getCurrentStep, 2));
            });
        }

        wrapper.orderByAsc(ApprovalRecord::getApplyTime);
        Page<ApprovalRecord> result = page(page, wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    @Override
    public Page<ApprovalRecord> approvedList(String username, Integer pageNum, Integer pageSize) {
        Page<ApprovalRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getType, 1)
                .ne(ApprovalRecord::getStatus, ApprovalStatus.PENDING.getCode())
                .orderByDesc(ApprovalRecord::getCreatedTime);
        Page<ApprovalRecord> result = page(page, wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    @Override
    @Transactional
    public void approveApplication(Long recordId, String approver) {
        ApprovalRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND);
        }
        if (record.getStatus() != ApprovalStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED);
        }

        if (record.getCurrentStep() == 1) {
            // 第一步通过：记录审批人，进入第二步
            record.setApprover(approver);
            record.setApproveTime(LocalDateTime.now());
            record.setCurrentStep(2);
            updateById(record);
            log.info("接口申请第一步通过: recordId={}, approver={}", recordId, approver);
        } else if (record.getCurrentStep() == 2) {
            // 第二步通过：审批完成
            record.setStatus(ApprovalStatus.APPROVED.getCode());
            record.setApprover2(approver);
            record.setApproveTime2(LocalDateTime.now());
            updateById(record);
            log.info("接口申请审批完成: recordId={}, approver={}", recordId, approver);
        }
    }

    @Override
    @Transactional
    public void rejectApplication(Long recordId, String approver, String reason) {
        ApprovalRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND);
        }
        if (record.getStatus() != ApprovalStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED);
        }

        record.setStatus(ApprovalStatus.REJECTED.getCode());
        if (record.getCurrentStep() == 1) {
            record.setApprover(approver);
            record.setApproveTime(LocalDateTime.now());
        } else {
            record.setApprover2(approver);
            record.setApproveTime2(LocalDateTime.now());
        }
        record.setRejectReason(reason);
        updateById(record);

        log.info("接口申请驳回: recordId={}, step={}, approver={}, reason={}",
                recordId, record.getCurrentStep(), approver, reason);
    }

    private void fillDisplayFields(List<ApprovalRecord> records) {
        for (ApprovalRecord record : records) {
            if (record.getApplicantSystemId() != null) {
                SysSystem sys = sysSystemService.getById(record.getApplicantSystemId());
                if (sys != null) record.setApplicantSystemName(sys.getName());
            }
            if (record.getProviderSystemId() != null) {
                SysSystem sys = sysSystemService.getById(record.getProviderSystemId());
                if (sys != null) record.setProviderSystemName(sys.getName());
            }
            if (record.getTransno() != null && !record.getTransno().isEmpty()) {
                LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InterfaceInfo::getTransno, record.getTransno()).last("LIMIT 1");
                InterfaceInfo info = interfaceInfoService.getOne(wrapper);
                if (info != null) record.setInterfaceName(info.getTransno() + " - " + info.getName());
            }
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
