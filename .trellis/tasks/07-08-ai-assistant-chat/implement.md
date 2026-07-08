# 智能助手 Chat 能力 — 实施计划

> 对应 `design.md`。按阶段拆分，每阶段有验证检查点。仅实现 chat，不实现 Text2API / skills / RAG 写入。

## 阶段总览

| 阶段 | 产出 | 验证检查点 |
|------|------|-----------|
| P0 依赖与配置 | pom 依赖、配置类、资产路径 | 编译通过、配置项可注入 |
| P1 持久化层 | 实体/mapper/service、建表 SQL | 单测：会话/消息 CRUD + 逻辑删除 |
| P2 AI 适配层 | AiGateway 接口 + AgentScope 实现 | 单测：mock 框架，验证流式回调 |
| P3 检索层 | 资产加载 + 文档/源码检索 | 单测：白名单/忽略规则生效 |
| P4 业务编排 | AssistantService + SSE 端点 | 集成测试：端到端 SSE 流 |
| P5 权限与边界 | 越权校验、日志脱敏 | 越权访问返回错误、日志无密钥 |
| P6 前端 | chat 工作区 | 手动联调：发起、流式、引用、恢复、删除 |
| P7 收尾 | 文档/脚本对齐 | 全量编译 + 测试通过 |

---

## P0. 依赖与配置

1. `dsp-admin-service/pom.xml` 引入：
   - AgentScope Java 2.0 依赖（`io.agentscope:agentscope-harness:2.0.0-RC5`，**官方 v2 文档推荐的 HarnessAgent 入口，`agentscope-core` 由 harness 传递引入**，不作为业务模块直接依赖；确认 Maven 坐标与当前 Java 21 / Spring Boot 3.5.16 兼容性；当前源码配置已是 Java 21，不按 Java 8 设计。旧文档 `AGENTS.md` / `CLAUDE.md` / `README.md` / `docs/project-knowledge/*` 中的 Java 8 / Spring Boot 2.7.18 信息已过期，**不作为事实依据**，后续需单独刷新）。
   - **P0 阻塞验证（Codex 提醒）**：实际确认 Maven 坐标可用，并执行 `mvn clean compile -pl dsp-admin-service -am` 验证与 Java 21 / Spring Boot 3.5.16 编译兼容，通过后再进入下游阶段。
   - 不引入 WebFlux（用 `SseEmitter`，`spring-boot-starter-web` 已支持）。
2. `AssistantProperties`（`@ConfigurationProperties("dsp.assistant")`）：
   - `assetsPath`（默认 `ai-assets/`，对应 `DSP_AI_ASSETS_PATH`）
   - `model` / `apiKey`（`${DSP_AI_API_KEY:}`）/ `baseUrl` / `timeoutMs` / `maxRetries`
   - `sseTimeoutMs`、`maxConcurrentPerUser`
3. **检查点**：`mvn clean compile -pl dsp-admin-service -am` 通过；`DSP_AI_ASSETS_PATH` 指向外部目录时可被读取。

## P1. 持久化层

1. `dsp-core/entity`：`AiChatSession`、`AiChatMessage`（带 `@TableLogic deleted`，字段见 design 第 4 节）。
2. `dsp-core/mapper`：`AiChatSessionMapper`、`AiChatMessageMapper`（继承 MyBatis-Plus `BaseMapper`）。
3. `dsp-core/service`：`AiChatSessionService`、`AiChatMessageService`（继承 `IService`/`ServiceImpl`）。
4. 建表 SQL 追加到 `dsp-core/src/main/resources/db/init.sql`，表名 `ai_chat_session`、`ai_chat_message`。
5. **检查点**：
   - 单测：新建会话、追加消息、更新消息状态（0→1）、逻辑删除会话、查询本人会话列表。
   - 越权查询返回空（按 `user_id` 过滤）。
   - **会话软删语义**：删除会话只逻辑删除 `ai_chat_session`，`ai_chat_message` 保留用于审计；查询消息入口校验 session 未删除且属于当前用户，删除后不可见。

## P2. AI 适配层

1. `AiGateway` 接口：`streamChat(ChatRequest, StreamHandler)`，`StreamHandler` 定义 `onDelta/onCitations/onComplete/onError`。
2. `AgentScopeAiGateway` 实现：基于 `HarnessAgent.streamEvents()` 获取 Flux 事件流，并适配为项目自有 `StreamHandler`（`onDelta/onCitations/onComplete/onError`），内部处理超时/重试/降级。业务层不直接感知 Reactor、HarnessAgent 或 AgentScope 事件类型。
3. 业务层（`AssistantService` 等）禁止 import AgentScope 类（code review 把关）。
4. **不引入 WebFlux**：Reactor（Mono/Flux）是 AgentScope 内部依赖，Web 层仍使用 Spring MVC `SseEmitter`，前端仍用 `fetch + ReadableStream` 消费 SSE。不改变现有 Web 栈。
5. **检查点**：
   - 单测：mock 框架返回，验证 `onDelta` 多次回调 + `onComplete`；异常路径触发 `onError`。
   - 不真实调用模型（CI 不依赖外部 API）。

## P3. 检索层

1. `AssetSourceLoader`：读取 `assetsPath/retrieval-sources.json`，缓存解析结果。
2. `DocRetriever`：在 `docs.sources` 范围检索（含 `template/*.xml`），容忍 06 校验报告缺失。
3. `SourceCodeRetriever`：在 `sourceCodeFallback.whitelist` 检索，**严格遵守 `ignore` 规则**（路径/文件模式/敏感配置/`maxFileSizeKB`）。
4. `RetrievalService`：问题分类（一期规则）→ 文档优先 → 源码兜底 → 引用拼装。
5. **检查点**：
   - 单测：白名单命中、`target/`/`application.yml` 被忽略、超 `maxFileSizeKB` 跳过。
   - 非项目问题不触发检索。

## P4. 业务编排与 SSE

1. `ChatSessionService`：会话/消息持久化业务方法。
2. `AssistantService`：编排检索 → 拼 prompt → 调 `AiGateway.streamChat` → 落库 → SSE 推送。
3. `AssistantChatController`：5 个端点（建会话/列表/历史/`ask` SSE/删除）。
4. `ChatSseEmitter`：封装 SSE 事件（start/delta/citations/complete/error/cancel），处理 `onTimeout/onCompletion/onError` 停止生成。
5. 消息写入：提问立即落库；助手消息先 `status=0` 占位，完成更新 `status=1`+`content`+`citations`，失败/取消相应更新。
6. **检查点**：
   - 集成测试：`ask` 返回 SSE，依次收到 start→delta*→citations→complete。
   - 前端关闭连接后后端停止生成（取消路径）。

## P5. 权限与边界

1. `/dsp/admin/assistant/chat/**` 走 `AdminAuthInterceptor`，无 `@RequireRole`。
2. Service 层校验 `session.userId == 当前 adminUserId`，越权返回 `BusinessException(ErrorCode.ACCESS_DENIED)`。
3. **`adminUserId` 类型稳健转换（Codex 提醒）**：`adminUserId` 来自 JWT claims，类型可能不是 `Long`（可能是 `String`/`Integer`/`Object`）。从 request attribute 或 claims 取值后必须做稳健转换与空值/格式校验，转换失败按未登录或鉴权异常处理，不要直接强转。
4. 日志脱敏：不输出完整 prompt / API Key / 连接串；异常堆栈截断。
5. `ErrorCode` 新增 chat 相关码（如 `5xxx`：AI 调用失败、生成超时、资产缺失）。
6. **检查点**：用户 A 删除/查看用户 B 的会话返回 403/ACCESS_DENIED；日志中无密钥明文。

## P6. 前端（dsp-admin-web）

1. 智能助手页面 + `chat` 工作区。
2. `dsp-admin-web/src/api/index.js` 新增 `assistantApi`（5 个方法）。
3. `ask` 用 `fetch + ReadableStream` 消费 SSE（**不用原生 `EventSource`**：它只能 GET、无法携带 `Admin-Token`，与管理端鉴权不兼容）。前端用 `fetch(POST /ask, {headers:{Admin-Token}})` 拿 `Response.body` 逐行解析 `text/event-stream`，处理生成中/完成/失败/取消。
4. **SSE 解析健壮性（Codex 提醒）**：按标准 SSE 格式解析，不能假设一个 chunk 就是一条完整事件。需处理：跨 chunk 分片、空行分隔事件、单事件多行 `data:`、`event:`/`data:` 前缀解析、`data:` 后的冒号空格剥离、不完整事件缓冲到下一个 chunk 再拼。建议封装一个状态机式 SSE 解析器，覆盖单元测试。
4. 渲染：`delta` 增量正文 + `citations` 引用列表（文档/源码分开展示）。
5. 会话列表、历史恢复、手动删除（逻辑删除）。
6. **检查点**：`npm run dev` 联调，刷新页面恢复历史，删除会话后不再可见。

## P7. 收尾

1. 更新 `AGENTS.md` / 相关文档（如新增端口说明外的接口列表，按需）。
2. 确认 `init.sql` 含新表；`ai-assets/` 未被修改。
3. **检查点**：`mvn clean compile`（全模块）+ `mvn test` 通过；`npm run build` 通过。

---

## 阶段间依赖与并行

```
P0 ──> P1 ──┐
P0 ──> P2 ──┼──> P4 ──> P5 ──> P7
P0 ──> P3 ──┘            │
                          └──> P6（前端可与 P5 后并行）
```

- P0 是阻塞前置（依赖与配置未定，下游无法开展）。
- P1/P2/P3 在 P0 后可并行。
- P4 依赖 P1/P2/P3。

## 验收对照（Acceptance Criteria → 阶段）

| 验收项 | 实现阶段 |
|--------|---------|
| 发起项目问答并接收 SSE 流式回答 | P2+P4 |
| 项目相关回答优先本地文档、必要时源码兜底 | P3 |
| 回答展示引用来源，不默认展开大段源码 | P3（引用拼装）+P4（citations 事件） |
| 非项目问题直接 AI 回复 | P3（问题分类） |
| 刷新后恢复 chat 历史 | P1+P4（会话/消息历史接口）+P6 |
| 手动逻辑删除 chat 会话 | P1+P5 |
| 业务代码通过自有适配层接入 AgentScope | P2 |

## 开放待确认项（交 Codex 审核 / 产品确认）

1. **AgentScope Java 2.0 的 Maven 坐标与 Java 21 / Spring Boot 3.5.16 兼容性**：需在 P0 阶段确认。当前源码已是 Java 21（`dsp-parent/pom.xml` 事实），不再以 Java 8 设计。若实际部署环境仍要求 Java 8，需先单独确认部署基线。**旧文档刷新**：`AGENTS.md` / `CLAUDE.md` / `README.md` / `docs/project-knowledge/*` 中的 Java 8 / Spring Boot 2.7.18 信息已过期，后续需要单独刷新，不作为本轮兼容性判断依据。
   - **RC5 预发布风险（追加）**：`2.0.0-RC5` 是预发布版本，P0 只验证依赖解析与编译兼容。P2 实现 `AgentScopeAiGateway` 时**必须做实际模型调用和 `streamEvents()` 流式事件联调**，不能只依赖编译通过。若 RC API 变化，通过 `AiGateway` 适配层隔离影响。
2. **问题分类实现方式**：一期用简单规则（关键词/路径命中）还是轻量 LLM 分类。倾向规则，成本低、可控。
3. **检索实现深度**：一期轻量关键词检索是否足够，还是需要最小可用向量检索。本任务倾向轻量，向量库留后续。
