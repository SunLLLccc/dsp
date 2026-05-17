package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.*;
import com.sunlc.dsp.enums.ApprovalType;
import com.sunlc.dsp.enums.InterfaceStatus;
import com.sunlc.dsp.enums.VersionStatus;
import com.sunlc.dsp.mapper.ApprovalFlowMapper;
import com.sunlc.dsp.mapper.ApprovalInfoMapper;
import com.sunlc.dsp.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalInfoServiceImpl extends ServiceImpl<ApprovalInfoMapper, ApprovalInfo>
        implements ApprovalInfoService {

    private final ApprovalFlowMapper approvalFlowMapper;
    private final InterfaceInfoService interfaceInfoService;
    private final InterfaceVersionService interfaceVersionService;
    private final SysSystemService sysSystemService;

    @Lazy
    @Autowired
    private InterfaceRelationService interfaceRelationService;

    private static final DateTimeFormatter APPROVAL_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInfo submit(Integer type, Map<String, Object> params, String applicant) {
        ApprovalType approvalType = ApprovalType.fromCode(type);

        // 构建审批单基本信息
        ApprovalInfo info = new ApprovalInfo();
        info.setApprovalNo(generateApprovalNo());
        info.setType(type);
        info.setStatus(0); // 0=待审批
        info.setApplicant(applicant);
        info.setApplicantName((String) params.get("applicantName"));
        info.setApplyTime(LocalDateTime.now());
        info.setCreatedTime(LocalDateTime.now());
        info.setUpdatedTime(LocalDateTime.now());

        // 根据审批类型设置字段
        switch (approvalType) {
            case NEW_INTERFACE:
                fillNewInterfaceFields(info, params);
                break;
            case MODIFY_INTERFACE:
                fillModifyInterfaceFields(info, params);
                break;
            case APPLY_INTERFACE:
                fillApplyInterfaceFields(info, params);
                break;
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的审批类型");
        }

        // 保存审批单
        save(info);

        // 创建审批流程步骤
        List<ApprovalFlow> flows = createApprovalFlows(info, approvalType, params);
        for (ApprovalFlow flow : flows) {
            flow.setApprovalId(info.getId());
            flow.setCreatedTime(LocalDateTime.now());
            approvalFlowMapper.insert(flow);
        }

        // 更新接口状态为待审批
        updateInterfaceStatusToPending(info);

        log.info("提交审批: approvalNo={}, type={}, applicant={}", info.getApprovalNo(), approvalType, applicant);
        return info;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long approvalId, String approver, String approverName) {
        ApprovalInfo info = getById(approvalId);
        if (info == null) {
            throw new BusinessException(ErrorCode.APPROVAL_INFO_NOT_FOUND);
        }
        if (info.getStatus() != 0) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING);
        }

        // 找到当前待审批步骤
        ApprovalFlow currentFlow = findCurrentFlow(approvalId);
        if (currentFlow == null) {
            throw new BusinessException(ErrorCode.APPROVAL_FLOW_NOT_FOUND);
        }

        // 更新当前步骤为通过
        currentFlow.setStatus(1); // 1=通过
        currentFlow.setApprover(approver);
        currentFlow.setApproverName(approverName);
        currentFlow.setApproveTime(LocalDateTime.now());
        approvalFlowMapper.updateById(currentFlow);

        // 检查是否还有下一步
        ApprovalFlow nextFlow = findNextPendingFlow(approvalId, currentFlow.getStepNo());
        if (nextFlow == null) {
            // 全部通过，执行业务操作
            info.setStatus(1); // 1=已通过
            info.setUpdatedTime(LocalDateTime.now());
            updateById(info);
            executeBusinessAction(info);
            log.info("审批全部通过: approvalId={}, approvalNo={}", approvalId, info.getApprovalNo());
        } else {
            log.info("当前步骤通过，进入下一步: approvalId={}, nextStep={}", approvalId, nextFlow.getStepNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long approvalId, String approver, String approverName, String reason) {
        ApprovalInfo info = getById(approvalId);
        if (info == null) {
            throw new BusinessException(ErrorCode.APPROVAL_INFO_NOT_FOUND);
        }
        if (info.getStatus() != 0) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING);
        }

        // 找到当前待审批步骤
        ApprovalFlow currentFlow = findCurrentFlow(approvalId);
        if (currentFlow == null) {
            throw new BusinessException(ErrorCode.APPROVAL_FLOW_NOT_FOUND);
        }

        // 标记当前步骤为驳回
        currentFlow.setStatus(2); // 2=驳回
        currentFlow.setApprover(approver);
        currentFlow.setApproverName(approverName);
        currentFlow.setApproveTime(LocalDateTime.now());
        currentFlow.setRejectReason(reason);
        approvalFlowMapper.updateById(currentFlow);

        // 标记审批单为已驳回
        info.setStatus(2); // 2=已驳回
        info.setUpdatedTime(LocalDateTime.now());
        updateById(info);

        // 恢复接口状态
        revertInterfaceStatus(info);

        log.info("审批驳回: approvalId={}, reason={}", approvalId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long approvalId, String applicant) {
        ApprovalInfo info = getById(approvalId);
        if (info == null) {
            throw new BusinessException(ErrorCode.APPROVAL_INFO_NOT_FOUND);
        }
        if (info.getStatus() != 0) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING, "只有待审批状态的审批单可以撤回");
        }
        if (!info.getApplicant().equals(applicant)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有申请人可以撤回审批单");
        }

        // 标记审批单为已撤回
        info.setStatus(3); // 3=已撤回
        info.setWithdrawTime(LocalDateTime.now());
        info.setUpdatedTime(LocalDateTime.now());
        updateById(info);

        // 恢复接口状态
        revertInterfaceStatus(info);

        log.info("撤回审批: approvalId={}, applicant={}", approvalId, applicant);
    }

    @Override
    public Page<ApprovalInfo> mySubmissions(String applicant, Integer type, Integer status,
                                             LocalDateTime startDate, LocalDateTime endDate,
                                             Integer pageNum, Integer pageSize) {
        Page<ApprovalInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalInfo::getApplicant, applicant);
        if (type != null) {
            wrapper.eq(ApprovalInfo::getType, type);
        }
        if (status != null) {
            wrapper.eq(ApprovalInfo::getStatus, status);
        }
        if (startDate != null) {
            wrapper.ge(ApprovalInfo::getApplyTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(ApprovalInfo::getApplyTime, endDate);
        }
        wrapper.orderByDesc(ApprovalInfo::getCreatedTime);

        Page<ApprovalInfo> result = page(page, wrapper);
        // 填充展示字段
        for (ApprovalInfo info : result.getRecords()) {
            fillDisplayFields(info);
        }
        return result;
    }

    @Override
    public Page<ApprovalInfo> pendingApproval(Long deptId, List<String> roles,
                                               Integer pageNum, Integer pageSize) {
        boolean isAdmin = roles != null && roles.contains("ADMIN");

        // 先通过 approval_flow 找到当前用户部门待审批的 approvalId 列表
        LambdaQueryWrapper<ApprovalFlow> flowWrapper = new LambdaQueryWrapper<>();
        flowWrapper.eq(ApprovalFlow::getStatus, 0); // 待审批步骤
        if (!isAdmin) {
            flowWrapper.eq(ApprovalFlow::getDeptId, deptId);
        }
        List<ApprovalFlow> flows = approvalFlowMapper.selectList(flowWrapper);

        if (flows.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 过滤出确实属于当前步骤的 approvalId（即该步骤之前没有待审批步骤，或者就是第一个待审批步骤）
        Set<Long> approvalIds = new HashSet<>();
        for (ApprovalFlow flow : flows) {
            ApprovalFlow currentStep = findCurrentFlow(flow.getApprovalId());
            if (currentStep != null && currentStep.getId().equals(flow.getId())) {
                approvalIds.add(flow.getApprovalId());
            }
        }

        if (approvalIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 查询这些审批单且状态为待审批
        Page<ApprovalInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ApprovalInfo::getId, approvalIds)
               .eq(ApprovalInfo::getStatus, 0)
               .orderByDesc(ApprovalInfo::getCreatedTime);

        Page<ApprovalInfo> result = page(page, wrapper);
        for (ApprovalInfo info : result.getRecords()) {
            fillDisplayFields(info);
        }
        return result;
    }

    @Override
    public Page<ApprovalInfo> approvedHistory(Long deptId, List<String> roles,
                                               LocalDateTime startDate, LocalDateTime endDate,
                                               Integer pageNum, Integer pageSize) {
        boolean isAdmin = roles != null && roles.contains("ADMIN");

        // 通过 approval_flow 找到本部门审批过的 approvalId（不在此处过滤日期，日期在 approval_info 层过滤）
        LambdaQueryWrapper<ApprovalFlow> flowWrapper = new LambdaQueryWrapper<>();
        flowWrapper.in(ApprovalFlow::getStatus, Arrays.asList(1, 2)); // 通过或驳回
        if (!isAdmin) {
            flowWrapper.eq(ApprovalFlow::getDeptId, deptId);
        }
        List<ApprovalFlow> flows = approvalFlowMapper.selectList(flowWrapper);

        if (flows.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        Set<Long> approvalIds = flows.stream()
                .map(ApprovalFlow::getApprovalId)
                .collect(Collectors.toSet());

        Page<ApprovalInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ApprovalInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ApprovalInfo::getId, approvalIds)
               .ne(ApprovalInfo::getStatus, 0); // 排除待审批
        if (startDate != null) {
            wrapper.ge(ApprovalInfo::getApplyTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(ApprovalInfo::getApplyTime, endDate);
        }
        wrapper.orderByDesc(ApprovalInfo::getUpdatedTime);

        Page<ApprovalInfo> result = page(page, wrapper);
        for (ApprovalInfo info : result.getRecords()) {
            fillDisplayFields(info);
        }
        return result;
    }

    @Override
    public List<ApprovalFlow> getFlowDetail(Long approvalId) {
        LambdaQueryWrapper<ApprovalFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalFlow::getApprovalId, approvalId)
               .orderByAsc(ApprovalFlow::getStepNo);
        return approvalFlowMapper.selectList(wrapper);
    }

    @Override
    public ApprovalInfo getDetail(Long approvalId) {
        ApprovalInfo info = getById(approvalId);
        if (info == null) {
            throw new BusinessException(ErrorCode.APPROVAL_INFO_NOT_FOUND);
        }
        fillDisplayFields(info);
        info.setFlows(getFlowDetail(approvalId));
        return info;
    }

    // ==================== 私有方法 ====================

    /**
     * 生成审批单号: AP + yyyyMMddHHmmss + 4位序号
     */
    private String generateApprovalNo() {
        String timestamp = LocalDateTime.now().format(APPROVAL_NO_FORMATTER);
        // 查询当天已有的最大序号
        LambdaQueryWrapper<ApprovalInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(ApprovalInfo::getApprovalNo, "AP" + timestamp.substring(0, 8))
               .orderByDesc(ApprovalInfo::getApprovalNo)
               .last("LIMIT 1");
        ApprovalInfo last = getOne(wrapper);

        int seq = 1;
        if (last != null && last.getApprovalNo() != null) {
            String lastNo = last.getApprovalNo();
            // 取最后4位作为序号
            try {
                String lastSeq = lastNo.substring(lastNo.length() - 4);
                seq = Integer.parseInt(lastSeq) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return "AP" + timestamp + String.format("%04d", seq);
    }

    /**
     * 填充新增接口审批的字段
     */
    private void fillNewInterfaceFields(ApprovalInfo info, Map<String, Object> params) {
        info.setTitle("新增接口: " + params.getOrDefault("transno", ""));
        info.setTransno((String) params.get("transno"));
        info.setVersionNo(getIntegerParam(params, "versionNo"));
        info.setApplicantDeptId(getLongParam(params, "applicantDeptId"));
        info.setApplicantSystemId(getLongParam(params, "applicantSystemId"));
        info.setProviderSystemId(getLongParam(params, "providerSystemId"));
    }

    /**
     * 填充修改接口审批的字段
     */
    private void fillModifyInterfaceFields(ApprovalInfo info, Map<String, Object> params) {
        info.setTitle("修改接口: " + params.getOrDefault("transno", ""));
        info.setTransno((String) params.get("transno"));
        info.setVersionNo(getIntegerParam(params, "versionNo"));
        info.setApplicantDeptId(getLongParam(params, "applicantDeptId"));
        info.setApplicantSystemId(getLongParam(params, "applicantSystemId"));
        info.setProviderSystemId(getLongParam(params, "providerSystemId"));
    }

    /**
     * 填充申请接口审批的字段
     */
    private void fillApplyInterfaceFields(ApprovalInfo info, Map<String, Object> params) {
        info.setTitle("申请接口: " + params.getOrDefault("transno", ""));
        info.setTransno((String) params.get("transno"));
        info.setApplicantDeptId(getLongParam(params, "applicantDeptId"));
        info.setApplicantSystemId(getLongParam(params, "applicantSystemId"));
        info.setProviderSystemId(getLongParam(params, "providerSystemId"));
        info.setRequirementNo((String) params.get("requirementNo"));
        info.setRequirementDesc((String) params.get("requirementDesc"));
        info.setApplyReason((String) params.get("applyReason"));
        info.setDownstreamInfo((String) params.get("downstreamInfo"));
    }

    /**
     * 根据审批类型创建审批流程步骤
     */
    private List<ApprovalFlow> createApprovalFlows(ApprovalInfo info, ApprovalType type, Map<String, Object> params) {
        List<ApprovalFlow> flows = new ArrayList<>();

        switch (type) {
            case NEW_INTERFACE:
                // 1步: 申请人部门经理
                flows.add(buildFlow(1, "部门经理审批", info.getApplicantDeptId()));
                break;

            case MODIFY_INTERFACE:
                // 步骤1: 接口所属系统部门的经理
                Long providerDeptId = findDeptIdBySystemId(info.getProviderSystemId());
                flows.add(buildFlow(1, "服务方部门经理审批", providerDeptId));
                // 如果有请求方且不是同一个部门，加请求方部门经理
                if (info.getApplicantDeptId() != null && !info.getApplicantDeptId().equals(providerDeptId)) {
                    flows.add(buildFlow(2, "申请方部门经理审批", info.getApplicantDeptId()));
                }
                break;

            case APPLY_INTERFACE:
                // 步骤1: 申请方部门经理
                flows.add(buildFlow(1, "申请方部门经理审批", info.getApplicantDeptId()));
                // 步骤2: 服务方系统所属部门的经理
                Long providerDeptId2 = findDeptIdBySystemId(info.getProviderSystemId());
                flows.add(buildFlow(2, "服务方部门经理审批", providerDeptId2));
                break;

            default:
                break;
        }
        return flows;
    }

    /**
     * 构建审批流程步骤
     */
    private ApprovalFlow buildFlow(int stepNo, String stepName, Long deptId) {
        ApprovalFlow flow = new ApprovalFlow();
        flow.setStepNo(stepNo);
        flow.setStepName(stepName);
        flow.setStatus(0); // 0=待审批
        flow.setDeptId(deptId);
        return flow;
    }

    /**
     * 根据系统ID查找所属部门ID
     */
    private Long findDeptIdBySystemId(Long systemId) {
        if (systemId == null) {
            return null;
        }
        SysSystem system = sysSystemService.getById(systemId);
        return system != null ? system.getDeptId() : null;
    }

    /**
     * 找到当前待审批步骤（status=0，按 stepNo 升序取第一个）
     */
    private ApprovalFlow findCurrentFlow(Long approvalId) {
        LambdaQueryWrapper<ApprovalFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalFlow::getApprovalId, approvalId)
               .eq(ApprovalFlow::getStatus, 0)
               .orderByAsc(ApprovalFlow::getStepNo)
               .last("LIMIT 1");
        return approvalFlowMapper.selectOne(wrapper);
    }

    /**
     * 检查指定步骤之后是否还有待审批步骤
     */
    private ApprovalFlow findNextPendingFlow(Long approvalId, int currentStepNo) {
        LambdaQueryWrapper<ApprovalFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalFlow::getApprovalId, approvalId)
               .eq(ApprovalFlow::getStatus, 0)
               .gt(ApprovalFlow::getStepNo, currentStepNo)
               .orderByAsc(ApprovalFlow::getStepNo)
               .last("LIMIT 1");
        return approvalFlowMapper.selectOne(wrapper);
    }

    /**
     * 审批全部通过后执行业务操作
     */
    private void executeBusinessAction(ApprovalInfo info) {
        ApprovalType type = ApprovalType.fromCode(info.getType());
        switch (type) {
            case NEW_INTERFACE:
            case MODIFY_INTERFACE:
                publishVersion(info);
                break;
            case APPLY_INTERFACE:
                createInterfaceRelation(info);
                break;
            default:
                break;
        }
    }

    /**
     * 发布版本：更新版本状态和接口状态
     */
    private void publishVersion(ApprovalInfo info) {
        if (info.getTransno() == null || info.getVersionNo() == null) {
            log.warn("发布版本缺少必要参数: transno={}, versionNo={}", info.getTransno(), info.getVersionNo());
            return;
        }

        // 更新版本状态为已发布
        InterfaceVersion version = interfaceVersionService.getVersion(info.getTransno(), info.getVersionNo());
        if (version != null) {
            version.setStatus(VersionStatus.PUBLISHED.getCode());
            version.setPublishedTime(LocalDateTime.now());
            interfaceVersionService.updateById(version);
        }

        // 更新接口状态为已发布，并设置当前版本号
        InterfaceInfo interfaceInfo = interfaceInfoService.getByTransnoAnyStatus(info.getTransno());
        if (interfaceInfo != null) {
            interfaceInfo.setStatus(InterfaceStatus.PUBLISHED.getCode());
            interfaceInfo.setCurrentVersion(info.getVersionNo());
            interfaceInfo.setUpdatedTime(LocalDateTime.now());
            interfaceInfoService.updateById(interfaceInfo);
        }

        log.info("版本发布完成: transno={}, versionNo={}", info.getTransno(), info.getVersionNo());
    }

    /**
     * 创建接口关系记录
     */
    private void createInterfaceRelation(ApprovalInfo info) {
        InterfaceRelation relation = new InterfaceRelation();
        relation.setTransno(info.getTransno());
        relation.setProviderSystemId(info.getProviderSystemId());
        relation.setApplicantSystemId(info.getApplicantSystemId());
        relation.setApprovalId(info.getId());
        relation.setStatus(1); // 1=生效
        relation.setApplyTime(LocalDateTime.now());
        relation.setRequirementNo(info.getRequirementNo());
        relation.setApplyReason(info.getApplyReason());
        relation.setCreatedTime(LocalDateTime.now());
        interfaceRelationService.save(relation);

        log.info("接口关系创建完成: transno={}, applicantSystemId={}, providerSystemId={}",
                info.getTransno(), info.getApplicantSystemId(), info.getProviderSystemId());
    }

    /**
     * 更新接口状态为待审批
     */
    private void updateInterfaceStatusToPending(ApprovalInfo info) {
        if (info.getTransno() == null) {
            return;
        }
        // 只有新增和修改类型才更新接口状态
        ApprovalType type = ApprovalType.fromCode(info.getType());
        if (type == ApprovalType.NEW_INTERFACE || type == ApprovalType.MODIFY_INTERFACE) {
            InterfaceInfo interfaceInfo = interfaceInfoService.getByTransnoAnyStatus(info.getTransno());
            if (interfaceInfo != null) {
                interfaceInfo.setStatus(InterfaceStatus.PENDING.getCode());
                interfaceInfo.setUpdatedTime(LocalDateTime.now());
                interfaceInfoService.updateById(interfaceInfo);
            }
        }
    }

    /**
     * 驳回/撤回后恢复接口状态
     */
    private void revertInterfaceStatus(ApprovalInfo info) {
        if (info.getTransno() == null) {
            return;
        }
        ApprovalType type = ApprovalType.fromCode(info.getType());
        if (type == ApprovalType.NEW_INTERFACE || type == ApprovalType.MODIFY_INTERFACE) {
            InterfaceInfo interfaceInfo = interfaceInfoService.getByTransnoAnyStatus(info.getTransno());
            if (interfaceInfo != null) {
                // 如果有已发布版本则恢复为已发布，否则恢复为草稿
                if (interfaceInfo.getCurrentVersion() != null && interfaceInfo.getCurrentVersion() > 0) {
                    interfaceInfo.setStatus(InterfaceStatus.PUBLISHED.getCode());
                } else {
                    interfaceInfo.setStatus(InterfaceStatus.DRAFT.getCode());
                }
                interfaceInfo.setUpdatedTime(LocalDateTime.now());
                interfaceInfoService.updateById(interfaceInfo);
            }
        }
    }

    /**
     * 填充展示字段：系统名称、接口名称、步骤进度
     */
    private void fillDisplayFields(ApprovalInfo info) {
        // 填充申请方系统名称
        if (info.getApplicantSystemId() != null) {
            SysSystem applicantSystem = sysSystemService.getById(info.getApplicantSystemId());
            if (applicantSystem != null) {
                info.setApplicantSystemName(applicantSystem.getName());
            }
        }

        // 填充提供方系统名称
        if (info.getProviderSystemId() != null) {
            SysSystem providerSystem = sysSystemService.getById(info.getProviderSystemId());
            if (providerSystem != null) {
                info.setProviderSystemName(providerSystem.getName());
            }
        }

        // 填充接口名称
        if (info.getTransno() != null) {
            InterfaceInfo interfaceInfo = interfaceInfoService.getByTransnoAnyStatus(info.getTransno());
            if (interfaceInfo != null) {
                info.setInterfaceName(interfaceInfo.getName());
            }
        }

        // 填充步骤进度
        List<ApprovalFlow> flows = getFlowDetail(info.getId());
        if (!flows.isEmpty()) {
            info.setTotalSteps(flows.size());
            // 计算当前步骤：第一个待审批步骤，若全部已处理则为总步骤数
            int currentStep = flows.size();
            for (ApprovalFlow flow : flows) {
                if (flow.getStatus() == 0) {
                    currentStep = flow.getStepNo();
                    break;
                }
            }
            info.setCurrentStep(currentStep);
        }
    }

    /**
     * 从 params Map 中获取 Integer 值
     */
    private Integer getIntegerParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 params Map 中获取 Long 值
     */
    private Long getLongParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
