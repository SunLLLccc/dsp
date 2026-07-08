# 智能助手与 Text2API 设计草案

## Architecture And Boundaries

- 一期智能助手后端放在 `dsp-admin-service` 内，作为管理后台能力提供，不新建 `dsp-ai-service`。
- AI 框架选择 AgentScope Java 2.0；项目内部通过自有 AI 适配层接入，业务流程不直接依赖 AgentScope Java 2.0 的具体 API。
- 前端在 `dsp-admin-web` 中新增智能助手入口，页面内区分 `chat` 与 `text2api` 两个工作区。
- `chat` 工作区面向项目问答：项目相关问题优先检索本地项目文档；当文档结果不足以回答具体实现、函数原理或代码路径问题时，再从源码兜底检索，然后调用 AI 生成回答；非项目问题直接调用 AI 回答。
- `text2api` 工作区面向 SQL 类联机接口生成：按用户确认点逐步生成接口输入输出、SQL、XML 和导入 JSON；中间步骤只保存草稿，最终由用户触发“导入并发布”后生效。
- 普通 `chat` 对话和 `text2api` 草稿都需要服务端持久化；会话归属优先使用管理端鉴权注入的 `adminUserId`，展示和审计可同时保留 `adminUser`。
- 用户可以手动逻辑删除自己的普通 `chat` 对话和 `text2api` 草稿；设计上贴合项目已有 `deleted` 逻辑删除风格。

## Integration Points

- 本地文档检索的一期候选数据源包括 `docs/project-knowledge/`、`docs/xml-dsl-reference.md`、`docs/engine-architecture.md`、`template/README.md` 和 `template/*.xml`。
- 源码兜底检索应只在文档检索不足时触发，优先限定在 `dsp-parent/*/src/main/java`、`dsp-parent/*/src/test/java`、`dsp-admin-web/src` 等项目源码目录，并排除 `target/`、`node_modules/`、构建产物、密钥配置和大文件。
- 项目问答回答需要展示引用来源。文档来源展示文档路径和标题；源码来源展示文件路径、类名或方法名，回答正文优先解释原理和调用链，不默认暴露大段源码。
- Text2API 最终产物需要兼容现有配置导入导出格式：`interfaceInfo`、`schema`、`template`。
- 现有 `/dsp/admin/config/import` 导入后会直接发布 Schema 和模板 XML；Text2API 应把它作为最终“导入并发布”动作的执行入口，而不是在接口定义确认阶段调用。

## Persistence Model

- `chat` 至少需要保存会话摘要、消息列表、创建人、创建时间、更新时间和逻辑删除标记。
- `text2api` 至少需要保存需求原文、当前阶段、接口输入输出草稿、SQL 草稿、模板选择结果、XML 草稿、导入 JSON 草稿、修正记录/学习记录、创建人、创建时间、更新时间和逻辑删除标记。
- 恢复体验应以“用户回到智能助手页面后能看到自己的历史 chat 和未完成 text2api 草稿”为最低目标。

## Text2API Lifecycle

- 阶段 1：用户提交自然语言、粘贴需求文本或上传需求文档，AI 生成接口输入输出草稿。
- 阶段 2：用户确认接口定义草稿，系统保存草稿但不发布接口。
- 阶段 3：AI 基于明确的表结构/字段说明依据生成 SQL，用户确认或指出错误；错误修正过程需要反映到草稿。
- 阶段 4：AI 基于接口定义和 SQL 选择 XML 模板，展示模板文件、适用场景、选择理由、填充点和重点复核项，用户确认模板。
- 阶段 5：AI 基于确认后的模板填充 XML，并生成导入 JSON，用户复核。
- 阶段 6：用户最终点击“导入并发布”，系统调用兼容现有导入格式的发布能力，使接口生效。

## XML Generation

- XML 生成必须模板驱动：先根据接口输入输出、SQL 数量、SQL 依赖关系、分页/动态条件/多结果集等特征选择模板，再填充模板。
- AI 不允许自由编写 XML；生成结果必须可追溯到具体模板文件。
- 生成 XML 前需要向用户展示所选模板、适用场景、选择原因、填充点和用户需要重点复核的 DSL 片段，并获得用户确认。
- 如果 `template/` 现有模板无法覆盖需求，系统应提示不支持或需要人工扩展模板，不生成偏离 DSL 约定的 XML。

## Correction Learning

- Web Text2API 中用户指出错误时，系统应保存原始错误、用户纠正、AI 修改结果和提炼出的重点信息到草稿的修正记录/学习记录。
- Web 一期不自动写入 RAG 或项目知识库，避免未经审核的会话内容污染知识。
- Agent skills 工具链中，`dsp-text2sql` 和 `dsp-text2api` 的纠错需要强制输出文档和记忆文件；是否更新 RAG 知识库由用户自行确认。

## Agent Skills

- DSP 专用 skills 放在项目内 `.agents/skills/` 下，和项目版本一起演进。
- 规划中的项目级 skills 包括 `dsp-grill`、`dsp-text2interface`、`dsp-text2sql`、`dsp-text2api`。
- 每个 skill 需要有清晰的输入、输出、确认点和下一步调用约定。
- 每个 skill 完成后自动提示下一步建议调用的 skill，并附带交接文档路径或必要上下文摘要；下一步执行必须等待用户确认。
- skills 设计需要兼容 Codex、Claude Code 等 agent 工具，不依赖单一工具的私有能力；跨工具交接优先通过 Markdown 文档、模板文件、记忆文件和明确的目录约定完成。
- `dsp-text2api` 需要携带或引用 XML 模板目录和导入 JSON 样例，生成结果必须兼容现有配置导入导出格式。
- skills 可按需包含 `templates/`、`memory/`、`notes/` 或类似目录，用于保存模板、记忆文件、纠错总结和阶段产物。

## Task Decomposition

- Parent task `07-08-ai-assistant-text2api-planning` owns shared requirements, cross-task terminology, integration constraints and final review.
- Child task `07-08-ai-assistant-chat` owns ordinary project Q&A.
- Child task `07-08-text2api-web-flow` owns the Web Text2API workflow.
- Child task `07-08-dsp-agent-skills` owns project-level agent skills.
- Child task `07-08-knowledge-template-assets` owns local knowledge, templates, JSON samples and memory conventions.

## Text2SQL Grounding

- SQL 生成必须有表结构/字段说明依据，不能仅凭接口意图或字段名猜测库表。
- 表结构依据允许来自三类来源：用户当前会话输入、知识库检索结果、已配置数据源的元数据读取结果。
- 如果依据不足，AI 应先追问缺失的表名、字段、关联关系、过滤条件或数据源信息，不生成无依据 SQL。
- SQL 结果需要说明所用表、关键字段、过滤条件、排序/分页逻辑，以及多段 SQL 之间的依赖关系；若多段 SQL 互不依赖，需要明确说明是多结果集。

## Datasource Metadata

- 一期需要新增受控的数据源元数据读取能力，供 Text2SQL 获取表结构依据。
- 用户应先选择一个已配置且可用的数据源，再触发元数据读取。
- 一期仅支持 JDBC SQL 类数据源：`MYSQL`、`DORIS`、`ORACLE`、`POSTGRESQL`。
- 一期不支持 `HTTP`、`DUBBO`、`MONGO` 元数据读取；这些类型不作为 Text2SQL 的自动表结构来源。
- 元数据读取只返回结构信息：库/Schema、表名、字段名、字段类型、是否可空、主键/索引信息和字段/表注释；不返回业务数据行。
- 元数据读取应复用现有数据源配置、连接测试、密码加密/脱敏和 JDBC 类型白名单思路。
- 元数据接口需要有超时、最大表数/字段数限制，并避免在日志中输出 JDBC 密码、完整连接串或敏感错误堆栈。

## Permissions

- 普通项目问答：所有已登录管理端用户可用。
- Text2API 草稿生成：所有已登录管理端用户可用。
- 数据源元数据读取：限制为 `DEPT_MANAGER` 或 `ADMIN`。
- 最终“导入并发布”：复用现有配置导入权限，限制为 `IMPORTER` 或 `ADMIN`。

## AI Adapter

- 后端应定义项目自有 AI 适配层，例如 `AssistantService` / `AiGateway`，向业务层提供项目问答、Text2API 阶段生成、SQL 生成、XML 生成等能力。
- AgentScope Java 2.0 作为适配层内部实现，业务 Controller 和领域服务不直接使用 AgentScope Java 2.0 API。
- 适配层需要为模型供应商配置、API Key、超时、重试、降级、审计和流式响应提供统一封装。
- AgentScope Java 2.0 官方文档显示其 v2 引入了权限、Middleware、Workspace 等生产化抽象；项目适配层应优先吸收这些能力，而不是把框架概念泄漏到前端或业务数据模型。

## Streaming

- 一期项目问答和 Text2API 生成过程都需要支持流式输出。
- 一期流式协议选择 SSE；后期如果需要更强双向协作、心跳控制或多路复用，再考虑迁移 WebSocket。
- 前端需要展示生成中、完成、失败和取消状态。
- 后端接口协议需要能推送增量内容、最终结果、错误事件和完成事件。
- Text2API 流式输出不等于边生成边发布；中间内容仍写入草稿，只有用户最终确认后才导入并发布。

## Requirement Document Upload

- 一期 Text2API 支持上传需求文档，但只允许 Markdown 和 HTML 格式。
- 单个需求文档大小不超过 30KB。
- 上传内容进入 Text2API 草稿，作为 AI 生成接口定义的输入依据。
- 不支持 PDF、Word、Excel、图片或压缩包解析；这些格式需要用户转换为 Markdown/HTML 或复制文本后再提交。

## Tradeoffs

- 放入 `dsp-admin-service` 能复用现有管理后台鉴权、角色、接口管理、模板管理和导入导出能力，降低一期闭环成本。
- 未来如果 AI 能力需要独立扩缩容、模型调用隔离、异步任务编排或面向多个前端入口，再评估拆分为独立服务。
