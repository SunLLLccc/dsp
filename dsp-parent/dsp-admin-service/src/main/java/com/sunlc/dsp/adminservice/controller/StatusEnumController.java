package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.enums.*;
import com.sunlc.dsp.export.ExportTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 状态枚举元数据接口。
 * 前端启动时加载此接口，实现状态标签与后端枚举的单源同步；
 * 接口失败时前端使用本地 fallback 常量，不影响页面可用性。
 */
@RestController
@RequestMapping("/dsp/admin/enums")
@RequiredArgsConstructor
public class StatusEnumController {

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> statusEnums() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interfaceStatus", toMap(InterfaceStatus.values()));
        result.put("versionStatus", toMap(VersionStatus.values()));
        result.put("approvalStatus", toMap(ApprovalStatus.values()));
        result.put("approvalType", toMap(ApprovalType.values()));
        result.put("commonStatus", toMap(CommonStatus.values()));
        result.put("exportTaskStatus", toMap(ExportTaskStatus.values()));
        result.put("relationStatus", relationStatusMap());
        return ApiResponse.success("ENUM_STATUS", "", result);
    }

    /**
     * 将枚举数组序列化为 { "code": { "name": "DRAFT", "label": "草稿" }, ... }
     */
    private Map<String, Map<String, String>> toMap(Enum<?>[] values) {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        for (Enum<?> e : values) {
            try {
                int code = (int) e.getClass().getMethod("getCode").invoke(e);
                String description = (String) e.getClass().getMethod("getDescription").invoke(e);
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("name", e.name());
                entry.put("label", description);
                map.put(String.valueOf(code), entry);
            } catch (Exception ignored) {
                // 跳过无法反射的枚举值
            }
        }
        return map;
    }

    /**
     * 关系状态最小映射。
     * interface_relation 表 status 字段：1-生效 2-已下线（见 V5__approval_refactor.sql）。
     * 后续如新增 Java 枚举类可直接替换此方法。
     */
    private Map<String, Map<String, String>> relationStatusMap() {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        map.put("1", entry("ONLINE", "生效"));
        map.put("2", entry("OFFLINE", "已下线"));
        return map;
    }

    private Map<String, String> entry(String name, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        return m;
    }
}
