# DSP Agent Skills 工具链

## Goal

创建 DSP 项目级 agent skills 工具链，让 Codex、Claude Code 等 agent 工具可以按一致流程从需求澄清到导入 JSON 生成 DSP 联机接口配置。

## Requirements

- Skills 放在项目内 `.agents/skills/`，不做全局安装。
- 需要创建 `dsp-grill`、`dsp-text2interface`、`dsp-text2sql`、`dsp-text2api`。
- 工具链顺序为 `dsp-grill` -> `dsp-text2interface` -> `dsp-text2sql` -> `dsp-text2api`。
- 每个 skill 完成后自动提示下一步建议调用的 skill，并提供交接文档或上下文路径；不得自动越过用户确认。
- Skills 需要兼容 Codex、Claude Code 等 agent 工具，不依赖单一工具私有能力。
- `dsp-text2interface` 生成接口输入输出，并询问接口名和所属系统；接口名提供 2-3 个建议并支持用户自定义。
- `dsp-text2sql` 必须基于表结构依据生成 SQL；多段 SQL 需要说明依赖关系，无依赖时说明是多结果集。
- `dsp-text2api` 必须基于模板选择和填充生成 XML，并产出兼容现有接口管理导入导出格式的导入 JSON。
- `dsp-text2sql` 和 `dsp-text2api` 中用户指出错误时，需要修改产物并提炼重点信息输出到文档和记忆文件；是否更新 RAG 知识库由用户确认。

## Acceptance Criteria

- [ ] 项目内存在 4 个 `dsp-*` skills，且每个都有清晰 `SKILL.md`。
- [ ] 每个 skill 定义输入、输出、用户确认点和下一步提示。
- [ ] skills 通过 Markdown 文档、模板文件、记忆文件和目录约定完成跨 agent 交接。
- [ ] `dsp-text2api` 生成的导入 JSON 可对照现有配置导入导出格式校验。
- [ ] 用户纠错会沉淀为文档和记忆文件。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
