# 纠错记忆规则（correction-memory）

本文件定义 Web 侧 Text2API 与 agent skills 的纠错沉淀规则、记忆字段格式和知识库更新策略。**Web 与 skills 的沉淀位置不同**，但都遵循「用户纠错必须沉淀、不自动污染知识库」的统一原则。

## 1. 统一原则

1. **必须沉淀**：用户指出错误时，原始错误、用户纠正、AI 修改结果和提炼出的可复用经验必须被记录，不得静默丢弃。
2. **不自动写知识库**：未经用户确认，纠错内容**不写入** RAG 知识库或项目知识库（`docs/project-knowledge/`），避免会话内容污染知识。
3. **是否更新知识库由用户确认**：只有用户明确同意后，才把某条经验提升为知识库条目。
4. **可追溯**：每条纠错记录需能关联到对应阶段、会话/草稿或 skill 产出。

## 2. Web Text2API 沉淀规则

- Web Text2API 中用户纠错**只进入草稿的「修正记录 / 学习记录」**，随草稿持久化，归属到当前用户。
- Web 一期**不输出独立记忆文件**，也不写入 RAG。
- 草稿字段建议（持久化模型由 `07-08-text2api-web-flow` 实现）：
  - `correctionRecords`：修正记录列表，元素结构见第 4 节。
  - `learningNotes`：从多次纠错中提炼的可复用经验摘要。
- 草稿逻辑删除遵循项目 `deleted` 字段风格，删除草稿后纠错记录一并隐藏。

## 3. Agent skills 沉淀规则

- `dsp-text2sql` 和 `dsp-text2api` 的纠错**需要强制输出文档和记忆文件**，存放在各自 `.agents/skills/dsp-*/` 目录下（例如 `.agents/skills/dsp-text2api/memory/`、`.agents/skills/dsp-text2sql/memory/`）。
- skills 的记忆文件用于跨会话、跨工具（Codex / Claude Code 等）交接。
- 是否把 skill 记忆提升为 RAG 知识库内容由**用户确认**，不由 skill 自动写入。
- 每个 skill 完成后提示下一步建议调用的 skill，但记忆与文档不跳过用户确认直接流转。

## 4. 建议记录字段

每条纠错记录建议包含以下字段（Web 草稿与 skills 记忆文件可共用同一结构）：

| 字段 | 说明 | 示例 |
|------|------|------|
| `时间` (timestamp) | 纠错发生时间（ISO-8601） | `2026-07-08T14:30:00+08:00` |
| `阶段` (stage) | 所属 Text2API 阶段 | `接口定义` / `Text2SQL` / `模板选择` / `XML生成` / `导入JSON` |
| `原始错误` (originalError) | AI 最初产出的问题内容 | SQL 误用了 `LEFT JOIN`、字段映射遗漏、模板选错 |
| `用户纠正` (userCorrection) | 用户给出的正确意图或修正 | 应改为 `INNER JOIN`、补充 `status` 字段映射 |
| `AI修改结果` (aiFixResult) | AI 根据纠正重新生成的内容摘要 | 改写后的 SQL / 调整后的 resultMap / 重新选择的模板 |
| `可复用经验` (reusableLesson) | 提炼出的、可迁移到其他接口的经验 | 「分页列表查询默认应使用模板 04 而非 01」 |
| `是否建议进入知识库` (suggestToKb) | 是否建议提升为知识库条目（true/false） | `true`，附简短理由 |

> 字段名在 Web 草稿持久化时可映射为后端实体属性；在 skills 记忆文件中可作为 Markdown / YAML / JSON 字段。

## 5. 知识库更新流程

1. 用户在 Web 草稿或 skill 产出中标记某条经验「建议进入知识库」。
2. 系统/skill 汇总候选条目，向用户展示并请求确认。
3. 用户确认后，由人工或专用流程写入对应知识库位置（`docs/project-knowledge/` 或 RAG 向量库）。
4. 未确认的条目保留在草稿/skill 记忆中，不进入知识库。

## 6. 边界与红线

- **不自动更新 RAG 知识库**：本规则明确，本任务不实现任何 RAG 写入逻辑，后续接入需独立流程和用户确认。
- **敏感信息**：纠错记录中不得包含生产环境密钥、连接串、密码等敏感信息；如用户输入了敏感内容，沉淀时需脱敏。
- **Web 与 skills 职责分开**：Web 读 `DSP_AI_ASSETS_PATH`，skills 读自身相对目录；纠错沉淀位置也分开，但结构可对齐便于后续对齐。
