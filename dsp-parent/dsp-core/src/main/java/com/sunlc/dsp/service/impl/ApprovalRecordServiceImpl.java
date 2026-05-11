package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.ApprovalRecord;
import com.sunlc.dsp.entity.InterfaceVersion;
import com.sunlc.dsp.enums.ApprovalStatus;
import com.sunlc.dsp.enums.VersionStatus;
import com.sunlc.dsp.mapper.ApprovalRecordMapper;
import com.sunlc.dsp.service.ApprovalRecordService;
import com.sunlc.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审批记录服务实现
 */
@Slf4j
@Service
public class ApprovalRecordServiceImpl extends ServiceImpl<ApprovalRecordMapper, ApprovalRecord>
        implements ApprovalRecordService {

    @Lazy
    @Autowired
    private InterfaceVersionService interfaceVersionService;

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
}
