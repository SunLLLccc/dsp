# ai-assets — Web 侧智能助手与 Text2API 默认资产

本目录是 **Web 侧**（智能助手 `chat` + Text2API）默认样例资产目录，沉淀项目问答检索范围、XML 模板选择规则、配置导入 JSON 样例和纠错记忆规则，供后续 `07-08-ai-assistant-chat`、`07-08-text2api-web-flow` 子任务直接消费。

> 本目录只产出资产与规则文件，**不实现** AI 调用、SSE、Text2API 状态机、agent skills 或后端读取逻辑。`dsp-*` skills 的专用资产放在各自 `.agents/skills/dsp-*/` 目录下，不放在这里。

## 1. 本地默认目录与生产外部目录的关系

- 仓库根目录 `ai-assets/` 是**本地开发/测试默认样例资产目录**，随仓库版本演进，可用于本地联调和单元测试。
- 生产环境**不直接读仓库内的 `ai-assets/`**，而是通过环境变量指向部署机外部资产目录，便于热更新、挂载和权限隔离。
- 业务代码（未来的 `AssistantService` / `AiGateway` 等）**只读取配置路径，不硬编码仓库相对路径**。本目录的存在仅为默认样例与版本审查提供单一事实来源。

### 环境变量约定

| 环境变量 | 作用 | 默认值（未配置时） |
|---------|------|-------------------|
| `DSP_AI_ASSETS_PATH` | Web 侧资产目录绝对路径。后端读取本目录下的 JSON 索引和 Markdown 规则。 | 仓库内 `ai-assets/`（仅用于本地开发；生产必须显式配置外部路径） |

> 环境变量名为**规划建议名**，最终命名以 `07-08-ai-assistant-chat` / `07-08-text2api-web-flow` 实现任务确认为准。若最终改名，需同步更新本说明。

## 2. 文件清单

| 文件 | 类型 | 用途 | 主要消费者 |
|------|------|------|-----------|
| `README.md` | 人读 | 本目录用途、环境变量约定、文件清单、消费方式 | 人 / 所有消费者 |
| `retrieval-sources.json` | 机器 | 项目问答检索配置：文档优先源、源码兜底白名单、忽略规则、引用展示规则 | `ai-assistant-chat` |
| `template-index.json` | 机器 | XML 模板索引：16 个模板的 id/场景/适用范围/选择信号/查询类型/确认项 | `text2api-web-flow` / `dsp-text2api` |
| `import-json-example.json` | 机器 | 兼容 `ConfigImportExportController` 的导入 JSON 样例（SQL 简单查询） | `text2api-web-flow` / `dsp-text2api` |
| `correction-memory.md` | 人读 | Web 与 skills 的纠错沉淀规则、记忆字段定义 | `text2api-web-flow` / `dsp-text2sql` / `dsp-text2api` |

## 3. template/ 是 XML DSL 模板唯一事实来源

- `template/*.xml` 是 **XML DSL 模板的唯一事实来源**，本目录**不复制**任何 XML 模板文件，只在 `template-index.json` 中通过 `file` 字段**引用**模板路径。
- 这样做的目的：避免模板双份维护、避免样例与真实模板漂移。任何对模板内容的修改只发生在 `template/`。
- XML 生成**必须模板驱动**：先根据接口输入输出、SQL 数量、SQL 依赖关系、分页/动态条件/多结果集等特征从 `template-index.json` 选择模板，再填充。
- **AI 不允许自由编写 XML**；生成结果必须可追溯到 `template/` 中的具体模板文件。
- 若 `template/` 现有 16 个模板无法覆盖需求，系统应**提示不支持或需要人工扩展模板**，而不是生成偏离 DSL 约定的 XML。
- Text2API 一期只针对 SQL 类接口，但 `template-index.json` 完整记录了全部 16 个模板的能力，便于未来扩展。

## 4. 导入 JSON 字段语义（inputSchema / outputSchema）

`import-json-example.json` 必须兼容 `ConfigImportExportController.importConfig` 的解析方式。该 Controller 按 key 读取 `interfaceInfo` / `schema` / `template` / `changeLog`，**字段语义需要特别注意**：

- **`schema.inputSchema` 是 XML 配置字符串**，不是 JSON Schema。导入时会被 `sqlSecurityValidator.validateXmlConfig(inputSchema)` 当作 XML 做 SQL 只读安全校验。
- **`schema.outputSchema` 是 JSON Schema 字符串**，用于描述响应结构。
- **简单 SQL 场景下，`schema.inputSchema` 与 `template.xmlContent` 可以同源**（本样例即如此）。但**后续 Text2API 实现必须明确：最终引擎执行以 `template.xmlContent` 为准**，`inputSchema` 仅作为 Schema 版本记录与导入期安全校验输入，不作为运行时执行模板。
- **不要混用旧的 `generateXmlFromSchema()` 语义**：项目里旧的 `generateXmlFromSchema()` 逻辑曾把 `inputSchema` 当 JSON Schema 解析，这与当前 `importConfig` 流程语义冲突。后续 Text2API 实现不应沿用这个旧语义。

> 字段语义的代码依据见 `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/ConfigImportExportController.java` 的 `importConfig` 方法。

## 5. 后续 chat / Text2API / skills 如何消费这些资产

### chat（普通项目问答）

1. 读取 `retrieval-sources.json` 获取文档优先检索源和源码兜底白名单。
2. 判断问题是否属于本项目相关：属于则优先检索文档，文档不足时从源码兜底检索，然后调 AI 生成回答；不属于则直接调 AI 回答。
3. 回答展示引用来源：文档展示路径/标题；源码展示文件路径、类名或方法名，不默认展示大段源码。

### Text2API（Web）

1. 按阶段流转：需求文本 → 接口输入输出草稿 → Text2SQL（需表结构依据） → 模板选择确认 → 填充 XML + 生成导入 JSON → 用户最终“导入并发布”。
2. 模板选择阶段读取 `template-index.json`，向用户展示模板文件、适用场景、选择理由、填充点和重点复核项，获得用户确认后才生成 XML。
3. 最终“导入并发布”复用现有 `/dsp/admin/config/import` 入口，导入 JSON 结构对照 `import-json-example.json`。
4. 用户纠错按 `correction-memory.md` 规则沉淀到草稿修正记录/学习记录，不自动写入 RAG 或项目知识库。

### skills（dsp-*）

- `dsp-text2api` skill 可引用同一份 `template-index.json` 选择规则和 `import-json-example.json` 格式，但运行时读取自身 `.agents/skills/dsp-text2api/` 相对目录。
- Web 资产与 skills 资产可引用同一来源模板，但运行时目录职责分开：Web 读 `DSP_AI_ASSETS_PATH`，skills 读自身相对目录。

## 6. 维护提示

- 新增/修改 `template/*.xml` 时，必须同步更新 `template-index.json`，保持索引与模板一致。
- 新增项目知识文档时，按需追加到 `retrieval-sources.json` 的文档优先检索源。
- 本目录文件保持中文说明；JSON 字段名使用英文以利于机器读取。
- 不要在本目录放置密钥、环境配置或构建产物。
