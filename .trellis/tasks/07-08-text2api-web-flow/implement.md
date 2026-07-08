# Text2API Web 流程 — 实施计划

> 对应 `design.md`。按阶段拆分，每阶段有验证检查点。
> 复用 chat 已沉淀的 AiGateway/SSE/AssistantProperties 基础设施。

## 阶段总览

| 阶段 | 产出 | 验证检查点 |
|------|------|-----------|
| T0 持久化 + 配置导入服务抽取 | 草稿表/实体 + ConfigImportService | 单测：草稿 CRUD + 导入服务 |
| T1 元数据读取 | DatasourceMetaService（JDBC 结构读取） | 单测：tableTypes/schema过滤/pattern escape/只读结构/超时/脱敏 |
| T2 模板选择 | TemplateSelector + Text2ApiAssetLoader | 单测：特征匹配/SQL类过滤/覆盖不到提示 |
| T3 阶段生成编排 | Text2ApiService（状态机 + SchemaEvidence 门禁 + AiGateway） | 单测：阶段流转/evidence空→追问不调网关/草稿落库/回退invalidation |
| T4 SSE + Controller | Text2ApiController + ChatSseEmitter 复用 | 集成测试：阶段生成 SSE + 权限 |
| T5 导入发布 | 复用 ConfigImportService + 权限 | 单测：权限校验/valid校验/失败重试 |
| T6 前端 | text2api 工作区（文档前端读取） | 手动联调：端到端 |
| T7 真实联调 | 真实模型 + 真实数据源 | 6 阶段端到端 + 导入后可调用 |

---

## T0. 持久化层 + 配置导入服务抽取

### T0.1 草稿持久化

1. `dsp-core/entity`：`AiText2ApiDraft`（字段见 design 第 6 节，含 `confirmed_stage`/`invalidated_from_stage`/`schema_evidence`/`publish_error`，`@TableLogic deleted`）
2. `dsp-core/mapper`：`AiText2ApiDraftMapper`
3. `dsp-core/service`：`AiText2ApiDraftService`（`getOwnedDraft(draftId, userId)`，同 chat 越权防护）
4. 建表 SQL 追加到 `init.sql`
5. **检查点**：单测草稿 CRUD + 越权返回 null + 逻辑删除

### T0.2 配置导入服务抽取（必改1）

1. 新增 `ConfigImportService`（`dsp-admin-service`）：
   - `Map<String,Object> importConfig(Map<String,Object> configData, String operator)`
   - 把现有 `ConfigImportExportController.importConfig` 的**业务逻辑**（事务、新建/覆盖接口、Schema 发布、模板发布、缓存刷新）迁入
2. `ConfigImportExportController.importConfig` 改为调 `ConfigImportService`（Controller 只作 HTTP 入口，保留 `@RequireRole`）
3. **不破坏**现有导入导出功能（Controller 行为不变）
4. **检查点**：
   - 单测：ConfigImportService.importConfig 正常导入
   - 确认现有 Controller 导入行为不变（回归）

## T1. 数据源元数据读取（核心新能力）

1. `DatasourceMetaService`（`dsp-admin-service`）：
   - `listReadableDatasources()`：列出 JDBC SQL 类已启用数据源
   - `listTables(dsName)`：`getMetaData().getTables(catalog, schema, null, {"TABLE"})`，过滤系统 schema
   - `listColumns(dsName, table)`：table **先从 listTables 白名单校验**，再 `getColumns`
2. 安全约束（**程序级强制**，design 5.2）：
   - tableTypes 只 `TABLE`
   - 系统 schema 过滤（information_schema/mysql/performance_schema/sys/pg_catalog/pg_toast/Oracle系统）
   - table 参数白名单校验（不直接当 pattern）
   - 返回不含 jdbcUrl/username/password/extraConfig
   - 日志脱敏（不打印完整 URL/SQLException 原文）
   - 配置项：`dsp.assistant.metadata.max-tables`(100)/`max-columns`(200)/`timeout-seconds`(10)
3. 建连接：复用 `DatasourceManagerServiceImpl.testConnection` 模式
4. **检查点**：
   - 单测：tableTypes 只 TABLE（VIEW 不返回）
   - 单测：系统 schema 被过滤
   - 单测：table 参数白名单校验（非法表名拒绝）
   - 单测：返回数据无连接串/密码
   - 单测：超时/表数/字段数上限

## T2. 模板选择

1. `Text2ApiAssetLoader`（`dsp-admin-service`）：
   - 读取 `template-index.json` + `import-json-example.json`
   - 复用 AssetSourceLoader 的路径安全策略，独立成类
2. `TemplateSelector`：
   - `select(interfaceDraft, sqlDraft)` → 匹配模板
   - 一期只选 `appliesTo` 含 `sql`
   - 返回模板文件/场景/理由/填充点/复核项
   - 无匹配返回「不支持，需人工扩展模板」
3. **检查点**：
   - 单测：单SQL→01/15；动态条件→02；分页→03/04；多查询→09/10
   - 单测：非 SQL 模板不选中
   - 单测：无匹配→明确提示

## T3. 阶段生成编排（Text2ApiService）

### T3.1 SchemaEvidence 门禁（必改3，先于 AiGateway 实现）

1. `SchemaEvidence`：封装表结构依据（表名/字段/类型/注释/来源）
2. 阶段 3 进入 AiGateway **前**检查：
   - evidence 为空 → **不调 AiGateway** → 返回 `needs_more_info`（追问）
   - evidence 非空 → 才调 AiGateway 生成 SQL
3. **检查点**：
   - 单测：无 evidence 时 aiGateway **不被调用**（verify never）
   - 单测：有 evidence 时才调用

### T3.2 阶段编排

1. `Text2ApiService`：
   - `generate(draftId, stage, input, userId)` → SSE emitter
   - 阶段 2：AI 从需求生成接口定义
   - 阶段 3：SchemaEvidence 门禁 → Text2SQL（多段结构化 JSON）
   - 阶段 4：调 `TemplateSelector`
   - 阶段 5：基于模板填充 + 生成导入 JSON
   - 每阶段产物落草稿对应字段
2. 回退 invalidation（必改2）：
   - `rollback(draftId, toStage)`：设 `invalidated_from_stage = toStage + 1`
   - `confirm(draftId, stage)`：推进 `confirmed_stage`
   - publish 校验阶段 5 valid
3. **检查点**：
   - 单测：阶段流转 1→2→3→4→5
   - 单测：无依据追问（aiGateway never）
   - 单测：回退后 invalidated 标记
   - 单测：回退后重新生成清除 invalidated

## T4. SSE + Controller

1. `Text2ApiController`：
   - 草稿 CRUD + generate(SSE) + confirm + rollback
   - datasources/tables/columns（`@RequireRole({"DEPT_MANAGER","ADMIN"})`）
   - publish（`@RequireRole({"IMPORTER","ADMIN"})`）
2. SSE 复用 `ChatSseEmitter`
3. 并发限制：复用 `ChatConcurrencyLimiter`（与 chat 共用 AI 生成额度）
4. **检查点**：
   - 单测：Controller 路径/权限
   - 集成测试：generate SSE 事件链

## T5. 导入发布

1. `publish(draftId, userId)`：
   - 校验 `confirmed_stage == 5` 且阶段 5 产物 valid（`invalidated_from_stage` 未覆盖 5）
   - 校验权限 `IMPORTER`/`ADMIN`
   - 取 `import_json_draft`，调 `ConfigImportService.importConfig`（T0.2 抽取的服务）
   - 成功 → stage=6/status=1
   - 失败 → 保持 stage=5/status=0，记录 `publish_error`，允许重试
2. 导入 JSON 兼容性：对照 `import-json-example.json`
3. **检查点**：
   - 单测：权限校验
   - 单测：valid 校验（invalidated 时拒绝）
   - 单测：失败时记录 publish_error + 允许重试

## T6. 前端（text2api 工作区）

1. 智能助手页面新增 `text2api` 分栏
2. 草稿列表 + 阶段流转 UI（确认点 + 回退）
3. **文档上传**：前端 `FileReader` 本地读取（.md/.html/.htm ≤30KB），作为 requirementText 写入草稿，**无后端 upload 接口**
4. 数据源选择 + 元数据展示
5. 模板确认面板、XML/JSON 复核面板、导入发布按钮
6. SSE 消费复用 `sseParser.js`
7. **检查点**：`npm run build` + 手动联调

## T7. 真实联调

1. 配置真实 model/apiKey/baseUrl（复用 `DSP_ASSISTANT_*`）
2. 配置真实 JDBC 数据源
3. 端到端验证 6 阶段
4. 验证无依据追问
5. 验证导入后接口真实可用（调 `/dsp/api/{transno}`）
6. **检查点**：6 阶段端到端通过 + 导入接口可调用

---

## 阶段间依赖与并行

```
T0(草稿+导入服务) ──> T3(编排) ──> T4(Controller) ──> T5(发布) ──> T7(联调)
T1(元数据) ─────────┤                                          ↑
T2(模板选择) ───────┘                                     T6(前端，与 T5 后并行)
```

- T0 是前置（草稿表 + 导入服务抽取）
- T1（元数据）和 T2（模板选择）可并行
- T3 依赖 T0/T1/T2，且**必须先实现 SchemaEvidence 门禁再接 AiGateway**
- T4 依赖 T3
- T5 依赖 T0.2（ConfigImportService）+ T4
- T6（前端）可在 T5 后并行

## 验收对照

| 验收项 | 实现阶段 |
|--------|---------|
| 草稿 CRUD | T0+T4 |
| 文档上传 ≤30KB | T6（前端读取） |
| 各阶段确认点 | T3+T6 |
| 中间不发布 | T3 |
| Text2SQL 追问 | T3（SchemaEvidence 门禁） |
| 元数据读取 | T1+T4 |
| 模板展示 | T2+T6 |
| 导入 JSON 兼容 | T5 |
| 导入权限 | T5 |
