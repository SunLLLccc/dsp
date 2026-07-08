# 功能清单 — DSP 数据服务平台

> 由 lp-feature-scan 自动生成。每个功能附【核心度/复杂度/依赖/证据】。
> 人在回路：在要深入学习的功能前把 `- [ ]` 改为 `- [x]`，lp-prompt-gen 只对选中的功能生成提示词与文档。
> 说明文档充分（README + docs/engine-architecture.md + docs/xml-dsl-reference.md + template/ 17 个 DSL 示例），功能识别以文档层为主、结构与入口层交叉验证，证据均经实际文件存在性核对。

## 功能清单（按核心度排序）

### - [x] 1. XML DSL 数据查询引擎（四阶段执行流水线）

- **简介**：通过 XML 配置定义接口逻辑（参数/查询/数据源/结果映射/响应），XmlEngine 按"参数校验→DAG 查询编排→结果映射→响应构建"四阶段流水线执行，是整个 DSP 平台"零代码发布接口"的执行内核。
- **核心度**：核心（README 核心特性首条；docs/engine-architecture.md 整篇围绕它；6 个模块全部直接或间接依赖它）
- **复杂度**：高
- **依赖**：无（这是平台主线，学其它功能的前置）
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java`: 引擎主类，executeWithConfig() 四阶段流水线（validateParams→QueryOrchestrator→ResultMapper→buildResponse），含带/不带 DebugContext 的两套入口
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/parser/XmlConfigParser.java`: DOM4J 解析 XML 字符串为 InterfaceConfig（request/datasource/query/resultMap/responseData 五部分）
  - `docs/engine-architecture.md`: 完整记录引擎总览流程与详细执行流水线
  - `README.md`: "XML 配置化开发 — 通过 XML 定义接口逻辑，零代码发布接口"

### - [x] 2. DAG 多查询并行编排（CompletableFuture + 依赖拓扑 + 环检测）

- **简介**：QueryOrchestrator 解析 `<query depends="q1,q2">` 构建有向无环图，无依赖查询并行提交线程池、有依赖查询等待上游 Future 完成，启动时 DFS 检测循环依赖直接抛异常。
- **核心度**：核心（README 核心特性"查询并行编排"；docs/engine-architecture.md 专节描述；区别于串行执行的普通查询框架）
- **复杂度**：高
- **依赖**：XML DSL 数据查询引擎
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/QueryOrchestrator.java`: orchestrate() 含 ThreadPoolExecutor(4,8)、validateDependencies()、detectCycle()、buildFuture() 递归构建 CompletableFuture 链
  - `template/09-parallel-orchestration.xml` 与 `template/10-dependency-orchestration.xml`: DAG 编排的 DSL 示例
  - `docs/xml-dsl-reference.md`: "depends 用于 DAG 依赖排序"

### - [ ] 3. 动态 SQL 处理（SpEL 驱动的 `<if>`/`<foreach>`/`#{}` 参数化）

- **简介**：DynamicSqlHandler 用 Spring SpEL 解析 `<if test="...">` 条件追加 SQL、`<foreach>` 展开 `?` 占位符，再将 `#{requestData.xxx}` / `#{queryId.col}` 替换为参数化的 `?`（防注入）。
- **核心度**：核心（README 核心特性"动态 SQL"；MyBatis 风格标签在自定义引擎内的独立实现，非 MyBatis 直接能力）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/DynamicSqlHandler.java`: SpelExpressionParser 解析 `<if>`/`<foreach>`，PARAM_PATTERN 正则 `#\{([^}]+)\}` 抓取参数，replaceParameters() 输出参数化 SQL
  - `template/02-dynamic-sql-query.xml`: 动态 SQL DSL 示例
  - `docs/xml-dsl-reference.md`: "动态 SQL — `<if>` 条件判断、`<foreach>` 集合遍历、`#{}` 参数替换"

### - [ ] 4. 多源查询执行器路由（SQL/HTTP/Dubbo/Mongo 策略分发）

- **简介**：XmlEngine 按 `<query type="...">` 分发到 SqlExecutor / HttpExecutor / DubboExecutor / MongoExecutor 四类执行器，SQL 类又借 Dynamic-DS 路由 MySQL/Doris/Oracle/PostgreSQL 等多数据源，Mongo 执行器 `@ConditionalOnClass` 按需加载。
- **核心度**：核心（README 核心特性"多数据源支持"；平台异构数据聚合的能力根基）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎、动态 SQL 处理（SQL 类查询复用）
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/`: SqlExecutor.java / HttpExecutor.java / DubboExecutor.java / MongoExecutor.java 四个执行器并存
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java`: @Autowired(required=false) MongoExecutor 按需注入
  - `template/05-http-query.xml`、`07-dubbo-query.xml`、`08-mongo-query.xml`、`12-multi-datasource.xml`: 各类源查询 DSL 示例

### - [ ] 5. 分页查询处理（游标分页 + 深分页子查询优化 + 防注入）

- **简介**：PaginationHandler 支持 CURSOR（`AND id > ? ORDER BY id LIMIT ?`）与 OPTIMIZED（子查询 `WHERE id >= (SELECT id FROM t ... LIMIT ?,1)` 改写深分页）两种模式，并对 order-by 字段做 SqlSecurityValidator 注入校验。
- **核心度**：核心（README 核心特性"分页查询 — 游标分页+深分页优化"；深分页优化是该平台特色机制）
- **复杂度**：中
- **依赖**：多源查询执行器路由、SQL 安全校验
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/PaginationHandler.java`: rewrite() 按 PaginationConfig.Mode 分发，rewriteCursor() 与 rewriteOptimized() 两套实现，getPageSize() 提取页大小
  - `template/03-cursor-pagination.xml` 与 `template/04-optimized-pagination.xml`: 两种分页模式 DSL 示例
  - `docs/xml-dsl-reference.md`: "分页 — 游标分页 / 优化子查询分页"

### - [ ] 6. 结果映射与内置函数注册表（25+ 函数，map/list 聚合）

- **简介**：ResultMapper 按 `<resultMap>` 做字段映射与 alias，支持 `fn:FUNC_NAME,arg1` 调用 FunctionRegistry 注册的 29 个内置函数（日期/字符串/JSON/聚合/数学等）；`<responseData>` 用 `as` 属性聚合为单对象(map)或列表(list)。
- **核心度**：核心（README 核心特性"内置函数库 — 29个内置函数"；引擎结果加工的关键能力）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎、DAG 编排（引用上游查询结果）
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/ResultMapper.java`: 结果映射主类
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/function/FunctionRegistry.java`: 静态 Map 注册 DATE_FORMAT/CONCAT/JSON_EXTRACT/SUM 等 29 个 FunctionInvoker
  - `template/11-result-mapping-functions.xml`: 结果映射与函数 DSL 示例
  - `README.md`: "内置函数一览（29个）"分类表

### - [ ] 7. XML 配置多级缓存（本地缓存 + DB 回源 + 定时刷新 + 即时失效）

- **简介**：XmlConfigCacheManager 以 ConcurrentHashMap 缓存 transno→InterfaceConfig，未命中从 DB 加载 XML 并解析；支持定时全量刷新（5 分钟）与配置变更（审批通过/下线）时经 XmlConfigCacheInvalidator SPI 即时失效。
- **核心度**：核心（README 核心特性"本地缓存"；引擎性能与一致性的关键机制，跨服务解耦的 SPI 范例）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎、统一 API 契约层（XmlConfigCacheInvalidator SPI 定义）
- **证据**：
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/cache/XmlConfigCacheManager.java`: 实现 XmlConfigCacheInvalidator 接口，get() 缓存未命中走 loadAndCache()
  - `dsp-parent/dsp-common/src/main/java/com/sunlc/dsp/common/service/XmlConfigCacheInvalidator.java`: 缓存失效 SPI 接口（解耦 engine 与 admin-service）
  - `docs/engine-architecture.md`: "缓存机制" 与 README "缓存架构" 章节
  - `README.md`: "本地缓存 — XML 配置解析结果缓存，5分钟定时刷新，发布/下线即时失效"

### - [ ] 8. 接口版本 + 审批发布流水线（零代码发布）

- **简介**：InterfaceVersionService 管理 Schema 版本（saveSchema/versionList/getVersion），提供"提交审批→通过发布→驳回→撤回→下线"完整生命周期；审批通过触发缓存失效使新版本对外生效，实现"零代码"上线。
- **核心度**：核心（README 核心特性"审批流程"；平台"零代码发布接口"闭环的关键环节）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎、XML 配置多级缓存（审批通过触发失效）、统一 API 契约层
- **证据**：
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/service/InterfaceVersionService.java`: 接口含 saveSchema/submitApproval/approveAndPublish/rejectApproval/offline/withdrawApproval 完整生命周期方法
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/service/ApprovalRecordService.java`: submitApproval/approve/reject 审批记录服务
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/ApprovalController.java` 与 `ApprovalApplicationController.java`: 审批管理 Controller
  - `dsp-admin-web/src/views/approval/`: 前端审批管理页面

### - [ ] 9. 在线/离线数据导出（异步任务 + 多格式 + 进度查询）

- **简介**：在线导出经 `/dsp/api/{transno}/export` 同步生成；离线导出经 dsp-offline-service 异步任务化（@Async），通过 DataQueryService SPI 调引擎分页取数，EasyExcel 写 XLSX/CSV/TXT，提供进度查询与文件下载端点。
- **核心度**：核心（README 核心特性"在线/离线导出"；data-service 与 offline-service 两个独立服务的核心职责）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎（借 DataQueryService SPI 取数）、统一 API 契约层
- **证据**：
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/service/impl/ExportServiceImpl.java`: @Async 异步任务，EasyExcel 写出，DataQueryService 取数
  - `dsp-parent/dsp-offline-service/src/main/java/com/sunlc/dsp/offlineservice/controller/OfflineExportController.java`: 提交任务/查进度/下载三端点（README 服务端点表）
  - `dsp-parent/dsp-data-service/src/main/java/com/sunlc/dsp/dataservice/controller/DataApiController.java`: 在线导出端点 `/dsp/api/{transno}/export`
  - `dsp-parent/dsp-common/src/main/java/com/sunlc/dsp/common/model/PaginationExportInfo.java`: 导出分页信息模型

### - [ ] 10. 多数据源动态管理与连接测试（运行时注册 + AES 加密 + 白名单校验）

- **简介**：DatasourceManagerService 借 Dynamic-DS 在运行时动态注册/移除数据源（registerDatasource/removeDatasource），testConnection() 校验类型白名单与 URL 合法性并返回脱敏错误，密码 AES 加密存储（ENC(base64)）。
- **核心度**：核心（README 配置说明与核心特性均强调多数据源；支撑引擎 SQL 查询的数据源底座，非通用 CRUD）
- **复杂度**：中
- **依赖**：无（独立于引擎，是引擎 SQL 查询的前置数据源准备）
- **证据**：
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/service/DatasourceManagerService.java`: loadAndRegisterAll/registerDatasource/removeDatasource/testConnection 方法签名
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/DatasourceAdminController.java`: 数据源管理 CRUD + 动态注册 + 连接测试 Controller
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/config/DatasourceInitRunner.java`: ApplicationRunner 启动时加载注册全部数据源
  - `README.md`: "数据源密码 — AES 加密存储，ENC(base64) 格式"

### - [ ] 11. 统一 API 契约层（ApiRequest/ApiResponse/BusinessException/SPI 解耦）

- **简介**：dsp-common 定义全项目共享的请求/响应报文（ApiRequest\<T\> 含 head+requestData、ApiResponse\<T\> 含 code+message+data）、统一业务异常（BusinessException+ErrorCode 枚举）与跨模块 SPI（DataQueryService/XmlConfigCacheInvalidator），被全部 6 个子模块依赖。
- **核心度**：核心（虽是"公共层"但该项目特有的统一契约设计，被 25+ 文件引用，是理解全项目的骨架）
- **复杂度**：低
- **依赖**：无（最底层，其它功能依赖它）
- **证据**：
  - `dsp-parent/dsp-common/src/main/java/com/sunlc/dsp/common/model/ApiRequest.java` 与 `ApiResponse.java`: 统一请求/响应报文封装
  - `dsp-parent/dsp-common/src/main/java/com/sunlc/dsp/common/exception/BusinessException.java` 与 `enums/ErrorCode.java`: 统一异常 + 错误码枚举（被 26+ 文件引用）
  - `dsp-parent/dsp-common/src/main/java/com/sunlc/dsp/common/service/DataQueryService.java` 与 `XmlConfigCacheInvalidator.java`: 跨模块解耦 SPI 接口
  - `README.md`: "业务错误码"表（0000/4001-4107/5001-5003）

### - [ ] 12. JWT 签名鉴权 + 防重放（AOP 切面 + 时间戳容差）

- **简介**：数据服务用 JwtAuthAspect（AOP @Before）拦截 DataApiController 校验 Admin-Token、appId、timestamp（±5 分钟容差防重放）；离线服务用 Servlet JwtAuthInterceptor；JwtUtil 提供生成/解析/校验，密码经 BCrypt 加密。
- **核心度**：核心（README 核心特性"JWT 鉴权 — 签名验证+过期校验+白名单+防重放"；data-service 对外服务的安全门）
- **复杂度**：中
- **依赖**：统一 API 契约层（JwtUtil、ErrorCode）
- **证据**：
  - `dsp-parent/dsp-data-service/src/main/java/com/sunlc/dsp/dataservice/interceptor/JwtAuthAspect.java`: @Aspect @Before 拦截 DataApiController，TIMESTAMP_TOLERANCE_MINUTES=5 时间戳容差
  - `dsp-parent/dsp-offline-service/src/main/java/com/sunlc/dsp/offlineservice/interceptor/JwtAuthInterceptor.java`: 离线服务 Servlet 拦截器实现
  - `dsp-parent/dsp-common/src/main/java/com/sunlc/dsp/common/util/JwtUtil.java`: JWT 生成/解析/校验工具（被 5+ 文件引用）
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/config/SecurityConfig.java`: BCrypt 密码加密器 Bean

### - [ ] 13. RBAC 角色权限双防线（@RequireRole AOP + 前端 v-role 指令）

- **简介**：管理服务用自定义 @RequireRole 注解 + RoleCheckAspect 切面做方法级角色校验（被 11+ Controller 引用）；前端用 Vue 自定义指令 v-role 做 DOM 级隐藏，形成前后端双重权限防线。
- **核心度**：核心（项目特有的注解驱动 RBAC 模式，前后端联动，非通用权限框架如 Spring Security）
- **复杂度**：中
- **依赖**：JWT 签名鉴权（鉴权前置）、统一 API 契约层
- **证据**：
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/annotation/RequireRole.java`: 自定义角色注解
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/aspect/RoleCheckAspect.java`: @RequireRole 的 AOP 执行器
  - `dsp-admin-web/src/directives/role.js`: v-role 指令 + hasAnyRole 辅助函数
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/interceptor/AdminAuthInterceptor.java`: session/token 双模式管理服务鉴权拦截器

### - [ ] 14. 管理操作审计日志 AOP（自动记录 + 接口市场聚合）

- **简介**：AuditLogAspect（@Around）自动记录管理平台 Controller 操作到 audit_log 表，前端审计日志页面可查询；MarketplaceController 复用 audit_log 聚合最近 7 天统计，生成接口市场健康看板。
- **核心度**：边缘（横切关注点，非主线业务；但项目特有的切面驱动审计模式）
- **复杂度**：低
- **依赖**：统一 API 契约层（AuditLogService）
- **证据**：
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/interceptor/AuditLogAspect.java`: @Aspect @Around 自动记录，反射读 @PathVariable 参数
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/service/AuditLogService.java` 与 `entity/AuditLog.java`: 审计日志服务与实体
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/MarketplaceController.java`: 接口市场 + 健康看板（audit_log 聚合 7 天）
  - `dsp-admin-web/src/views/audit/` 与 `dsp-admin-web/src/views/marketplace/`: 对应前端页面

### - [ ] 15. 接口配置导入/导出（配置迁移 + SQL 安全校验）

- **简介**：ConfigImportExportController 提供接口配置（interface_info + interface_version + interface_template）整体导入/导出能力（@Transactional 保证一致性），导入时经 SqlSecurityValidator 校验 XML 内 SQL 安全性，便于环境间配置迁移与备份。
- **核心度**：边缘（运维支撑能力，非主线；但项目特有的配置整体迁移机制）
- **复杂度**：低
- **依赖**：XML DSL 数据查询引擎（SqlSecurityValidator）、XML 配置多级缓存（导入后触发失效）
- **证据**：
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/ConfigImportExportController.java`: @RequestMapping("/dsp/admin/config")，@Transactional 导入，依赖 SqlSecurityValidator 校验
  - `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/validator/SqlSecurityValidator.java`: SQL 安全校验器（防注入/危险关键字，被 6+ 文件引用）

### - [ ] 16. 接口模板管理（XML 模板 + Schema 自动生成 + 历史版本）

- **简介**：InterfaceTemplateService 管理可复用 XML 模板（create/update/publish/offline），支持从 Schema 自动生成 XML（generateXmlFromSchema），并维护模板历史版本（InterfaceTemplateHistory）。
- **核心度**：边缘（提效工具，非主线执行链路；但体现"零代码"理念下的模板沉淀机制）
- **复杂度**：中
- **依赖**：XML DSL 数据查询引擎、接口版本（模板与版本协同）
- **证据**：
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/service/InterfaceTemplateService.java`: createTemplate/updateTemplate/publishTemplate/generateXmlFromSchema/historyList 方法
  - `dsp-parent/dsp-core/src/main/java/com/sunlc/dsp/entity/InterfaceTemplate.java` 与 `InterfaceTemplateHistory.java`: 模板与历史版本实体
  - `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/InterfaceTemplateController.java`: 模板管理 Controller
  - `dsp-admin-web/src/views/template/`: 模板管理前端页面
