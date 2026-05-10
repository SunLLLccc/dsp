package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.common.service.XmlConfigCacheInvalidator;
import com.fintechervision.dsp.entity.ApprovalRecord;
import com.fintechervision.dsp.entity.InterfaceInfo;
import com.fintechervision.dsp.entity.InterfaceVersion;
import com.fintechervision.dsp.enums.ApprovalStatus;
import com.fintechervision.dsp.enums.InterfaceStatus;
import com.fintechervision.dsp.enums.VersionStatus;
import com.fintechervision.dsp.mapper.InterfaceVersionMapper;
import com.fintechervision.dsp.service.ApprovalRecordService;
import com.fintechervision.dsp.service.InterfaceInfoService;
import com.fintechervision.dsp.service.InterfaceVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterfaceVersionServiceImpl extends ServiceImpl<InterfaceVersionMapper, InterfaceVersion>
        implements InterfaceVersionService {

    private final InterfaceInfoService interfaceInfoService;
    private final ApprovalRecordService approvalRecordService;
    private final XmlConfigCacheInvalidator xmlConfigCacheInvalidator;

    @Override
    public InterfaceVersion saveSchema(String transno, String inputSchema, String outputSchema, String changeLog, String operator) {
        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno).orderByDesc(InterfaceVersion::getVersionNo).last("LIMIT 1");
        InterfaceVersion lastVersion = getOne(wrapper);
        int nextVersion = (lastVersion != null) ? lastVersion.getVersionNo() + 1 : 1;
        InterfaceVersion version = new InterfaceVersion();
        version.setTransno(transno);
        version.setVersionNo(nextVersion);
        version.setInputSchema(inputSchema);
        version.setOutputSchema(outputSchema);
        version.setChangeLog(changeLog);
        version.setStatus(VersionStatus.DRAFT.getCode());
        version.setCreatedBy(operator);
        version.setCreatedTime(LocalDateTime.now());
        save(version);
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        return version;
    }

    @Override
    public Page<InterfaceVersion> versionList(String transno, Integer pageNum, Integer pageSize) {
        Page<InterfaceVersion> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno)
               .eq(InterfaceVersion::getStatus, VersionStatus.PUBLISHED.getCode())
               .orderByDesc(InterfaceVersion::getVersionNo);
        return page(page, wrapper);
    }

    @Override
    public InterfaceVersion getVersion(String transno, Integer versionNo) {
        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno).eq(InterfaceVersion::getVersionNo, versionNo);
        return getOne(wrapper);
    }

    @Override
    public void submitApproval(String transno, Integer versionNo, String operator) {
        // 检查是否已有待审批记录
        LambdaQueryWrapper<ApprovalRecord> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ApprovalRecord::getTransno, transno).eq(ApprovalRecord::getStatus, ApprovalStatus.PENDING.getCode());
        if (approvalRecordService.count(checkWrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该接口已有待审批记录，请先撤销或等待审批完成");
        }

        approvalRecordService.submitApproval(transno, versionNo, operator);
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(InterfaceStatus.PENDING.getCode());
            info.setUpdatedBy(operator);
            interfaceInfoService.updateById(info);
        }
        log.info("接口提交审批: transno={}, version={}", transno, versionNo);
    }

    private ApprovalRecord findPendingRecord(String transno) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTransno, transno).eq(ApprovalRecord::getStatus, ApprovalStatus.PENDING.getCode());
        ApprovalRecord record = approvalRecordService.getOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND, "未找到该接口的待审批记录");
        }
        return record;
    }

    @Override
    public void approveAndPublish(String transno, String approver) {
        ApprovalRecord record = findPendingRecord(transno);
        approvalRecordService.approve(record.getId(), approver);

        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setCurrentVersion(record.getVersionNo());
            info.setStatus(InterfaceStatus.PUBLISHED.getCode());
            info.setUpdatedBy(approver);
            interfaceInfoService.updateById(info);
        }
        log.info("接口审批通过并发布: transno={}, version={}, approver={}", transno, record.getVersionNo(), approver);
        xmlConfigCacheInvalidator.invalidate(transno);
    }

    @Override
    public void rejectApproval(String transno, String reason, String operator) {
        ApprovalRecord record = findPendingRecord(transno);
        approvalRecordService.reject(record.getId(), null, reason);

        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(InterfaceStatus.DRAFT.getCode());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        log.info("接口审批驳回: transno={}, version={}, reason={}", transno, record.getVersionNo(), reason);
    }

    @Override
    public void offline(String transno, String operator) {
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(InterfaceStatus.OFFLINE.getCode());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        log.info("接口下线: transno={}", transno);
        xmlConfigCacheInvalidator.invalidate(transno);
    }

    @Override
    public void withdrawApproval(String transno, String operator) {
        ApprovalRecord record = findPendingRecord(transno);
        approvalRecordService.removeById(record.getId());

        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(InterfaceStatus.DRAFT.getCode());
            info.setUpdatedBy(operator);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        log.info("撤销审批: transno={}, version={}", transno, record.getVersionNo());
    }

    @Override
    public InterfaceVersion getLatestVisibleVersion(String transno, String currentUser) {
        // 查最新一条版本（不限状态）
        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno)
               .orderByDesc(InterfaceVersion::getVersionNo)
               .last("LIMIT 1");
        InterfaceVersion latest = getOne(wrapper);

        if (latest == null) return null;

        // 如果最新是草稿且不是自己的，返回最新已发布版本
        if (latest.getStatus() == VersionStatus.DRAFT.getCode()
                && (currentUser == null || !currentUser.equals(latest.getCreatedBy()))) {
            LambdaQueryWrapper<InterfaceVersion> pubWrapper = new LambdaQueryWrapper<>();
            pubWrapper.eq(InterfaceVersion::getTransno, transno)
                      .eq(InterfaceVersion::getStatus, VersionStatus.PUBLISHED.getCode())
                      .orderByDesc(InterfaceVersion::getVersionNo)
                      .last("LIMIT 1");
            return getOne(pubWrapper);
        }
        return latest;
    }
}
