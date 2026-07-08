# DSP 数据服务平台 知识库

> 这是 DSP 数据服务平台（Data Service Platform）的知识库前门。
> 本目录由 project-mastery 的 `pm-*` 系列 skill 自动生成，覆盖项目概览、技术栈与架构、开发规范、API 索引、构建打包部署等维度。
> 打开这份 README，你应该能在 30 秒内知道：知识库里有哪些文档、每份是干什么的、你要做什么任务时该读哪几份。

## 项目一句话简介

DSP 是一个**面向"零代码接口发布"的全栈数据服务平台**——后端用 XML DSL 描述查询编排（多数据源 + DAG 并行 + SpEL 动态 SQL + 双模分页），前端用 Vue 3 管理后台做接口/数据源/审批/用户的可视化配置，把"写代码发接口"变成"配 XML 发接口"。

要点建立地形认知：
- **构成**：后端 `dsp-parent`（Maven 多模块，6 个子模块）+ 前端 `dsp-admin-web`（Vue 3 + Vite）+ `template/`（16 个 XML DSL 示例）。
- **服务**：3 个独立可部署 Spring Boot 服务——`dsp-data-service`(8080，在线查询/导出)、`dsp-offline-service`(8081，离线导出)、`dsp-admin-service`(8082，管理平台后端)。
- **核心自封装**：`dsp-engine` 模块的 **XmlEngine**——支持 mysql/doris/oracle/postgresql/http/dubbo/mongo 七种查询类型 + DAG 依赖编排，是"零代码接口发布"能力的载体。
- **技术特征**：Spring Boot 2.7.18（Java 1.8）+ MyBatis-Plus + dynamic-datasource 多数据源 + Dubbo + DOM4J/EasyExcel；前端 Vue 3.5 + Element Plus + Pinia + Vite。

判定依据见 `.codebase/scan-result.json`（classifications types: fullstack/monorepo/microservices/backend/frontend，primaryType=fullstack，置信度 confirmed）。

## 知识库文档地图

知识库共 5/6 份文档已生成（06 校验报告待波次 4 生成），编号即推荐阅读顺序：

| 文档 | 何时读 | 一句话定位 |
|------|--------|-----------|
| [01-001-项目概览.md](01-001-项目概览.md) | 当你想快速了解"这是什么项目、多大、入口在哪"时先读；任何任务的起点 | 项目类型/目录结构/规模指标/入口点/顶层技术栈速览/关键命令 |
| [02-001-技术栈与架构.md](02-001-技术栈与架构.md) | 当你想知道"用了哪些框架、怎么分层、数据怎么流"时读；改架构、动 XmlEngine 前必读 | 开源框架清单 + 5 类自封装（XmlEngine 核心）+ 架构模式/数据流/9 种设计模式 + 模块依赖 DAG + 3 个解耦接口 |
| [03-001-开发规范.md](03-001-开发规范.md) | 当你想写代码、改代码、提交 PR 前；想知道"目录怎么放、类怎么命名、异常怎么抛、提交怎么写"时读 | 28 条规范（目录/命名/封装/前端样式/错误处理/测试/Git 提交）+ 5 条红线（数据源密码加密、JWT 密钥、改引擎须手测、分支纪律、行为准则） |
| [04-001-API索引.md](04-001-API索引.md) | 当你想改/加一个 API、排查 API 报错、找调用关系时读 | 93 条 HTTP 路由 + 2 个 SPI 接口 + 15 个前端调用桶 + 1 个定时触发器 + 核心内部 Service 方法采样 + 调用关系总览 |
| [05-001-构建打包部署.md](05-001-构建打包部署.md) | 当你想把项目跑起来、打包、部署、配环境变量时读 | 环境要求/依赖安装/开发启动/构建打包/部署形态/环境变量配置项/CI 流水线（每条带来源与已验证/推断标注） |
| 06-001-校验报告.md — 未生成 | （待生成）当你想知道"KB 抽样校验结果、哪些章节置信度低需复核"时读 | 波次 4 校验尚未执行，待生成 |

## 按任务找文档

> 这是最实用的章节。带着任务来，从这里决定读哪几份。

### 我刚接手 / 想快速了解这个项目
1. 读 [01-001-项目概览.md](01-001-项目概览.md) 的「目录结构概览」「入口点」「顶层技术栈速览」三节，建立地形认知
2. 读 [02-001-技术栈与架构.md](02-001-技术栈与架构.md) 的「三、架构模式 → 整体架构 / 分层结构 / 数据流」与「二、项目自封装框架 → 2.1 XmlEngine」，理解核心引擎与数据走向
3. 跳到下面「我要把项目跑起来」把工程跑起来，再回头看 04 的「调用关系总览」理解请求链路

### 我要把项目跑起来（开发环境）
- 读 [05-001-构建打包部署.md](05-001-构建打包部署.md) 的「一、环境要求」（JDK 1.8 / Node 20 / MySQL 5.7）→「二、依赖安装」→「三、开发启动」三节
- 关键点：后端在 `dsp-parent/` 下 `mvn clean package` 出 3 个 fat-jar（8080/8081/8082 三个服务）；前端在 `dsp-admin-web/` 下 `npm install` + `npm run dev`（dev server 3000，代理 /dsp/admin→8082、/dsp/offline→8081）
- 注意：数据源密码需 AES 加密为 `ENC(base64)`（见 03 红线 1），JWT 密钥生产环境必须换环境变量（见 03 红线 2）

### 我要加 / 改一个功能
1. 先读 [03-001-开发规范.md](03-001-开发规范.md) 的「一、目录规范」「二、命名规范」「三、代码封装规范」「五、错误处理」，确认放在哪个模块/哪层、类怎么命名、统一报文与异常怎么用
2. 读 [02-001-技术栈与架构.md](02-001-技术栈与架构.md) 的「三、架构模式 → 分层结构」「四、模块依赖关系」，确认分层职责与跨模块依赖方向（dsp-common ← dsp-core ← dsp-engine ← 各 service）
3. 若涉及 API：在 [04-001-API索引.md](04-001-API索引.md) 找现有入口与调用关系；新增 Controller 遵循 admin-service 的分域 Controller 模式
4. 红线：改代码前必先建分支、`main` 禁 push（03 红线 4）；PR ≤400 行 + 1 人 review（03 规范 7-4）

### 我要改 / 排查一个 API
1. 在 [04-001-API索引.md](04-001-API索引.md) 按服务（data/offline/admin）+ Controller 域找对应 HTTP 端点，看 `文件:行`、请求方法、鉴权要求
2. 顺「调用关系总览 → 主干调用链」追下游（Controller → Service → XmlEngine/XmlConfigCacheManager → Executor → 数据源）
3. 报错时看 [03-001-开发规范.md](03-001-开发规范.md) 的「五、错误处理」——统一 `BusinessException` + `ErrorCode` 枚举（0000 成功 / 4xxx 客户端 / 5xxx 服务端），各服务独立 `@RestControllerAdvice` 映射 HTTP 状态
4. **重要校正**（见 04 文档）：Dubbo 在本项目不是服务间 RPC（`@DubboService`/`@DubboReference` 零结果），仅作为 XmlEngine 的「DUBBO 数据源类型」被动态调用；3 个服务间无 RPC 契约，各自独立暴露 HTTP 端点

### 我要部署 / 上生产
1. 读 [05-001-构建打包部署.md](05-001-构建打包部署.md) 的「四、构建/打包」「五、部署方式」「六、环境变量/配置项」三节
2. 部署形态为**裸机直跑 fat-jar + 前端静态托管**（推断，无 Dockerfile/compose/k8s/Makefile）；CI 仅 GitHub Actions 且无自动部署步骤
3. 必须替换的默认值/红线：JWT 密钥（03 红线 2，`${DSP_JWT_SECRET}`）、数据源密码 AES 密钥（03 红线 1，`dsp.security.encrypt-key`）；守护进程方式（nohup/systemd）、JVM 参数、反向代理拓扑均未在配置声明，需自行决定

### 我要改核心引擎（dsp-engine / XmlEngine）
1. 读 [02-001-技术栈与架构.md](02-001-技术栈与架构.md) 的「二、项目自封装框架 → 2.1 XmlEngine」与「四、模块依赖关系 → 解耦接口」（`DataQueryService` / `XmlConfigCacheInvalidator` / `DataSourceRegistrar`）
2. 读 [04-001-API索引.md](04-001-API索引.md) 的「核心内部 API → XmlEngine」「跨模块 SPI 接口」，理解 engine 被谁调用、如何解耦
3. **红线**：改引擎逻辑必须充分手动验证（03 红线 3），因为引擎是"零代码接口发布"的核心，回归面广

## 快速参考

| 速查项 | 值 |
|--------|------|
| 后端服务端口 | data-service 8080 / offline-service 8081 / admin-service 8082 |
| 前端 dev 端口 | 3000（代理 /dsp/admin→8082、/dsp/offline→8081） |
| 后端构建 | `mvn clean package`（在 `dsp-parent/`，产 3 个 fat-jar） |
| 前端构建 | `npm run build`（在 `dsp-admin-web/`，产 `dist/`） |
| 鉴权方式 | JWT（JJWT 0.11.5），LoginController 登录签发；管理端接口经 JwtAuthAspect + `@RequireRole` 校验 |
| 统一报文 | `ApiResponse<T>` + `ErrorCode` 枚举（0000/4xxx/5xxx） |
| 错误处理 | `BusinessException` + 各服务 `@RestControllerAdvice`，HTTP 状态映射 401/403/400/200 |
| 核心引擎 | XmlEngine（dsp-engine）—— XML DSL 查询编排，7 种数据源类型 + DAG 并行 |
| 解耦接口 | `DataQueryService` / `XmlConfigCacheInvalidator` / `DataSourceRegistrar`（engine 对外契约） |

## 元信息

| 字段 | 值 | 来源 |
|------|------|------|
| 项目名称 | DSP 数据服务平台 (Data Service Platform) | `.codebase/scan-result.json` 的 `project.name` |
| KB 生成时间（起始扫描） | 2026-06-14T16:59:25Z | `.codebase/scan-result.json` 的 `generated_at`（pm-scan 时间戳，代表 KB 生成起始时刻；等同于已退役 `_meta/project-type.json` 的 `scannedAt`） |
| skill 版本 | pm-scan v2（其余 pm-techstack-generic / pm-conventions / pm-api-index / pm-build-deploy / pm-kb-index / pm-verify 为 v1） | `.codebase/scan-result.json` 的 `scanner.version`；其余见收尾 `_meta/manifest.json` |
| 项目类型 | primaryType=fullstack，allTypes=[fullstack, monorepo, microservices, backend, frontend]，置信度=confirmed | `.codebase/scan-result.json` 的 `classifications` |
| 源码版本/commit | 733a0e6 | `git rev-parse --short HEAD`（`_meta/manifest.json` 尚未生成，收尾步骤写入） |
| KB 文档完成状态 | 已生成 5/6，06-001-校验报告待生成（波次 4 未跑） | `ls` 实际结果 |

> `_meta/manifest.json` 未生成（收尾步骤尚未执行），源码 commit 已从 git 直接读取；完整 manifest（含全部 skill 版本、verificationStatus）将在收尾写入。本知识库不再使用已退役的 `_meta/project-type.json`——项目类型判定改由 `.codebase/scan-result.json` 的 `classifications` 提供。
> 完成度缺口：06-001-校验报告.md 待生成（波次 4 校验尚未执行）。

## 与项目其他文档的关系

本知识库（`docs/project-knowledge/`）由 project-mastery 自动生成，**引用而不重复**项目根目录的人工文档：

- `README.md`（根目录）：项目主文档（技术栈/结构/命令/端点/表/错误码）——本 KB 在 01/02/04 中引用其内容并标注证据位置。
- `CLAUDE.md` / `AGENTS.md`：Agent 上下文说明与行为准则——03 的 Git 提交规范与红线引用其中显式约定。
- `CONTRIBUTING.md`：分支/commit/SemVer 协作规范——03 规范 7-1 ~ 7-4、05 的「人工协作规范」均引用。
- `docs/dsp-engine-flow.md` / `docs/dsp-engine-flow-v2.md`：XmlEngine 引擎流程设计文档——02 的 XmlEngine 章节与 04 的调用链可作为其代码侧印证。
- `template/*.xml`（16 个）：XML DSL 配置模板示例——理解 XmlEngine 用法时配合阅读。
- `TODO.md`：路线图。
