# 智能助手与 Text2API 能力规划

## Goal

规划 DSP 的智能助手、Text2API 页面能力，以及配套的 agent skills 工具链，让用户可以通过自然语言理解项目、生成联机接口配置，并逐步接近“零代码接口发布”的产品目标。

本任务当前处于规划阶段，只沉淀需求、领域术语、关键决策、技术设计和实施计划；不直接进入实现。

## Confirmed Facts

- DSP 是面向“零代码接口发布”的数据服务平台，核心能力是用 XML DSL 描述查询编排并发布对外联机接口。
- 现有项目已有本地知识库 `docs/project-knowledge/`，包含项目概览、技术栈与架构、开发规范、API 索引、构建打包部署和校验报告。
- 现有项目已有 XML DSL 参考文档 `docs/xml-dsl-reference.md` 和 16 个模板文件 `template/*.xml`，覆盖简单 SQL、动态 SQL、分页、多查询编排、多数据源、HTTP/Dubbo/Mongo 等场景。
- 前端已有接口管理、模板管理、数据源管理、审批、导入导出等模块；接口管理路由位于 `dsp-admin-web/src/router/index.js`。
- 前端已有配置导入弹窗 `dsp-admin-web/src/views/interface/ImportConfigDialog.vue`，读取 JSON 后调用 `configApi.importConfig`。
- 后端已有配置导入导出控制器 `ConfigImportExportController`，导入格式包含 `interfaceInfo`、`schema`、`template`，导入后会直接发布 Schema 和模板 XML，并刷新 XML 配置缓存。
- 现有 `configApi` 提供 `exportSingle`、`exportBatch`、`importConfig` 三个方法，可作为 Text2API 生成导入 JSON 的兼容目标。
- 智能助手一期后端边界确定为放进现有 `dsp-admin-service`，不新建独立 AI 服务。
- 现有管理端鉴权会在请求上设置 `adminUser` 和 `adminUserId`，可作为智能助手会话归属依据。
- 现有实体中 `InterfaceInfo`、`InterfaceTemplate`、`DatasourceConfig`、`SysUser` 使用 `deleted` 字段进行逻辑删除。

## Task Map

- `07-08-ai-assistant-chat`：实现智能助手普通项目问答，包括 AgentScope Java 2.0 适配、SSE 流式输出、文档优先源码兜底、引用来源、会话保存和逻辑删除。
- `07-08-text2api-web-flow`：实现 Text2API Web 流程，包括需求文本/文档输入、草稿状态机、Text2SQL、数据源元数据读取、模板选择确认、XML/导入 JSON 生成和最终导入发布。
- `07-08-dsp-agent-skills`：实现项目级 `dsp-*` skills 工具链，兼容 Codex、Claude Code 等 agent 工具。
- `07-08-knowledge-template-assets`：整理本地知识、源码兜底规则、XML 模板、导入 JSON 样例和纠错记忆规则。

## Cross-Task Ordering

- `knowledge-template-assets` 应先于或并行支撑 `ai-assistant-chat` 与 `text2api-web-flow`，因为两者都依赖本地知识和模板资产。
- `ai-assistant-chat` 与 `text2api-web-flow` 可并行规划；实现时共享 AgentScope Java 2.0 适配层、SSE 流式输出和会话/草稿持久化模式。
- `dsp-agent-skills` 可与 Web 能力并行，但必须复用同一套领域语言、XML 模板和导入 JSON 格式。

## Requirements

- 智能助手提供面向用户的对话能力。
- 智能助手一期作为管理后台能力建设，后端接口归属 `dsp-admin-service`。
- 智能助手判断问题是否属于本项目相关内容：属于则优先检索本地文档，文档检索不足时允许从源码兜底检索后再生成回答；不属于则直接由 AI 回答。
- 一期采用“本地文档优先、源码兜底”的检索策略，预留向量库、RAG 知识库等后续扩展能力。
- 项目问答回答需要展示引用来源；来源包括文档路径、源码文件路径、类名或方法名，不默认展示大段源码。
- 智能助手页面按栏位区分普通项目问答与 Text2API：普通对话在 `chat` 栏，Text2API 在 `text2api` 栏。
- 普通 `chat` 对话和 `text2api` 草稿都需要持久化保存。
- 用户需要能够手动删除自己的普通 `chat` 对话和 `text2api` 草稿；删除语义为逻辑删除。
- Text2API 一期仅针对 SQL 类联机接口。
- Text2API 输入支持用户直接输入/粘贴需求文本，也支持上传需求文档；一期上传文档只允许 Markdown 或 HTML 格式，单文件大小不超过 30KB。
- Text2API 流程需要先从自然语言、粘贴文本或需求文档生成接口输入输出，用户确认后保存接口定义草稿；随后提醒并进入 Text2SQL，生成 SQL 给用户确认；用户确认后生成 XML 和导入 JSON；全部确认后由用户最终点击“导入并发布”使接口生效。
- Text2API 中间步骤不创建已经生效的半成品联机接口。
- Text2SQL 生成 SQL 前必须具备表结构/字段说明依据；依据可以来自用户当前会话输入、知识库检索，或从已配置的数据源元数据中选择性读取。
- Text2API 一期需要支持受控读取已配置数据源的元数据，作为 Text2SQL 的表结构依据来源；元数据读取只允许读取库表、字段、类型、注释等结构信息，不读取业务数据行。
- 数据源元数据读取一期仅支持 JDBC SQL 类数据源：`MYSQL`、`DORIS`、`ORACLE`、`POSTGRESQL`；不支持 `HTTP`、`DUBBO`、`MONGO` 元数据读取。
- 如果没有表结构/字段说明依据，AI 必须先追问用户补充信息，不能生成看似可用但无依据的 SQL。
- Text2API 生成 XML 时必须基于模板目录选择模板并填充，不能让 AI 自由编写 XML。
- Text2API 需要在生成 XML 前展示模板选择结果，包括模板文件、适用场景、选择理由、填充点和重点复核项，并由用户确认。
- 如果现有模板无法覆盖用户需求，系统需要提示不支持或需要人工扩展模板，而不是自由生成偏离 DSL 的 XML。
- 用户在 Text2API 中指出错误时，Web 页面一期需要把修正过程和提炼出的重点保存到 Text2API 草稿的修正记录/学习记录中，但不自动写入 RAG 或项目知识库。
- 普通项目问答对所有已登录管理端用户开放。
- Text2API 草稿生成对所有已登录管理端用户开放。
- 数据源元数据读取限制为 `DEPT_MANAGER` 或 `ADMIN`。
- Text2API 最终“导入并发布”复用现有配置导入权限，仅允许 `IMPORTER` 或 `ADMIN`。
- AI 框架一期选择 AgentScope Java 2.0。
- 项目业务代码需要通过自有 AI 适配层调用 AgentScope Java 2.0，避免项目问答、Text2API 等业务逻辑直接依赖框架具体 API。
- 智能助手一期需要支持流式输出，覆盖项目问答和 Text2API 生成过程。
- 流式输出协议一期选择 SSE，后期保留迁移到 WebSocket 的可能。
- Agent skills 工具链需要支持 Claude Code、Codex 等多种 agent 工具调用，按 `dsp-grill` -> `dsp-text2interface` -> `dsp-text2sql` -> `dsp-text2api` 的顺序产出接口配置。
- 每个 `dsp-*` skill 完成后需要自动提示下一步建议调用的 skill，并提供交接文档或上下文路径；但不能自动越过用户确认进入下一阶段。
- `dsp-text2api` 最终生成的导入 JSON 需要兼容现有接口管理的导入导出配置格式。
- `dsp-*` skills 作为项目级 skills 放在仓库 `.agents/skills/` 下，不做全局安装。
- `dsp-*` skills 可以携带项目内模板、记忆或说明目录，用于保存 XML 模板、导入 JSON 样例、纠错记忆和阶段产物。

## Acceptance Criteria

- [ ] 规划文档说明智能助手一期范围、非项目问题处理方式、本地检索数据源和后续 RAG 扩展边界。
- [ ] 规划文档说明源码兜底检索的触发条件、路径白名单、忽略规则和回答引用方式。
- [ ] 项目问答回答能够展示本地文档或源码兜底检索的引用来源。
- [ ] 规划文档说明普通 chat 与 Text2API 草稿的保存、恢复、归属和手动逻辑删除规则。
- [ ] 规划文档说明 Text2API 的端到端用户流程、每一步的用户确认点、失败/修改回路和最终产物。
- [ ] Text2API 支持直接输入/粘贴需求文本，以及上传不超过 30KB 的 Markdown/HTML 需求文档。
- [ ] Text2API 只有最终“导入并发布”动作会让接口配置在 DSP 中生效，中间确认点只更新草稿。
- [ ] Text2SQL 只有在存在表结构/字段说明依据时才生成 SQL；缺少依据时必须追问。
- [ ] Text2API 支持用户选择已配置数据源并读取受控元数据，读取结果只包含结构信息，不包含业务数据行。
- [ ] 数据源元数据读取一期仅覆盖 `MYSQL`、`DORIS`、`ORACLE`、`POSTGRESQL`。
- [ ] XML 生成必须基于模板选择和填充；模板无法覆盖时要提示不支持或需要扩展模板。
- [ ] XML 生成前展示模板选择结果并获得用户确认。
- [ ] Web Text2API 的用户纠错会沉淀到草稿修正记录/学习记录，不自动更新 RAG 或项目知识库。
- [ ] Agent skills 工具链中的用户纠错需要强制沉淀为文档和记忆文件，并由用户决定是否更新 RAG 知识库。
- [ ] 智能助手与 Text2API 权限边界清晰：登录用户可问答/生成草稿，`DEPT_MANAGER`/`ADMIN` 可读元数据，`IMPORTER`/`ADMIN` 可导入并发布。
- [ ] AI 调用通过项目自有适配层接入 AgentScope Java 2.0，业务流程不直接绑定框架 API。
- [ ] 项目问答和 Text2API 生成过程支持流式输出，并能处理生成中、完成、失败和取消状态。
- [ ] 一期流式输出使用 SSE 协议实现，设计上保留后续迁移 WebSocket 的空间。
- [ ] 规划文档明确 Text2API 一期 SQL 类接口的输入输出、SQL、XML、导入 JSON 之间的关系。
- [ ] 规划文档明确 agent skills 的职责边界、调用顺序、每个 skill 的输入输出和需沉淀的记忆/文档。
- [ ] `dsp-*` skills 兼容 Codex、Claude Code 等 agent 工具，完成后自动提示下一步，但不跳过用户确认。
- [ ] `dsp-*` skills 的项目内目录位置和模板/记忆/产物目录约定明确。
- [ ] 设计文档说明推荐的后端/前端模块边界、与现有接口管理/模板管理/导入导出能力的集成方式。
- [ ] 实施计划拆分为可独立验证的阶段或子任务。
- [ ] 四个子任务的 PRD 均具备明确目标、需求和验收标准。
- [ ] 最终集成验收确认 chat、Text2API Web、agent skills、知识模板资产之间的术语、格式和权限边界一致。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
