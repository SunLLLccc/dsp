# DSP AI 数据接口平台 学习文档

> 这是 DSP AI 数据接口平台的学习文档前门，由 learn-project 的 `lp-*` 系列 skill 自动生成。
> 打开这份 README，你应该能在 30 秒内知道：这是什么项目、有哪些功能学习文档、该按什么顺序读。

## 项目一句话简介

DSP 是面向企业查询型数据接口的 AI 辅助设计、受控生成与全生命周期治理平台。XML DSL 引擎负责把已确认的接口配置解释为可调用 HTTP 查询接口，Text2API、Schema Evidence、模板约束、审批发布、授权和审计共同构成受控生成与治理闭环。

深入背景见：[01-001-项目概览.md](../project-knowledge/01-001-项目概览.md)、[02-001-技术栈与架构.md](../project-knowledge/02-001-技术栈与架构.md)，产品边界见 [product-positioning.md](../product-positioning.md)。

## 功能学习文档

| 文档 | 何时读 | 一句话定位 |
|------|--------|-----------|
| [docs/01-xml-dsl-engine.md](docs/01-xml-dsl-engine.md) | 当你想搞懂"一份 XML 是怎么变成一个可调用 HTTP 接口的"时**先读这份**；它是理解整个 DSP 引擎层的钥匙 | XmlEngine 四阶段执行流水线（参数校验 → DAG 查询编排 → 结果映射 → 响应构建），是平台执行内核 |
| [docs/02-dag-orchestration.md](docs/02-dag-orchestration.md) | 当你想搞懂"一个接口里多个查询是怎么并行 + 按依赖串起来的"时读这份（依赖 01 的执行内核，需先读 01） | QueryOrchestrator 用 CompletableFuture 把多个 query 按依赖组织成有向无环图并行执行，启动时 DFS 环检测 |

> 其余 14 个功能（动态 SQL、多源执行器路由、分页、结果映射、缓存、审批发布、导出、数据源管理、统一 API 契约层、JWT 鉴权、RBAC、审计日志、配置导入/导出、模板管理）本次未选中生成文档。完整功能清单（含未选中的）见 [features/inventory.md](features/inventory.md)。如需深入可另行挑选标记 `- [x]` 后重跑 lp-prompt-gen / doc。

## 学习路径

> 带着学习目标来，从这里决定按什么顺序读。

### 从零到理解主线

1. 先读 [docs/01-xml-dsl-engine.md](docs/01-xml-dsl-engine.md) —— **为什么先读它**：功能 #1 XML DSL 引擎在功能清单里标"无依赖（平台主线，学其它功能的前置）"，是 `dsp-engine` 模块的核心。`XmlEngine` 的四阶段流水线里第 2 阶段（DAG 查询编排）就是功能 #2 的入口，不先建立"四阶段 + InterfaceConfig + executeWithConfig"的整体认知，读 #2 会缺少执行上下文。
2. 再读 [docs/02-dag-orchestration.md](docs/02-dag-orchestration.md) —— **为什么接着读它**：功能 #2 DAG 编排在清单里显式标"依赖：XML DSL 数据查询引擎"，且 02 文档自身在"前置"一节点名"建议先学功能 #1"。`QueryOrchestrator` 正是 `XmlEngine.execute()` 流水线的第 2 阶段，理解了 #1 的流水线骨架，再读 #2 就能把它放到正确位置——看清楚 CompletableFuture、线程池、`depends` 拓扑是在哪个阶段插入的。

### 排序依据（依赖字段拓扑）

- 无依赖 → 排前：功能 #1 XML DSL 引擎（清单标注"无依赖，平台主线，学其它功能的前置"）。
- 依赖 1 个 → 排后：功能 #2 DAG 编排（清单标注"依赖：XML DSL 数据查询引擎"），排在 #1 之后。
- 依赖链完整，无断裂——本次选中的 2 个功能可构成完整阅读链。

> 未选中的 14 个功能若日后挑选生成，其依赖关系会延伸出更多分支（如分页依赖多源执行器、缓存依赖统一 API 契约层、审批依赖缓存等），届时重跑 lp-index 会自动重排路径。如需深入可另行挑选。

## 元信息

| 字段 | 值 | 来源 |
|------|------|------|
| 项目名称 | DSP AI 数据接口平台 | README 与 `docs/product-positioning.md` 的当前定位 |
| 生成时间 | 2026-06-14T12:58:26Z | `_meta/progress.json` 的 `generatedAt` |
| skill 版本 | learn-project v1 | `_meta/progress.json` 的 `skillVersion` |
| 源码版本/commit | 733a0e6 | `_meta/progress.json` 的 `sourceCommit` |
| 功能/选中/完成状态 | 共 16 功能，选中 2，文档已生成 2/2 | `_meta/progress.json` 的 `features`（selected/status） |
