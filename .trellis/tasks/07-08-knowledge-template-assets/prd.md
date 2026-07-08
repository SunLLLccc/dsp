# 知识与模板资产

## Goal

整理智能助手与 Text2API 所需的本地知识、模板、导入 JSON 样例和纠错记忆规则，为 chat 检索、模板驱动 XML 生成和 skills 工具链提供稳定资产。

## Requirements

- 本任务是智能助手与 Text2API 拆分后的第一个实施子任务，先于 chat、Text2API Web 和 agent skills 落地。
- 项目问答一期文档检索数据源包括 `docs/project-knowledge/`、`docs/xml-dsl-reference.md`、`docs/engine-architecture.md`、`template/README.md` 和 `template/*.xml`。
- 源码兜底检索需要路径白名单和忽略规则，避免检索构建产物、密钥配置、大文件。
- XML 生成必须基于 `template/` 的场景模板选择和填充。
- 需要提供或维护接口管理导入 JSON 样例，供 Web Text2API 和 `dsp-text2api` skill 对照。
- 需要定义纠错记忆/文档的存放规则，支持用户确认后更新 RAG 知识库。
- 若现有模板无法覆盖需求，需要明确提示不支持或需要人工扩展模板。
- Web 侧知识与模板资产放在单独目录中，并通过环境变量配置路径后由应用读取。
- 仓库内需要提供 Web 侧默认样例资产目录 `ai-assets/` 用于开发和测试；生产环境通过环境变量指向部署机外部资产目录。
- `ai-assets/` 不复制 XML 模板文件，只建立索引和选择规则引用现有 `template/` 目录，避免模板双份维护。
- `ai-assets/` 采用 Markdown + JSON 双轨格式：说明性规则用 Markdown，机器读取索引用 JSON。
- 本任务需要直接产出 `ai-assets/` 最小可用默认资产文件，包括 `README.md`、`retrieval-sources.json`、`template-index.json`、`import-json-example.json`、`correction-memory.md`。
- Skills 专用资产放在各自 `.agents/skills/dsp-*/` 目录下。

## Acceptance Criteria

- [ ] 本任务产出的知识、模板、导入 JSON 和记忆规则可作为后续 chat、Text2API Web 和 agent skills 的共同标准。
- [ ] 本地文档检索范围和源码兜底白名单/忽略规则明确。
- [ ] XML 模板目录和模板场景说明可被 Text2API/skills 使用。
- [ ] 存在兼容现有配置导入导出格式的导入 JSON 样例或模板。
- [ ] 纠错记忆/文档目录和更新规则明确。
- [ ] 模板覆盖不到的场景有明确处理规则。
- [ ] Web 侧资产目录通过环境变量配置，不硬编码到业务代码。
- [ ] 仓库内默认样例资产可支持本地开发；生产可通过环境变量切换到外部目录。
- [ ] `ai-assets/` 中的 XML 模板资产通过索引引用 `template/`，不复制模板内容。
- [ ] `ai-assets/` 同时包含人可读 Markdown 说明和机器可读 JSON 索引。
- [ ] 仓库内真实存在最小可用默认资产文件：`ai-assets/README.md`、`ai-assets/retrieval-sources.json`、`ai-assets/template-index.json`、`ai-assets/import-json-example.json`、`ai-assets/correction-memory.md`。
- [ ] Skills 资产位于各自项目级 skill 目录内。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
