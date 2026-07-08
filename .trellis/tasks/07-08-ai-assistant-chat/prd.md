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

- [x] 用户可以在管理后台发起项目问答并接收 SSE 流式回答。
- [x] 项目相关回答优先基于本地文档，必要时从源码兜底检索。
- [x] 回答展示引用来源，不默认展开大段源码。
- [x] 非项目相关问题可以直接 AI 回复。
- [x] 用户刷新页面后可以恢复自己的 chat 历史。
- [x] 用户可以手动逻辑删除自己的 chat 会话。
- [x] 后端业务代码通过项目自有 AI 适配层接入 AgentScope Java 2.0。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.

---

## 完成状态（2026-07-09）

**状态：已完成，通过真实联调验收。**

### 提交记录

| 阶段 | commit | 说明 |
|------|--------|------|
| P1 持久化层 | `346ccb8` | ai_chat_session / ai_chat_message 实体/Mapper/Service + 建表 SQL |
| P2 AI 适配层 | `b9c2682` | AiGateway / StreamHandle / AgentScopeAiGateway（HarnessAgent + streamEvents） |
| P3 检索层 | `80e0b15` | AssetSourceLoader / DocRetriever / SourceCodeRetriever / RetrievalService |
| P4-A 后端编排+SSE | `5819fe9` | AssistantChatService / ChatSseEmitter / Controller / 并发限制 |
| P4-B 前端 chat | `39d8126` | Chat.vue / sseParser / assistant API/store / CitationsView |
| 联调修复 | `562df1d` | 环境变量命名对齐 + SystemMessage 注入修复 |

### 真实联调结论（DeepSeek deepseek-v4-pro，OpenAI 兼容端点）

6 场景验证：
- 项目相关问题：✅ start → citations → delta → complete，消息落库 status=1
- 源码兜底：✅ CurrentUserResolver 问题可回答并带 citations
- 非项目问题：✅ context 为空仍可正常流式回答
- 取消：⚠️ 基本通过，并发释放有延迟（见已知限制）
- 历史恢复：✅ 重新查询会话消息全恢复
- 删除会话：✅ 逻辑删除后返回 403，不可见

### 已知限制

- **取消即时性**：客户端断开后，SseEmitter 的 onCompletion/onError 回调触发有延迟（Spring MVC 已知特性），并发配额释放不是即时的。一期接受。后续如需更强取消即时性，可考虑显式 cancel endpoint、WebSocket 或后端主动任务状态管理。
- **AgentScope 2.0 SystemMessage 约束**：框架禁止在 inputMessages 注入 SYSTEM 消息，systemPrompt + retrievalContext 已改为合并进 UserMessage 前置（见 `AgentScopeAiGateway.toMessages`）。
- **AgentSupplier 每次 get 新建 agent**：不复用，避免有状态 agent 跨会话污染；后续如有性能压力可评估池化。

### 安全提醒

- DeepSeek API Key 已在 shell history 暴露，需轮换（不在任何提交内容中）。
- 历史 commit `09b6320` 存在旧数据库密码，需轮换数据库密码（当前代码已用环境变量占位符，历史清理需单独授权）。
- `.agentscope/`（AgentScope 运行时目录）已加入 `.gitignore`。
