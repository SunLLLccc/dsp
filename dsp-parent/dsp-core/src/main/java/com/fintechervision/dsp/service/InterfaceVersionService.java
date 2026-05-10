package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fintechervision.dsp.entity.InterfaceVersion;

public interface InterfaceVersionService extends IService<InterfaceVersion> {

    InterfaceVersion saveSchema(String transno, String inputSchema, String outputSchema, String changeLog, String operator);

    Page<InterfaceVersion> versionList(String transno, Integer pageNum, Integer pageSize);

    InterfaceVersion getVersion(String transno, Integer versionNo);

    void submitApproval(String transno, Integer versionNo, String operator);

    void approveAndPublish(String transno, String approver);

    void rejectApproval(String transno, String reason, String operator);

    void offline(String transno, String operator);

    void withdrawApproval(String transno, String operator);

    InterfaceVersion getLatestVisibleVersion(String transno, String currentUser);
}
