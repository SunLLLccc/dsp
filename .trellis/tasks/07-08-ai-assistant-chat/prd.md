# 智能助手 Chat 能力

## Goal

实现管理后台智能助手的普通项目问答能力，让已登录用户可以围绕 DSP 项目提问，并获得带引用来源的流式回答。

## Requirements

- 后端能力归属 `dsp-admin-service`，不新建独立 AI 服务。
- AI 框架使用 AgentScope Java 2.0，但业务层通过项目自有 AI 适配层调用。
- 项目相关问题优先检索本地文档；文档不足时允许从源码兜底检索。
- 非项目相关问题直接由 AI 回答。
- 回答必须展示引用来源：文档展示路径/标题，源码展示文件路径、类名或方法名。
- 支持 SSE 流式输出，覆盖生成中、完成、失败和取消状态。
- 普通 chat 会话与消息需要服务端持久化，用户可手动逻辑删除自己的会话。
- 所有已登录管理端用户可使用普通项目问答。

## Acceptance Criteria

- [ ] 用户可以在管理后台发起项目问答并接收 SSE 流式回答。
- [ ] 项目相关回答优先基于本地文档，必要时从源码兜底检索。
- [ ] 回答展示引用来源，不默认展开大段源码。
- [ ] 非项目相关问题可以直接 AI 回复。
- [ ] 用户刷新页面后可以恢复自己的 chat 历史。
- [ ] 用户可以手动逻辑删除自己的 chat 会话。
- [ ] 后端业务代码通过项目自有 AI 适配层接入 AgentScope Java 2.0。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
