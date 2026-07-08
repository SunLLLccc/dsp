# 智能助手与 Text2API 能力规划实施拆分

## Order

1. 完成 `07-08-knowledge-template-assets`，确认本地知识范围、源码兜底规则、XML 模板说明、导入 JSON 样例和纠错记忆规则。
2. 完成 `07-08-ai-assistant-chat`，建立 AgentScope Java 2.0 适配层、SSE 流式问答、引用来源和 chat 持久化。
3. 完成 `07-08-text2api-web-flow`，实现需求输入、草稿状态机、Text2SQL、元数据读取、模板确认、XML/导入 JSON 和最终导入发布。
4. 完成 `07-08-dsp-agent-skills`，让本地 agent 工具链复用同一套模板、格式和纠错沉淀规则。
5. 做父任务集成复核，确认四个子任务在术语、权限、导入 JSON 格式、模板选择和知识更新规则上保持一致。

## Validation

- 每个子任务进入实现前都需要补齐自身 `design.md` 和 `implement.md`。
- Web 子任务需要后端编译、前端构建和关键流程手测。
- Skills 子任务需要至少用一个 SQL 类接口样例跑通 `dsp-grill` -> `dsp-text2interface` -> `dsp-text2sql` -> `dsp-text2api` 的文档产物链路。
- 父任务最终验收时检查所有子任务 PRD 验收项是否覆盖本任务要求。

## Rollback Points

- AgentScope Java 2.0 接入应被封装在项目自有 AI 适配层内，必要时可以替换实现而不重写业务流程。
- SSE 协议集中封装，后续迁移 WebSocket 时避免扩散到业务状态机。
- Text2API 最终发布复用现有导入格式，导入发布前的所有中间步骤都只写草稿，避免半成品接口生效。
