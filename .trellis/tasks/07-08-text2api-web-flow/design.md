# Text2API Web 流程 — 技术设计

> 本子任务实现管理后台 Text2API Web 流程。复用 chat 已沉淀的 AiGateway / SSE / AssistantProperties 基础设施。
> 不实现 dsp-* skills、不实现 RAG 写入。

## 1. 范围与边界

| 范畴 | 说明 |
|------|------|
| 后端归属 | `dsp-admin-service`，复用 chat 的 `ai` 适配包（AiGateway/StreamHandle） |
| 前端 | `dsp-admin-web` 智能助手页面的 `text2api` 工作区（与 chat 工作区并列） |
| AI 能力 | 复用 `AiGateway.streamChat`，Text2API 各阶段生成走流式 |
| 接口范围 | 一期仅 SQL 类联机接口 |
| 不做 | dsp-* skills、RAG 写入、HTTP/Dubbo/Mongo 接口生成 |

## 2. 与 chat 的复用关系

chat（`07-08-ai-assistant-chat`）已沉淀的基础设施，Text2API 直接复用：

| chat 资产 | Text2API 复用方式 |
|-----------|------------------|
| `AiGateway` / `StreamHandle` / `AgentScopeAiGateway` | 各阶段 AI 生成走同一网关 |
| `AssistantProperties` | `dsp.assistant.*` 配置 |
| `ChatSseEmitter` | SSE 事件封装（text2api 生成过程也流式） |
| `sseParser.js` | 前端 SSE 解析（纯函数，复用） |
| `CurrentUserResolver` | adminUserId 稳健转换 |

**不直接复用，但思路复用**：
- `ChatConcurrencyLimiter`：一期**共用同一 AI 并发额度**（同一用户同时只能有一个 AI 生成，无论 chat 还是 text2api）。文档明确这是「AI 生成全局单用户并发限制」。
- `AssetSourceLoader`：**不复用该类**（职责是 retrieval-sources.json）。Text2API 新增 `Text2ApiAssetLoader`（见第 8 节），复用同样的路径安全校验策略。

**不复用**：`AiChatSession`/`AiChatMessage`（text2api 有独立草稿表）、`RetrievalService`（text2api 用数据源元数据代替文档检索）。

## 3. 6 阶段状态机 + 回退 invalidation

### 3.1 阶段流转

```
[1 需求输入] → [2 接口定义] → [3 Text2SQL] → [4 模板选择] → [5 XML/JSON生成] → [6 导入发布]
     ↑              ↑              ↑              ↑              ↑
     └──────────────┴─── 用户可回退修正 ──┴──────────────┘
```

| 阶段 | 输入 | AI 产出 | 用户确认点 |
|------|------|--------|-----------|
| 1 需求输入 | 自然语言/粘贴文本/前端读取MD·HTML(≤30KB) | — | 确认需求内容 |
| 2 接口定义 | 需求文本 | 接口输入输出字段 | 确认接口定义 |
| 3 Text2SQL | 接口定义 + **SchemaEvidence** | SQL（多段含依赖说明） | 确认 SQL |
| 4 模板选择 | 接口定义 + SQL 特征 | 模板选择结果 | 确认模板 |
| 5 XML/JSON 生成 | 确认后的模板 + SQL + 接口定义 | XML + 导入 JSON | 复核 XML/JSON |
| 6 导入发布 | 确认后的导入 JSON | 调配置导入业务服务 | 最终点击导入 |

**关键约束**：阶段 1-5 只更新草稿，**不创建已生效接口**；只有阶段 6 调真实导入。

### 3.2 回退 invalidation 机制（定死）

回退到阶段 N 时：
- **保留**后续阶段产物（用于审计/对比，不删除）
- 标记后续产物为 **invalidated**（草稿新增 `invalidated_from_stage` 字段）
- invalidated 产物**不允许继续发布**（publish 必须校验阶段 5 产物 valid）
- 用户从阶段 N 重新确认/生成到阶段 5 后，publish 才允许
- 草稿新增 `confirmed_stage` 字段：记录用户最后确认到的阶段

## 4. Text2SQL 依据门禁（SchemaEvidence，程序级强制）

**SQL 生成必须有表结构依据，这是程序门禁，不只是 prompt 约束。**

### 4.1 SchemaEvidence 来源（只允许两类）

| 来源 | 说明 |
|------|------|
| 用户输入 | 用户在需求文本/对话中明确提供的表结构说明 |
| 数据源元数据 | 用户选择的数据源元数据读取结果（见第 5 节） |

### 4.2 门禁实现

阶段 3 进入 AiGateway **前**，后端先构造 `SchemaEvidence`：
- 若 `SchemaEvidence` 为空：
  - **不调用** AiGateway（SQL 生成 prompt 不发出）
  - 返回 `needs_more_info` 类型的阶段结果（追问表名/字段/关联/过滤条件）
  - 草稿 stage 保持在 Text2SQL 待补充状态
- 若 `SchemaEvidence` 非空：
  - 才允许调用 AiGateway 生成 SQL
  - SQL 结果需说明：所用表、关键字段、过滤条件、排序/分页、多段 SQL 依赖关系

### 4.3 多段 SQL 结构化（定死）

SQL 草稿用结构化 JSON 数组，每段含：
```json
{
  "sqlId": "q1",
  "sql": "SELECT ...",
  "purpose": "查询用户基本信息",
  "dependsOn": [],
  "outputAlias": "userInfo",
  "relationDescription": "无依赖，多结果集"
}
```
- 无依赖时 `dependsOn: []`，明确标注「多结果集」

## 5. 数据源元数据读取（新能力，安全细节定死）

### 5.1 范围

- **仅支持 JDBC SQL 类**：MYSQL、DORIS、ORACLE、POSTGRESQL
- **不支持**：HTTP、DUBBO、MONGO
- **只读结构**：表名、字段名、字段类型、是否可空、主键、注释
- **绝不读业务数据行**（不执行任何业务 SQL）
- **权限**：`DEPT_MANAGER` 或 `ADMIN`
- **一期用户必须主动选择数据源/表**，AI 不自动读表（安全、可控、可解释）

### 5.2 安全约束（程序级，非建议）

| 约束 | 实现 |
|------|------|
| `getTables` tableTypes | **只 `TABLE`**，一期不含 VIEW（需单独确认后才加） |
| 系统 schema 过滤 | 默认跳过：`information_schema`/`mysql`/`performance_schema`/`sys`/`pg_catalog`/`pg_toast`/Oracle 系统 schema |
| catalog/schema 过滤 | 支持 catalog/schema 参数，默认值过滤系统库 |
| table 参数 pattern escape | `getColumns` 的 table 参数**先从 listTables 白名单校验**，不直接当 SQL LIKE pattern 传入；若必须用 pattern，`_`/`%` 需 escape |
| 返回前端不泄露 | 返回的 metadata **不含** jdbcUrl/username/password/extraConfig 原文 |
| 日志脱敏 | 不打印完整 JDBC URL、SQLException message 原文和堆栈（复用 `classifyConnectionError`） |
| 超时 | 连接超时 10s |
| 最大表数 | 100（配置项 `dsp.assistant.metadata.max-tables`，默认 100） |
| 最大字段数 | 每表 200（配置项 `dsp.assistant.metadata.max-columns`，默认 200） |

### 5.3 实现

复用 `DatasourceManagerServiceImpl.testConnection` 的建连接模式：
```
DatasourceConfig（按 dsName 查库）→ 解密 ENC → DruidDataSource 临时连接
→ conn.getMetaData().getTables(catalog, schema, null, {"TABLE"})
→ 过滤系统 schema
→ getColumns 同理（table 参数白名单校验）
→ 封装为结构信息返回（无连接串/密码）
→ 关闭临时 DataSource
```

## 6. 持久化模型

### 表 `ai_text2api_draft`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | bigint PK auto | |
| `draft_id` | varchar(64) | 业务草稿 ID（UUID），唯一索引 |
| `user_id` | bigint | 归属用户 |
| `user_name` | varchar(64) | 冗余展示 |
| `stage` | tinyint | 当前阶段：1-需求 2-接口定义 3-SQL 4-模板 5-XML 6-已发布 |
| `confirmed_stage` | tinyint | 用户最后确认到的阶段 |
| `invalidated_from_stage` | tinyint | 回退时标记的失效起点（null=无失效） |
| `status` | tinyint | 0-进行中 1-已完成 2-已取消 |
| `requirement_text` | mediumtext | 需求原文 |
| `interface_draft` | text | 接口定义 JSON |
| `schema_evidence` | text | Text2SQL 依据 JSON（表结构/元数据） |
| `sql_draft` | text | SQL 草稿（结构化 JSON 数组，见 4.3） |
| `template_selection` | text | 模板选择结果 JSON |
| `xml_draft` | text | XML 草稿 |
| `import_json_draft` | text | 导入 JSON 草稿 |
| `correction_records` | text | 修正记录 JSON（见 ai-assets/correction-memory.md） |
| `publish_error` | text | 导入失败时的错误信息（允许重试） |
| `created_time/updated_time` | datetime | |
| `deleted` | tinyint default 0 | 逻辑删除 |

- 软删语义：与 chat 一致，只逻辑删除草稿，修正记录保留审计
- 建表 SQL 追加到 `dsp-core/src/main/resources/db/init.sql`

## 7. 导入发布（复用配置导入业务服务，不调 Controller）

### 7.1 抽取 ConfigImportService

**必改：不直接调 `ConfigImportExportController.importConfig`。** 抽取业务服务：
- 新增 `ConfigImportService`（`dsp-core` 或 `dsp-admin-service`）：
  - `Map<String,Object> importConfig(Map<String,Object> configData, String operator)`
  - 包含原 Controller 的导入事务逻辑（新建/覆盖接口、Schema 发布、模板发布、缓存刷新）
- 现有 `ConfigImportExportController.importConfig` 改为调这个 Service（Controller 只作 HTTP 入口）
- Text2API `publish` 也调这个 Service

### 7.2 Text2API publish 流程

1. 校验草稿 `confirmed_stage == 5` 且阶段 5 产物 **valid**（未被 invalidated）
2. 校验权限 `IMPORTER`/`ADMIN`（`@RequireRole` 在 Controller 上）
3. 取草稿 `import_json_draft`
4. 调 `ConfigImportService.importConfig`
5. 成功 → stage=6/status=1
6. **失败** → 草稿保持 stage=5/status=0，记录 `publish_error`，允许修正后重试（导入事务由 ConfigImportService 保证，Text2API 不自行半回滚）

### 7.3 导入 JSON 字段语义（复用 ai-assets 结论）

- `schema.inputSchema` 是 **XML 配置字符串**（非 JSON Schema），经 `sqlSecurityValidator.validateXmlConfig` 校验
- `schema.outputSchema` 是 JSON Schema
- 运行时执行以 `template.xmlContent` 为准
- 不混用旧 `generateXmlFromSchema()` 语义

## 8. 模板选择与资产加载

### 8.1 Text2ApiAssetLoader（新增，不复用 AssetSourceLoader）

- 读取 `DSP_ASSISTANT_ASSETS_PATH/template-index.json` 和 `import-json-example.json`
- 复用 `AssetSourceLoader` 的**路径安全校验策略**（normalize + projectRoot + toRealPath），但独立成类，避免 retrieval loader 职责过宽

### 8.2 模板选择

- 消费 `template-index.json`
- 根据 SQL 数量、依赖关系、分页需求、动态条件等特征匹配
- **一期只选 SQL 类模板**（`appliesTo` 含 `sql`），非 SQL 标注不可选
- 向用户展示：模板文件/场景/理由/填充点/复核项
- **AI 不自由编写 XML**；模板覆盖不到时提示「不支持，需人工扩展模板」

### 8.3 XML 生成

基于用户确认的模板填充，结果可追溯到 `template-index.json` 具体模板。导入 JSON 对照 `import-json-example.json` 结构。

## 9. HTTP / SSE 接口

路径前缀 `/dsp/admin/assistant/text2api`，走 AdminAuthInterceptor。

| 方法 | 路径 | 权限 | 用途 |
|------|------|------|------|
| POST | `/drafts` | 登录用户 | 创建草稿 |
| GET | `/drafts` | 登录用户 | 我的草稿列表 |
| GET | `/drafts/{draftId}` | 登录用户 | 草稿详情 |
| DELETE | `/drafts/{draftId}` | 登录用户 | 逻辑删除草稿 |
| POST | `/drafts/{draftId}/generate` | 登录用户 | 阶段生成（SSE，body 指定 stage + 输入） |
| POST | `/drafts/{draftId}/confirm` | 登录用户 | 确认某阶段（推进 confirmed_stage） |
| POST | `/drafts/{draftId}/rollback` | 登录用户 | 回退到某阶段（设置 invalidated_from_stage） |
| POST | `/drafts/{draftId}/publish` | `IMPORTER`/`ADMIN` | 导入并发布 |
| GET | `/datasources` | `DEPT_MANAGER`/`ADMIN` | 可读元数据的 JDBC 数据源 |
| GET | `/datasources/{dsName}/tables` | 同上 | 表名列表（过滤系统 schema） |
| GET | `/datasources/{dsName}/tables/{table}/columns` | 同上 | 字段结构（table 白名单校验） |

- 草稿归属校验：`draft.user_id == 当前 adminUserId`
- 无独立 upload 接口（文档上传前端读取，见第 10 节）

## 10. 前端（text2api 工作区）

### 文档上传（定死：前端本地读取）

- 前端用 `FileReader` 本地读取文件内容
- 校验扩展名 `.md`/`.html`/`.htm`、大小 ≤30KB
- 作为 `requirementText` 写入草稿
- **后端不存原始文件**，只存文本内容
- 一期不开后端 upload 接口（简单可控）

### 其它

- 智能助手页面新增 `text2api` 分栏（与 `chat` 并列）
- 草稿列表 + 阶段流转 UI（每阶段确认点 + 回退）
- 数据源选择 + 元数据展示（权限校验后）
- 模板选择确认面板、XML/JSON 复核面板、导入发布按钮
- SSE 消费复用 `sseParser.js` + `fetch + ReadableStream`

## 11. 纠错记忆

复用 `ai-assets/correction-memory.md` 规则：
- Web Text2API 用户纠错只进草稿的 `correction_records`
- **不自动写 RAG 或项目知识库**

## 12. 权限分层

| 操作 | 权限 |
|------|------|
| 草稿创建/生成/恢复/删除 | 所有登录用户 |
| 数据源元数据读取 | `DEPT_MANAGER` 或 `ADMIN` |
| 最终导入并发布 | `IMPORTER` 或 `ADMIN` |

## 13. 风险与取舍

- **数据源元数据读取**是新能力：严格 tableTypes/schema 过滤/pattern escape/配置项上限/日志脱敏
- **Text2SQL 依据门禁**是程序级（SchemaEvidence 为空不调 AiGateway），不只靠 prompt
- **回退 invalidation**保留产物 + 标记失效，publish 校验 valid
- **导入复用业务服务**（ConfigImportService），不调 Controller
- **模板覆盖度**一期 16 个模板，覆盖不到明确提示不支持
- **并发额度**与 chat 共用（同一用户全局单 AI 生成）

## 14. 开放项结论（已定死）

| 开放项 | 结论 |
|--------|------|
| Text2SQL 依据获取方式 | 一期用户主动选数据源/表，AI 不自动读表 |
| 草稿回退粒度 | 保留后续产物 + 标记 invalidated，不删除 |
| 多段 SQL 依赖 | 结构化 JSON（sqlId/sql/purpose/dependsOn/outputAlias/relationDescription） |
| 导入发布失败回滚 | 草稿保持 stage=5/status=0，记录 publishError，允许重试 |
