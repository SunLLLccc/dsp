# DSP

DSP 是面向企业查询型数据接口的 AI 辅助设计、受控生成与全生命周期治理平台。

它的核心价值不是把接口逻辑简单改写成 XML 配置，而是把自然语言数据需求转化为有表结构依据、受模板约束、可验证、可审批、可发布和可审计的数据接口。AI 负责需求理解和候选内容生成，平台通过 Schema Evidence、模板、版本、审批、授权、审计和运行缓存把生成结果纳入工程治理。

## 产品定位

DSP 聚焦企业内部常见的查询型、数据聚合型接口建设场景：业务方提出数据需求，平台辅助生成接口定义、SQL、XML/JSON 配置，并在关键阶段由人确认、修改、回退和审批。XML DSL 与 DAG 查询编排是接口执行底座。

核心链路：

```
业务需求 → 接口定义 → 基于 Schema Evidence 的 Text2SQL → 模板选择
→ XML/JSON 配置生成 → 验证 → 审批/导入发布 → 授权、审计与运行治理
```

## 适用场景

- 企业内部报表、列表、详情、聚合查询等读接口的设计与发布。
- 需要基于多数据源查询、分页、动态条件、结果映射或多段查询编排的接口。
- 需要把 AI 生成纳入人工确认、版本管理、审批发布、应用授权和审计留痕的场景。
- 需要让接口配置、调试、导入导出、上线下线和运行缓存统一管理的场景。

## 不适用场景

- 复杂交易、写操作、强一致业务流程或需要大量领域代码的后端接口。
- 不具备表结构或字段说明依据，却希望直接生成可上线 SQL 的场景。
- 希望 AI 自由生成任意 XML、任意 SQL 或绕过审批治理直接上线的场景。
- 通用代码生成器、通用工作流平台、全类型后端接口开发平台。

## 设计原则

1. AI 负责需求理解和候选内容生成，不直接代表最终工程事实。
2. Schema Evidence 约束表和字段，减少 Text2SQL 幻觉。
3. 平台模板约束 XML 结构，AI 不自由生成任意配置。
4. 人在接口定义、SQL、模板、XML/JSON 和发布等关键阶段确认、修改、回退或审批。
5. 平台负责版本、授权、审计、发布、缓存失效和运行治理。
6. 明确聚焦查询型、数据聚合型接口，不宣称覆盖所有后端接口。
7. XML DSL 与 DAG 查询编排是执行底座，服务于受控生成和治理闭环。
8. 当前能力与未来规划分开描述，避免把未实现能力写成已交付能力。

更系统的产品边界与指标说明见 [docs/product-positioning.md](docs/product-positioning.md)。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21 + Spring Boot 3.5.16 + MyBatis-Plus + Dynamic-DS + Druid + DOM4J + EasyExcel + Dubbo 3.2 + MongoDB + JWT |
| 前端 | Vue 3.5 + Element Plus + Pinia + Vue Router 4 + Vite 8 + Axios |
| 包名 | com.sunlc.dsp |

## 项目结构

```
dsp/
├── dsp-parent/               # Maven 父POM
│   ├── dsp-common/           # 公共模块 (jar)
│   ├── dsp-core/             # 核心代码模块 (jar)
│   ├── dsp-engine/           # 引擎模块 (jar)
│   ├── dsp-data-service/     # 数据服务 (可部署 jar, port=8080)
│   ├── dsp-offline-service/  # 离线导出服务 (可部署 jar, port=8081)
│   └── dsp-admin-service/    # 管理平台服务 (可部署 jar, port=8082)
├── dsp-admin-web/            # 前端项目 (Vue 3 + Vite)
├── ai-assets/                # 智能助手 / Text2API 默认资产与索引
└── template/                 # XML 配置模板
```

### 模块依赖关系

```
dsp-common          (无依赖)
dsp-core            → dsp-common
dsp-engine          → dsp-common, dsp-core
dsp-data-service    → dsp-common, dsp-core, dsp-engine
dsp-offline-service → dsp-common, dsp-core, dsp-engine
dsp-admin-service   → dsp-common, dsp-core, dsp-engine
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+
- Node.js 20+
- MySQL 5.7+
- Redis 5.0+
- MongoDB 4.0+（可选，使用 mongo 查询类型时需要）

### 数据库初始化

```bash
mysql -u root -p < dsp-parent/dsp-core/src/main/resources/db/init.sql
```

### 后端

```bash
cd dsp-parent

# 编译整个项目
mvn clean compile

# 编译单个模块
mvn clean compile -pl dsp-common

# 运行测试
mvn test

# 打包
mvn clean package

# 运行数据服务 (port=8080)
java -jar dsp-data-service/target/dsp-data-service-1.0.0.jar

# 运行离线导出服务 (port=8081)
java -jar dsp-offline-service/target/dsp-offline-service-1.0.0.jar

# 运行管理平台服务 (port=8082)
java -jar dsp-admin-service/target/dsp-admin-service-1.0.0.jar
```

### 前端

```bash
cd dsp-admin-web

# 安装依赖
npm install

# 开发模式 (Vite 热更新，代理到 localhost:8082)
npm run dev

# 生产构建
npm run build

# 预览构建结果
npm run preview
```

## 核心特性

- **Text2API 受控生成** — 从需求文本进入阶段式工作区，依次生成接口定义、Text2SQL、模板选择、XML/JSON，并支持确认、回退、发布重试
- **Schema Evidence 门禁** — Text2SQL 必须基于表名与字段依据；无依据时只追问，不生成看似可用的 SQL
- **模板约束的 XML/JSON 生成** — XML 只能基于 `template/` 下模板选择与填充，生成前后经过路径安全和 SQL 安全校验
- **AI 助手** — AgentScope Java 2.0 接入，支持项目问答、SSE 流式输出、本地文档优先检索与源码兜底
- **版本、审批与发布治理** — 接口版本支持草稿、提交审批、通过发布、驳回、撤回、下线，发布后触发运行缓存失效
- **应用授权与审计** — AppId/AppSecret、接口级授权 Token、管理端/数据服务审计日志
- **XML DSL 执行底座** — 通过 XML 定义请求、查询、结果映射和响应结构，由运行引擎解释执行
- **DAG 查询编排** — CompletableFuture + 依赖校验 + 循环依赖检测，支持并行/串行/混合查询编排
- **多数据源支持** — MySQL / Doris / PostgreSQL / HTTP / Dubbo / MongoDB
- **动态 SQL** — `<if>` 条件判断、`<foreach>` 集合遍历、`#{}` 参数替换
- **分页查询** — 游标分页（cursor）+ 深分页优化（子查询改写）
- **内置函数库** — 29个内置函数（日期/字符串/类型转换/JSON/空值/条件/聚合/数学）
- **本地缓存** — XML 配置解析结果缓存，5分钟定时刷新，发布/下线即时失效
- **在线/离线导出** — 支持 XLSX / CSV / TXT 多格式导出
- **JWT 鉴权** — 签名验证 + 过期校验 + 白名单 + 防重放
- **管理后台** — 接口管理/数据源管理/审批管理/导出管理/应用授权/审计日志/智能助手/Text2API

## 引擎架构

```
请求 → XmlEngine → 解析XML → 校验参数 → 编排查询 → 结果映射 → 组装响应
                      │           │           │           │
                  XmlConfigParser  │    QueryOrchestrator  ResultMapper
                                  │           │
                            DynamicSqlHandler  ├── SqlExecutor
                            PaginationHandler  ├── HttpExecutor
                                              ├── DubboExecutor
                                              └── MongoExecutor
```

### 缓存架构

```
请求 → DataApiController
          │
          ├── 缓存命中 → XmlConfigCacheManager.get(transno) → InterfaceConfig
          │                                                    ↓
          │                                          XmlEngine.executeWithConfig()
          │
          └── 缓存未命中 → 自动从DB加载XML → 解析为InterfaceConfig → 缓存

定时刷新：CacheRefreshScheduler (5分钟) → CacheLoadStrategy.loadActiveTransnos() → 全量刷新
即时失效：审批通过/下线 → XmlConfigCacheInvalidator.invalidate(transno)
```

### 支持的查询类型

| 类型 | 执行器 | 说明 |
|------|--------|------|
| mysql / doris / sql / oracle / postgresql | SqlExecutor | SQL 查询，Dynamic-DS 数据源切换 |
| http | HttpExecutor | HTTP 外部接口调用（GET/POST） |
| dubbo | DubboExecutor | Dubbo 泛化调用 |
| mongo | MongoExecutor | MongoDB 查询（需引入 spring-boot-starter-data-mongodb） |

### 内置函数一览（29个）

| 分类 | 函数 |
|------|------|
| 日期 | DATE_FORMAT, DATE_ADD, DATE_SUB, WORKDAYS |
| 字符串 | CONCAT, CONCAT_WS, SUBSTRING, TRIM, REPLACE, UPPER, LOWER, LIKE_MATCH, REGEX_MATCH, LENGTH, PAD_LEFT, PAD_RIGHT |
| 空值/条件 | NVL, IFNULL, IFF |
| 类型转换 | TYPE_CONVERT |
| JSON | JSON_EXTRACT |
| 聚合 | SUM, AVG, COUNT, MAX, MIN |
| 数学 | ROUND, CEIL, FLOOR |

## 服务端点

| 服务 | 端口 | 端点 | 说明 |
|------|------|------|------|
| dsp-data-service | 8080 | `POST /dsp/api/{transno}` | 数据查询 |
| dsp-data-service | 8080 | `POST /dsp/api/{transno}/export` | 在线导出 |
| dsp-offline-service | 8081 | `POST /dsp/offline/export` | 提交离线导出任务 |
| dsp-offline-service | 8081 | `GET /dsp/offline/export/{taskId}/progress` | 查询导出进度 |
| dsp-offline-service | 8081 | `GET /dsp/offline/export/{taskId}/download` | 下载导出文件 |
| dsp-admin-service | 8082 | `/dsp/admin/interface/*` | 接口管理 CRUD + 版本 + 审批 + 调试 |
| dsp-admin-service | 8082 | `/dsp/admin/datasource/*` | 数据源管理 CRUD + 动态注册 + 连接测试 |
| dsp-admin-service | 8082 | `/dsp/admin/app/*` | 应用授权 CRUD + Token 签发 |
| dsp-admin-service | 8082 | `/dsp/admin/assistant/chat/*` | 智能助手会话、消息历史、SSE 问答 |
| dsp-admin-service | 8082 | `/dsp/admin/assistant/text2api/*` | Text2API 草稿、阶段生成、确认/回退、导入发布 |

## 前端页面

| 页面 | 路由 | 说明 |
|------|------|------|
| 接口管理 | /interface | 接口列表、搜索、编辑、版本历史查看 |
| 接口编辑 | /interface/edit/:id? | 基础信息 + XML配置 + 数据源关联 |
| 接口调试 | /interface/debug | 接口在线调试 |
| 数据源管理 | /datasource | 数据源列表、搜索分页、新增/编辑/测试/删除 |
| 审批管理 | /approval | 待审批列表、通过/驳回、审批记录查看 |
| 应用授权 | /appauth | 应用管理、Token签发 |
| 导出管理 | /export | 导出任务列表 |
| 审计日志 | /audit | 操作审计日志查询 |
| 智能助手 | /assistant | 项目问答、引用展示、会话历史、逻辑删除 |
| Text2API | /assistant/text2api | 需求到 SQL 类接口发布的 6 阶段工作区 |

## 数据库表结构

| 表名 | 说明 | 对应 Entity |
|------|------|-------------|
| interface_info | 接口基础信息 | InterfaceInfo |
| interface_version | 接口版本（含XML配置） | InterfaceVersion |
| approval_record | 审批记录 | ApprovalRecord |
| datasource_config | 数据源配置 | DatasourceConfig |
| interface_datasource | 接口-数据源关联 | InterfaceDatasource |
| export_task | 导出任务 | ExportTask |
| app_auth | 应用授权 | AppAuth |
| audit_log | 操作审计日志 | AuditLog |
| ai_chat_session | 智能助手会话 | AiChatSession |
| ai_chat_message | 智能助手消息 | AiChatMessage |
| ai_text2api_draft | Text2API 草稿与阶段产物 | AiText2ApiDraft |

## 配置说明

- **MyBatis-Plus** — 逻辑删除字段 `deleted`，自动驼峰命名转换
- **JWT 密钥** — `${DSP_JWT_SECRET:默认值}`，生产环境必须通过环境变量更换
- **数据源密码** — AES 加密存储，`ENC(base64)` 格式，密钥配置在 `dsp.security.encrypt-key`
- **XML缓存** — `dsp.cache.xml.refresh-enabled=true` 启用定时刷新，`refresh-interval` 刷新间隔（毫秒）
- **MongoDB** — `spring.data.mongodb.uri`，需引入 spring-boot-starter-data-mongodb
- **AI 助手** — `dsp.assistant.model` / `dsp.assistant.base-url` / `dsp.assistant.api-key`，API Key 建议通过 `DSP_ASSISTANT_API_KEY` 注入
- **AI 资产目录** — `dsp.assistant.assets-path`，生产建议通过 `DSP_ASSISTANT_ASSETS_PATH` 指向部署机外部目录；默认读取仓库内 `ai-assets/`
- **Text2API 元数据读取** — `dsp.assistant.metadata.timeout-seconds` / `max-tables` / `max-columns` 控制只读元数据扫描上限
- **前端代理** — Vite 将 `/dsp` 请求代理到 `localhost:8082`
- **导出目录** — 离线导出文件存储在 `./export-files`

## 智能助手与 Text2API

### 智能助手

- 后端使用 AgentScope Java 2.0 RC5 作为 AI 适配层，业务层只依赖项目自有 `AiGateway`。
- 问答采用 Spring MVC `SseEmitter` 输出，前端通过 `fetch + ReadableStream` 消费 SSE。
- 项目相关问题优先检索本地文档；文档命中不足时按白名单从源码兜底检索，并返回引用来源。
- 会话与消息持久化在 `ai_chat_session` / `ai_chat_message`，删除会话为逻辑删除，消息保留审计。

### Text2API

Text2API 一期聚焦 SQL 类接口生成，必须基于用户提供的表结构依据生成 SQL，不能在没有表结构/字段依据时生成看似可用的 SQL。

阶段流转：

```
需求文本 → 接口定义 → Text2SQL → 模板选择 → XML/JSON 生成 → 导入发布
```

- Text2SQL 必须有 `SchemaEvidence`。当前前端支持用户在会话中输入表名与字段；后端已具备数据源元数据读取服务，可作为后续入口对接的依据来源。
- XML 生成必须基于 `template/` 下的模板选择与填充，AI 不允许自由编写 XML。
- 导入发布复用 `ConfigImportService.importConfig`，生成的导入 JSON 中 `schema.inputSchema` 与 `template.xmlContent` 一期同源为 XML 配置字符串。
- 发布失败会保留草稿并记录 `publishError`，允许修正后重试发布。

## 业务错误码

| 错误码 | 含义 |
|--------|------|
| 0000 | 成功 |
| 4001 | Token缺失或格式错误 |
| 4002 | Token已过期 |
| 4003 | 无权访问该接口 |
| 4004 | 接口不存在 |
| 4005 | 时间戳超出允许范围 |
| 4100 | 请求参数错误 |
| 4101 | 审批记录不存在 |
| 4102 | 重复提交审批 |
| 4103 | 审批记录已处理 |
| 4104 | 接口版本不存在 |
| 4105 | 版本状态不允许此操作 |
| 4106 | 应用不存在或已禁用 |
| 4107 | 数据源关联已存在 |
| 5001 | 系统内部错误 |
| 5002 | 数据源连接异常 |
| 5003 | 导出失败 |

## 许可证

私有项目，未经授权禁止使用。
