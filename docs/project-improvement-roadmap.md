# DSP 数据服务平台改进路线图

本文档用于指导 Claude Code 按阶段改进项目。每个任务完成后，先停止并提交变更摘要、影响文件、测试结果；由 Codex 复核通过后，再继续下一项。

## 协作规则

| 角色 | 职责 |
| --- | --- |
| Claude Code | 按本文档任务顺序实现；每次只做一个任务；不夹带无关重构；完成后给出变更清单和验证结果 |
| Codex | 审查 Claude Code 的变更；重点验证安全边界、接口契约、回归风险、测试覆盖 |
| 开发人员 | 在业务规则不明确时确认角色、审批、发布、导出等产品口径 |

## 执行顺序

| 阶段 | 优先级 | 目标 | 通过标准 |
| --- | --- | --- | --- |
| 1 | P0 | 修复安全边界 | 已登录普通用户不能越权审批、签发 Token、导出未授权接口、探测数据源 |
| 2 | P1 | 统一接口契约与异常 | 管理端、数据服务、离线服务错误返回策略清晰一致 |
| 3 | P1 | 敏感信息脱敏与 DTO 化 | 前端不再收到密码、密钥、文件路径等敏感字段 |
| 4 | P1 | 导出与审批性能优化 | 大数据导出不一次性堆内存，审批列表消除明显 N+1 |
| 5 | P1 | 前端权限、下载、反馈完善 | 路由按角色拦截，下载携带 token，列表有 loading/empty/error |
| 6 | P1 | XML 引擎开发体验增强 | 发布前校验、调试链路、错误定位更清楚 |
| 7 | P2 | 重构与产品体验增强 | 大组件拆分、模板库、看板、接口市场等体验提升 |

## P0 必做任务

### T01 审批接口补权限校验

| 项目 | 内容 |
| --- | --- |
| 位置 | `dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/ApprovalController.java`、`dsp-core/src/main/java/com/sunlc/dsp/service/impl/ApprovalInfoServiceImpl.java` |
| 问题 | `approve`、`reject` 未限制角色，任意登录用户可通过审批单 ID 操作 |
| 要求 | Controller 加 `@RequireRole({"DEPT_MANAGER", "ADMIN"})`；Service 校验当前审批步骤所属部门，`ADMIN` 可跳过部门限制 |
| 验收 | USER 角色调用审批/驳回返回 403；非本部门 DEPT_MANAGER 返回 403；本部门 DEPT_MANAGER 和 ADMIN 可正常审批 |

参考方向：

```java
@PostMapping("/{id}/approve")
@RequireRole({"DEPT_MANAGER", "ADMIN"})
public ApiResponse<Void> approve(@PathVariable Long id, HttpServletRequest request) {
    approvalInfoService.approve(id, getCurrentUser(request), getCurrentUserName(request),
            getCurrentDeptId(request), getCurrentRoles(request));
    return ApiResponse.success("APPROVAL", "APPROVE", null);
}
```

### T02 应用 Token 签发补权限

| 项目 | 内容 |
| --- | --- |
| 位置 | `AppAuthAdminController.java`、`AppAuthServiceImpl.java` |
| 问题 | 任意登录用户可签发任意应用 Token |
| 要求 | 签发 Token 至少要求 `DEPT_MANAGER` 或应用归属管理员；如果暂未建应用归属字段，先限制 `DEPT_MANAGER` / `ADMIN` |
| 验收 | USER 角色无法签发；DEPT_MANAGER/ADMIN 可签发；失败返回统一业务错误 |

### T03 离线导出校验授权范围和任务归属

| 项目 | 内容 |
| --- | --- |
| 位置 | `dsp-offline-service/controller/OfflineExportController.java`、`ExportServiceImpl.java` |
| 问题 | 只认证 token，不校验 `allowedTransnos`，也不校验 task 是否属于当前 app |
| 要求 | 提交导出时校验 transno 白名单；进度/下载时校验 `applyUser == appId` 或具备管理员语义 |
| 验收 | appA 不能提交未授权 transno；appA 不能查看/下载 appB 任务 |

参考方向：

```java
private void checkTransnoAllowed(String transno, HttpServletRequest request) {
    List<String> allowed = (List<String>) request.getAttribute("allowedTransnos");
    if (allowed == null || (!allowed.contains("*") && !allowed.contains(transno))) {
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该接口");
    }
}
```

### T04 数据源测试接口加权限和连接边界

| 项目 | 内容 |
| --- | --- |
| 位置 | `DatasourceAdminController.java`、`DatasourceManagerServiceImpl.java` |
| 问题 | 任意登录用户可触发后端连接任意 JDBC URL |
| 要求 | 增加 `@RequireRole({"DEPT_MANAGER", "ADMIN"})`；限制协议、驱动类型；错误信息不要回显完整连接细节 |
| 验收 | USER 角色返回 403；非法 JDBC URL 返回参数错误；失败信息不包含密码、完整内网地址 |

## P1 建议任务

### T05 统一异常处理和响应契约

| 项目 | 内容 |
| --- | --- |
| 位置 | 三个服务的 `GlobalExceptionHandler`、`GlobalResponseBodyAdvice` |
| 问题 | 管理端、数据服务、离线服务 HTTP 状态与业务码策略不一致 |
| 要求 | 明确全局规则：认证 401，权限 403，参数 400，系统异常 500，业务异常是否 200 需项目统一 |
| 验收 | 同类异常在三个服务返回结构一致；前端拦截器能准确处理 401/403/业务失败 |

### T06 DTO 化与敏感字段脱敏

| 项目 | 内容 |
| --- | --- |
| 位置 | `AppAuthAdminController`、`DatasourceAdminController`、`SysUserController`、实体返回点 |
| 问题 | Controller 直接收发 Entity，`appSecret`、数据源密码等会返回前端 |
| 要求 | 引入 Request/VO DTO；列表和详情默认脱敏；只有一次性 Token 签发结果展示 token |
| 验收 | 应用列表不返回 `appSecret`；数据源列表/详情不返回 `password`；用户接口不返回 `password` |

示例：

```java
public class DatasourceConfigVO {
    private Long id;
    private String dsName;
    private String dsType;
    private String jdbcUrlMasked;
    private String username;
    private Integer status;
}
```

### T07 请求校验标准化

| 项目 | 内容 |
| --- | --- |
| 位置 | 各 Controller 入参 |
| 问题 | 多处使用裸 `Map` 或 Entity，缺少 `@Valid` |
| 要求 | 为高频接口补 DTO 和校验注解；数据服务 `ApiRequest` 使用 `@Valid` |
| 验收 | 缺必填字段返回明确参数错误；不进入业务逻辑 |

### T08 动态 SQL 安全边界

| 项目 | 内容 |
| --- | --- |
| 位置 | `dsp-engine/executor/PaginationHandler.java`、`DynamicSqlHandler.java`、XML 发布流程 |
| 问题 | `orderBy` 等 SQL 片段直接拼接；XML SQL 缺少只读检查 |
| 要求 | 发布前校验只允许 SELECT；`orderBy` 字段白名单或正则校验；禁止多语句和危险关键字 |
| 验收 | 包含 `UPDATE/DELETE/INSERT/DROP/;` 的 XML 发布失败；非法 orderBy 发布失败 |

### T09 导出改为流式或分批

| 项目 | 内容 |
| --- | --- |
| 位置 | `ExportServiceImpl.java` |
| 问题 | 一次性查询完整结果到内存，`chunkSize` 未使用 |
| 要求 | 按分页/游标分批查询并写文件；离线任务实时更新进度 |
| 验收 | 导出 10 万行以上不出现一次性大 List；任务进度随批次更新 |

### T10 审批列表消除 N+1

| 项目 | 内容 |
| --- | --- |
| 位置 | `ApprovalInfoServiceImpl.java` |
| 问题 | 审批列表逐条查流程、系统、接口 |
| 要求 | 批量查询并 Map 组装展示字段 |
| 验收 | 一页 10 条审批记录不再产生几十次 SQL 查询 |

### T11 前端路由权限守卫

| 项目 | 内容 |
| --- | --- |
| 位置 | `dsp-admin-web/src/router/index.js`、菜单配置 |
| 问题 | 路由只校验登录，不校验角色 |
| 要求 | 路由增加 `meta.roles`；守卫校验角色；增加 403 页面 |
| 验收 | 非 ADMIN 直接访问 `/system/user` 被拦截 |

### T12 前端下载改 Axios Blob

| 项目 | 内容 |
| --- | --- |
| 位置 | `api/index.js`、`views/export/List.vue` |
| 问题 | `window.open` 不携带 `Admin-Token` |
| 要求 | 使用 axios `responseType: 'blob'` 下载 |
| 验收 | 下载请求带 `Admin-Token`；401 时仍走统一拦截或页面错误提示 |

### T13 前端列表状态完善

| 项目 | 内容 |
| --- | --- |
| 位置 | 接口、模板、应用授权、审批、数据源等列表页 |
| 问题 | 多处缺 loading、empty、error 状态；全局错误和页面错误重复 |
| 要求 | 统一列表 loading；空态文案；错误不重复弹窗 |
| 验收 | 慢接口有 loading；空列表有空态；失败不会重复提示 |

### T14 XML 调试体验增强

| 项目 | 内容 |
| --- | --- |
| 位置 | `XmlEngine`、调试接口、`views/interface/Debug.vue` |
| 问题 | 调试失败只返回字符串，无法定位 SQL、queryId、耗时、映射步骤 |
| 要求 | 增加 debug trace：queryId、数据源、最终 SQL、参数、行数、耗时、异常 |
| 验收 | 调试页能展示执行链路；生产查询默认不暴露 SQL，调试模式才展示 |

## P2 可选任务

### T15 前端大组件拆分

| 项目 | 内容 |
| --- | --- |
| 位置 | `views/interface/List.vue`、`views/approval/List.vue`、`views/template/List.vue` |
| 要求 | 拆分为列表、弹窗、申请、关系、版本历史等子组件；抽取通用分页逻辑 |
| 验收 | 单文件职责清晰；功能行为不变 |

### T16 状态枚举统一

| 项目 | 内容 |
| --- | --- |
| 位置 | Java 枚举、前端 `constants/status.js` |
| 要求 | 后端提供枚举接口，或构建时生成前端常量 |
| 验收 | 状态码不再前后端双写分叉 |

### T17 接口市场和看板

| 项目 | 内容 |
| --- | --- |
| 要求 | 增加接口目录、标签、调用量、成功率、耗时、最近错误 |
| 验收 | 开发人员能快速找到可复用接口，能看到接口健康状态 |

### T18 配置向导

| 项目 | 内容 |
| --- | --- |
| 要求 | 在保留 XML 高级模式的同时，提供表单式配置：数据源、SQL、入参、出参、分页、映射 |
| 验收 | 常见单表查询接口可不手写 XML 完成配置 |

## Claude Code 每项任务完成后的汇报模板

```markdown
### 任务编号
Txx

### 改动文件
- path/to/file

### 改动摘要
- ...

### 验证方式
- mvn test ...
- npm run build
- 手动验证 ...

### 风险点
- ...

### 需要 Codex 复核
- 权限是否完整
- 接口契约是否兼容
- 是否有遗漏测试
```

## Codex 复核清单

| 检查项 | 说明 |
| --- | --- |
| 变更范围 | 是否只改当前任务相关文件 |
| 安全边界 | 是否覆盖角色、部门、appId、transno、taskId 归属 |
| 接口兼容 | 前端调用是否同步调整；响应结构是否破坏现有页面 |
| 异常语义 | 401/403/400/500 与业务码是否符合约定 |
| 测试覆盖 | 至少包含正向、无权限、非法入参三个方向 |
| 日志脱敏 | 日志和响应中不暴露密码、密钥、Token |
| 回归风险 | 导出、审批、接口发布、XML 调试等核心流程是否可用 |

## 推荐第一轮执行计划

| 顺序 | 任务 | 原因 |
| --- | --- | --- |
| 1 | T01 审批接口补权限校验 | 直接越权风险最高 |
| 2 | T02 应用 Token 签发补权限 | 会影响开放数据接口访问边界 |
| 3 | T03 离线导出校验授权范围和任务归属 | 防止跨应用读取导出数据 |
| 4 | T04 数据源测试接口加权限和连接边界 | 防止连接探测与敏感错误泄露 |
| 5 | T06 DTO 化与敏感字段脱敏 | 降低管理端敏感信息暴露 |
| 6 | T05 统一异常处理和响应契约 | 为后续前端统一处理打基础 |

