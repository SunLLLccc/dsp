package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fintechervision.dsp.entity.InterfaceVersion;

import java.util.Map;

/**
 * 接口版本服务
 * 从 InterfaceAdminController 中提取版本管理逻辑
 */
public interface InterfaceVersionService extends IService<InterfaceVersion> {

    /**
     * 保存接口Schema配置（创建新版本）
     */
    InterfaceVersion saveSchema(String transno, String inputSchema, String outputSchema, String changeLog, String operator);

    /**
     * 查询接口版本列表
     */
    Page<InterfaceVersion> versionList(String transno, Integer pageNum, Integer pageSize);

    /**
     * 获取指定版本的XML配置
     */
    InterfaceVersion getVersion(String transno, Integer versionNo);

    /**
     * 提交发布申请
     */
    void submitApproval(String transno, Integer versionNo, String operator);

    /**
     * 审批通过并发布
     */
    void approveAndPublish(String transno, Integer versionNo, String approver);

    /**
     * 审批驳回
     */
    void rejectApproval(String transno, Integer versionNo, String reason);

    /**
     * 接口下线
     */
    void offline(String transno);

    /**
     * 撤销审批
     */
    void withdrawApproval(String transno, Integer versionNo);
}
