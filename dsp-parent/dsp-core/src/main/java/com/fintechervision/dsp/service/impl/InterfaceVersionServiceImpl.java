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
        version.setStatus(0);
        version.setCreatedBy(operator);
        version.setCreatedTime(LocalDateTime.now());
        save(version);
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) { info.setUpdatedTime(LocalDateTime.now()); interfaceInfoService.updateById(info); }
        return version;
    }

    @Override
    public Page<InterfaceVersion> versionList(String transno, Integer pageNum, Integer pageSize) {
        Page<InterfaceVersion> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InterfaceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceVersion::getTransno, transno).orderByDesc(InterfaceVersion::getVersionNo);
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
        approvalRecordService.submitApproval(transno, versionNo, operator);
        // 将接口状态改为待审批
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(3);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        log.info("接口提交审批: transno={}, version={}", transno, versionNo);
    }

    @Override
    public void approveAndPublish(String transno, Integer versionNo, String approver) {
        // 查找该版本的待审批记录
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTransno, transno)
                .eq(ApprovalRecord::getVersionNo, versionNo)
                .eq(ApprovalRecord::getStatus, 0);
        ApprovalRecord record = approvalRecordService.getOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND, "未找到该版本的待审批记录");
        }

        // 审批通过（会更新审批记录和版本状态）
        approvalRecordService.approve(record.getId(), approver);

        // 更新接口信息为已发布
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setCurrentVersion(versionNo);
            info.setStatus(1);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        log.info("接口审批通过并发布: transno={}, version={}, approver={}", transno, versionNo, approver);
        xmlConfigCacheInvalidator.invalidate(transno);
    }

    @Override
    public void rejectApproval(String transno, Integer versionNo, String reason) {
        // 查找该版本的待审批记录
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTransno, transno)
                .eq(ApprovalRecord::getVersionNo, versionNo)
                .eq(ApprovalRecord::getStatus, 0);
        ApprovalRecord record = approvalRecordService.getOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND, "未找到该版本的待审批记录");
        }

        // 审批驳回（会更新审批记录和版本状态）
        approvalRecordService.reject(record.getId(), null, reason);

        // 接口状态改回草稿
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(0);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }

        log.info("接口审批驳回: transno={}, version={}, reason={}", transno, versionNo, reason);
    }

    @Override
    public void offline(String transno) {
        LambdaQueryWrapper<InterfaceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceInfo::getTransno, transno);
        InterfaceInfo info = interfaceInfoService.getOne(wrapper);
        if (info != null) { info.setStatus(2); info.setUpdatedTime(LocalDateTime.now()); interfaceInfoService.updateById(info); }
        log.info("接口下线: transno={}", transno);
        xmlConfigCacheInvalidator.invalidate(transno);
    }

    @Override
    public void withdrawApproval(String transno, Integer versionNo) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTransno, transno)
                .eq(ApprovalRecord::getVersionNo, versionNo)
                .eq(ApprovalRecord::getStatus, 0);
        ApprovalRecord record = approvalRecordService.getOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_RECORD_NOT_FOUND, "未找到待审批记录");
        }
        // 删除审批记录
        approvalRecordService.removeById(record.getId());

        // 接口状态改回草稿
        InterfaceInfo info = interfaceInfoService.getByTransnoAnyStatus(transno);
        if (info != null) {
            info.setStatus(0);
            info.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(info);
        }
        log.info("撤销审批: transno={}, version={}", transno, versionNo);
    }
}
