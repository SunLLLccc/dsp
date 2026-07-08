# 知识与模板资产设计草案

## Scope

本任务先整理资产与规则，不实现智能助手 UI、AI 调用、SSE、Text2API 状态机或 agent skills。

## Outputs

- Web 侧本地知识检索范围与优先级，存放在独立资产目录中。
- Web 侧源码兜底检索白名单和忽略规则，存放在独立资产目录中。
- Web 侧 XML 模板场景说明、模板选择规则和导入 JSON 样例，存放在独立资产目录中。
- Skills 专用模板、记忆文件和交接文件，存放在各自 `.agents/skills/dsp-*/` 目录下。
- Web 修正记录与 skills 纠错记忆/文档的目录和更新规则。
- 本任务直接创建 `ai-assets/` 最小默认资产文件，供后续子任务直接消费。

## Asset Locations

- Web 侧资产目录通过环境变量配置，业务代码只读取配置路径，不硬编码仓库路径。
- 环境变量名称在实现任务中最终确定，规划建议使用类似 `DSP_AI_ASSETS_PATH` 的单一入口。
- 仓库根目录 `ai-assets/` 作为默认样例资产目录，便于本地开发、测试和版本审查。
- 生产环境通过环境变量指向部署机外部资产目录，便于热更新、挂载和权限管理。
- `ai-assets/` 不复制 `template/*.xml`；它保存模板索引、选择规则和引用路径，`template/` 仍是 XML DSL 模板事实来源。
- `ai-assets/` 采用 Markdown + JSON 双轨：Markdown 保存目录说明、更新规则、纠错记忆规则等人读内容；JSON 保存检索源、模板索引、导入 JSON 样例等机器可读内容。
- 最小默认资产文件包括 `README.md`、`retrieval-sources.json`、`template-index.json`、`import-json-example.json`、`correction-memory.md`。
- Skills 资产跟随项目级 skill 存放，例如 `.agents/skills/dsp-text2api/templates/`、`.agents/skills/dsp-text2api/memory/`。
- Web 侧资产与 skills 资产可以引用同一份来源模板，但运行时目录职责分开：Web 读取环境变量路径，skills 读取自身相对目录。

## Consumers

- `07-08-ai-assistant-chat` 使用本任务产出的本地知识范围、源码兜底规则和引用来源规则。
- `07-08-text2api-web-flow` 使用本任务产出的模板选择规则、导入 JSON 样例和纠错记录规则。
- `07-08-dsp-agent-skills` 使用本任务产出的模板、样例、记忆规则和跨 agent 文档约定。
