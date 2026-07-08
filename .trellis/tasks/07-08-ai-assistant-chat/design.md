# 智能助手 Chat 能力 — 技术设计

> 本子任务范围：仅实现普通项目问答（chat），不实现 Text2API Web 流程，不实现 `dsp-*` skills，不实现 RAG 知识库写入。
> 复用 `ai-assets/retrieval-sources.json` 作为检索范围配置；本任务不修改该文件。

## 1. 范围与边界

| 范畴 | 说明 |
|------|------|
| 后端归属 | `dsp-admin-service`，不新建 `dsp-ai-service` |
| AI 框架 | AgentScope Java 2.0（作为内部实现），业务层不直接依赖其 API |
| 前端 | `dsp-admin-web` 新增智能助手 `chat` 工作区 |
| 检索策略 | 本地文档优先 → 源码兜底 → 非项目问题直答 |
| 流式协议 | SSE，后期保留迁移 WebSocket 空间 |
| 持久化 | chat 会话 + 消息列表，服务端持久化，逻辑删除 |
| 权限 | 所有已登录管理端用户可用 |
| 不做 | Text2API Web、dsp-* skills、RAG 写入、向量库 |

## 2. 模块边界与目录结构

新增能力集中在 `dsp-admin-service` 内的 `assistant` 子包，业务层通过自有适配层 `AssistantService` / `AiGateway` 调用 AgentScope Java 2.0，Controller 和领域服务不直接使用框架 API。

```
dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/
├── assistant/
│   ├── controller/
│   │   └── AssistantChatController.java        // chat HTTP + SSE 端点
│   ├── service/
│   │   ├── AssistantService.java               // 业务编排（对外契约）
│   │   ├── ChatSessionService.java             // 会话/消息持久化
│   │   └── RetrievalService.java               // 文档优先+源码兜底检索
│   ├── gateway/
│   │   ├── AiGateway.java                      // 项目自有 AI 适配层接口
│   │   └── agentscope/
│   │       └── AgentScopeAiGateway.java        // AgentScope Java 2.0 实现
│   ├── retrieval/
│   │   ├── AssetSourceLoader.java              // 读取 DSP_AI_ASSETS_PATH 下 retrieval-sources.json
│   │   ├── DocRetriever.java                   // 文档检索
│   │   └── SourceCodeRetriever.java            // 源码兜底检索（遵守白名单/忽略规则）
│   ├── sse/
│   │   └── ChatSseEmitter.java                 // SSE 事件封装
│   └── config/
│       └── AssistantProperties.java            // 资产路径、模型、超时配置
```

> 实体、mapper、service 接口放 `dsp-core`（遵循现有分层：`dsp-common ← dsp-core ← dsp-admin-service`），见第 4 节。

### 为什么需要 AiGateway 适配层

- 业务 Controller 和 `AssistantService` 只依赖 `AiGateway` 接口，不 import AgentScope 的类。
- 模型供应商、API Key、超时、重试、降级、流式回调的封装集中在 `AgentScopeAiGateway`。
- 后续若替换框架或新增供应商，只改 `gateway/agentscope/` 下的实现。

## 3. HTTP / SSE 接口设计

所有接口挂在 `/dsp/admin/assistant/chat/**`，经现有 `AdminAuthInterceptor` 鉴权（默认所有登录用户可用，无需 `@RequireRole`）。从 request attribute 取 `adminUserId` 作为会话归属。

| 方法 | 路径 | 用途 | 返回 |
|------|------|------|------|
| POST | `/dsp/admin/assistant/chat/sessions` | 新建会话 | `ApiResponse<ChatSessionVO>` |
| GET | `/dsp/admin/assistant/chat/sessions` | 我的会话列表（分页） | `ApiResponse<Page<ChatSessionVO>>` |
| GET | `/dsp/admin/assistant/chat/sessions/{sessionId}/messages` | 会话消息历史 | `ApiResponse<List<ChatMessageVO>>` |
| POST | `/dsp/admin/assistant/chat/sessions/{sessionId}/ask` | 提问并接收 SSE 流式回答 | `SseEmitter`（text/event-stream） |
| DELETE | `/dsp/admin/assistant/chat/sessions/{sessionId}` | 逻辑删除会话（仅本人） | `ApiResponse<Void>` |

### SSE 事件协议

`ask` 接口返回 `SseEmitter`，事件类型如下：

| event | data 结构 | 说明 |
|-------|----------|------|
| `start` | `{messageId, createdAt}` | 开始生成，返回服务端分配的消息 ID |
| `delta` | `{content}` | 增量内容（逐 token 或逐片段） |
| `citations` | `{docs:[{path,title,section}], sources:[{path,className,method}]}` | 引用来源（在正文 delta 流完后再发） |
| `complete` | `{messageId, finishReason}` | 正常完成 |
| `error` | `{code, message}` | 失败 |
| `cancel` | `{messageId}` | 用户取消（前端关闭流 + 后端检测） |

- `citations` 单独事件：正文不默认内嵌大段源码，引用来源在正文流完后集中下发。
- 前端关闭连接视为取消；后端通过 `SseEmitter.onTimeout/onCompletion/onError` 感知并停止生成。
- **前端消费方式定死为 `fetch + ReadableStream`**（不是原生 `EventSource`）：浏览器原生 `EventSource` 只能发 GET、且无法携带自定义 `Admin-Token` header，与管理端鉴权不兼容。前端用 `fetch(POST /ask, {headers:{Admin-Token}})` 拿到 `Response.body`（`ReadableStream`）后逐行解析 `text/event-stream`。

### 请求/响应报文

- 列表/历史/新建沿用项目统一 `ApiResponse<T>`（`ApiResponse.success(transno, traceId, data)`，transno 用 `"CHAT_SESSION"` 等语义常量，与现有 Controller 风格一致）。
- `ask` 因返回流，不走 `ApiResponse` 包装，直接 `SseEmitter`；错误也用 SSE `error` 事件下发。

## 4. 持久化模型

实体放 `dsp-core/entity`，mapper 放 `dsp-core/mapper`，service 放 `dsp-core/service`，遵循现有分层与命名。两张新表，均带 `deleted` 逻辑删除字段（MyBatis-Plus `@TableLogic`，与 `InterfaceInfo` 等一致）。

### 表 `ai_chat_session`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | bigint PK auto | |
| `session_id` | varchar(64) | 业务会话 ID（UUID），唯一索引 |
| `title` | varchar(255) | 会话标题（取首问摘要） |
| `user_id` | bigint | 归属用户（adminUserId） |
| `user_name` | varchar(64) | 冗余展示（adminUser） |
| `created_time` | datetime | |
| `updated_time` | datetime | |
| `deleted` | tinyint default 0 | 逻辑删除 |

### 表 `ai_chat_message`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | bigint PK auto | |
| `session_id` | varchar(64) | 关联会话，普通索引 |
| `role` | varchar(16) | `user` / `assistant` |
| `content` | mediumtext | 消息正文 |
| `citations` | text | 引用来源 JSON（docs+sources），可空 |
| `status` | tinyint | 0=生成中,1=完成,2=失败,3=取消 |
| `created_time` | datetime | |
| `deleted` | tinyint default 0 | 逻辑删除 |

- 草稿式写入：用户提问立即落库；助手回答先建一条 `status=0` 占位，流式完成时更新为 `status=1` 并写 `content`/`citations`，失败/取消相应更新。
- **会话软删语义（定死）**：一期删除会话只逻辑删除 `ai_chat_session`；`ai_chat_message` 保留用于审计，不做同步逻辑删除。所有查询消息的入口必须先校验 session 未删除且属于当前用户（`session.deleted == 0 && session.user_id == 当前 adminUserId`），因此删除后的会话及其消息对用户不可见。

### 数据库脚本位置

- 建表 SQL 追加到 `dsp-core/src/main/resources/db/init.sql`，与现有脚本同处维护。
- 命名前缀 `ai_chat_*` 与业务表区分。

## 5. 检索策略（消费 ai-assets/retrieval-sources.json）

`RetrievalService` 编排「问题分类 → 文档检索 → 源码兜底 → 引用拼装」：

1. **读取检索配置**：`AssetSourceLoader` 读取 `DSP_AI_ASSETS_PATH/retrieval-sources.json`（未配置环境变量时回退到仓库内 `ai-assets/`，仅本地开发）。配置缓存，变更需重启或提供刷新开关（一期可重启生效）。
2. **问题分类**：判断是否项目相关（基于关键词/路径命中，一期可简单规则，不强制 LLM 分类）。
   - 非项目相关 → 直接调 `AiGateway` 生成，不带检索结果，不下发 `citations`。
3. **文档优先检索**：`DocRetriever` 在 `docs.sources` 范围内检索：
   - `docs/project-knowledge/`（注意 06 校验报告待生成，容忍缺失）
   - `docs/xml-dsl-reference.md`、`docs/engine-architecture.md`、`template/README.md`、`template/*.xml`
4. **源码兜底检索**：当文档结果不足以回答具体实现/函数原理/代码路径时，`SourceCodeRetriever` 在 `sourceCodeFallback.whitelist` 内检索，严格遵守 `ignore` 规则：
   - 路径：`target/`、`node_modules/`、`dist/`、`build/`
   - 文件模式：`*.class/*.jar/*.min.js/package-lock.json` 等
   - 敏感配置：`application*.yml`、`*secret*`、`*.key`、`*.env` 等一律排除
   - 大文件：超过 `maxFileSizeKB`（默认 512）跳过
5. **引用拼装**：按 `citationRules` 生成 `citations`。
   - 文档来源：`{path, title, section}`
   - 源码来源：`{path, className, method}`
   - 不默认展示大段源码；仅用户追问时按需引用关键片段。

> 检索实现一期可以是轻量文件/文本检索（关键词+片段截取），不强求向量库。向量库/RAG 留给后续任务，本任务的 `retrieval-sources.json.ragExtension.enabled=false`。

## 6. AI 适配层（AiGateway / AgentScope Java 2.0）

> **AI 框架依赖**：一期使用 `io.agentscope:agentscope-harness:2.0.0-RC5`，以官方 v2 推荐的 `HarnessAgent` 作为 AgentScope 侧入口；`agentscope-core` 作为传递依赖，不作为业务模块直接依赖。

```
AssistantService ──依赖──> AiGateway(接口)
                                └── 实现: AgentScopeAiGateway ──依赖──> AgentScope Java 2.0 API
```

- `AiGateway` 接口方法（一期）：
  - `void streamChat(ChatRequest req, StreamHandler handler)` — 流式问答，回调 `onDelta/onCitations/onComplete/onError`。
  - `boolean isProjectRelated(String question)` — 一期可选，简单规则或交由 `RetrievalService` 判断。
- `AgentScopeAiGateway` 内部基于 `HarnessAgent.streamEvents()` 获取 AgentScope 事件流（Flux），并将 AgentScope 事件适配为项目自有 `StreamHandler` 的 `onDelta/onCitations/onComplete/onError`，再由 SSE 层推给前端。
- 配置项（`AssistantProperties`，`@ConfigurationProperties("dsp.assistant")`）：
  - `assets-path`：对应 `DSP_AI_ASSETS_PATH`，默认 `ai-assets/`
  - `model`、`api-key`、`base-url`、`timeout-ms`、`max-retries`
  - **密钥不硬编码**，`api-key` 走环境变量（如 `${DSP_AI_API_KEY:}`），生产必须配置。
- 业务 Controller/Service 禁止 import `com.alibaba.agentscope.*`（或对应包名），只依赖 `AiGateway`。该约束在 code review 时人工把关。

## 7. 权限

- `/dsp/admin/assistant/chat/**` 受 `AdminAuthInterceptor` 保护，所有登录用户可访问，不加 `@RequireRole`。
- 会话/消息的查询和删除必须校验 `session.user_id == 当前 adminUserId`，防止越权访问他人会话。
- 数据源元数据读取、Text2API 导入发布等高权限操作不在本任务，留待后续子任务。

## 8. 错误处理与日志

- 复用 `GlobalExceptionHandler` 处理同步接口异常（`BusinessException` + `ErrorCode`）。
- SSE 流式接口的错误通过 `error` 事件下发，同时记录日志。
- **日志脱敏**：不输出完整 prompt、API Key、连接串；失败堆栈截断敏感信息。
- `ErrorCode` 按需新增 chat 相关码（如 `5xxx` 区间：AI 调用失败、生成超时、资产文件缺失）。

## 9. 前端边界（dsp-admin-web）

- 新增智能助手页面，`chat` 工作区。
- 调用上述 5 个接口；`ask` 用 `fetch + ReadableStream` 消费 `POST /ask` 返回的 `text/event-stream`（不用原生 `EventSource`，见第 3 节）。
- 展示生成中/完成/失败/取消状态；渲染 `delta` 增量内容；在正文后渲染 `citations`。
- 会话列表、历史恢复、手动删除会话。
- 前端调用桶追加到 `dsp-admin-web/src/api/index.js`，命名 `assistantApi`。

## 10. 与 ai-assets 的关系

- 本任务**只消费** `ai-assets/retrieval-sources.json`，不修改它。
- `ai-assets/template-index.json`、`import-json-example.json`、`correction-memory.md` 是 Text2API/skills 的资产，本任务不读取。
- 资产路径通过 `DSP_AI_ASSETS_PATH` 环境变量注入，业务代码不硬编码仓库相对路径。

## 11. 风险与取舍

- **AgentScope Java 2.0 依赖引入**：需确认其 Maven 坐标与当前项目 Java 21 / Spring Boot 3.5.16 兼容性。当前源码配置（`dsp-parent/pom.xml`：`<java.version>21</java.version>`、Spring Boot parent `3.5.16`、`maven-compiler-plugin` `<release>${java.version}</release>`）已是 Java 21。旧文档（`AGENTS.md` / `CLAUDE.md` / `README.md` / `docs/project-knowledge/*`）中的 Java 8 / Spring Boot 2.7.18 信息已过期，**不能作为兼容性判断依据**。若实际部署环境仍要求 Java 8，需先单独确认部署基线，否则不按 Java 8 设计。**版本风险**：`2.0.0-RC5` 是预发布版本。当前 P0 只验证依赖解析与编译兼容；P2 实现 `AgentScopeAiGateway` 时必须做一次实际调用和 `streamEvents()` 流式事件联调。若 RC API 变化，通过 `AiGateway` 适配层隔离影响。
- **SSE 与 Spring MVC**：`spring-boot-starter-web` 支持 `SseEmitter`，无需引入 WebFlux；但需注意 `SseEmitter` 异步线程与 Tomcat 线程池配置，避免长连接耗尽线程。一期可调大 `spring.mvc.async.request-timeout` 或限制单用户并发会话数。
- **Reactor 与 WebFlux 边界**：AgentScope Java 内部使用 Reactor Mono/Flux，这是框架内部响应式依赖；本项目 Web 层仍采用 Spring MVC + `SseEmitter`，不引入 WebFlux，不改变现有 Web 栈。
- **SSE 前端消费方式**：一期前端统一使用 `fetch + ReadableStream` 消费 `POST /ask` 返回的 `text/event-stream`。原因：浏览器原生 `EventSource` 只能 GET 且无法设置自定义 `Admin-Token` header，而管理端鉴权依赖 `Admin-Token`。不把原生 `EventSource` 作为同等可选方案。
- **检索质量**：一期轻量检索可能覆盖不全，但符合「文档优先、源码兜底」最低可用目标；向量库/RAG 留后续。
- **会话隔离**：越权防护必须在 Service 层做（拦截器只做登录校验）。

## 12. 验收映射

见 `prd.md` 的 Acceptance Criteria，每条在本设计中均有对应：
- SSE 流式回答 ↔ 第 3 节接口 + 第 8 节状态
- 文档优先/源码兜底 ↔ 第 5 节
- 引用来源 ↔ 第 5 节引用拼装 + SSE `citations` 事件
- 非项目直答 ↔ 第 5 节问题分类
- 历史恢复 ↔ 第 4 节持久化 + 会话列表/历史接口
- 手动逻辑删除 ↔ 第 4 节 `deleted` + 第 7 节越权校验
- 自有适配层 ↔ 第 6 节 AiGateway
