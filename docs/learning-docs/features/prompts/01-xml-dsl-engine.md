# 教学提示词 — XML DSL 数据查询引擎（四阶段执行流水线）

> 由 lp-prompt-gen 生成。供 doc 生成 subagent 执行，产出 `docs/learning-docs/docs/01-xml-dsl-engine.md`。
> 可复用/重跑：修改本文件后重新 dispatch 即可重新生成文档。

## 任务

你是技术文档撰写者。目标读者：**有经验的开发者，但不熟悉本项目**（假设 Java/Spring/DOM4J 熟练，缺的是 DSP 项目特定知识）。
请阅读下列证据代码，撰写关于「XML DSL 数据查询引擎」的学习文档，写到 `{PROJECT_ROOT}/docs/learning-docs/docs/01-xml-dsl-engine.md`。

## 功能背景

- 功能名：XML DSL 数据查询引擎（四阶段执行流水线）
- 简介：通过 XML 配置定义查询接口的执行结构（参数/查询/数据源/结果映射/响应），XmlEngine 按"参数校验 → DAG 查询编排 → 结果映射 → 响应构建"四阶段流水线执行，是 DSP 查询接口的运行执行底座。
- 核心度：核心（README 将其定位为 XML DSL 执行底座；docs/engine-architecture.md 整篇围绕它；6 个模块全部直接或间接依赖它）
- 复杂度：高
- 前置依赖：无（这是平台主线，学其它功能的前置）

## 必读证据（请实际打开阅读，勿臆造）

核心代码（路径已核实存在，均为该功能核心）：

- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java`: 引擎主类。重点读 `executeWithConfig(InterfaceConfig, Map, DebugContext)`（第 65-121 行）—— 这是四阶段流水线的总入口，依次执行 `validateParams` → `queryOrchestrator.orchestrate` → `resultMapper.mapResult` → `resultMapper.buildResponse`，每阶段用 `recordStep/recordVoidStep` 包裹以支持零开销调试跟踪。同时看 `executeWithConfig` 两套入口（带/不带 DebugContext，第 58-60 行）与 `validateParams`（第 328 行起）了解参数校验。
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/parser/XmlConfigParser.java`: XML → InterfaceConfig 的解析器（基于 DOM4J）。重点读 `parse(String)`（第 15 行）与 `parseInterface`（第 25 行），以及 `parseRequestData`/`parseDataSource`/`parseQuery`/`parseResultMap`/`parseResponseData` 五个分块解析方法（第 56-266 行），理解 XML 五部分如何映射为 InterfaceConfig 数据结构。
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java` 第 28 行（`queryOrchestrator` 字段）与第 27 行（`resultMapper` 字段）: 理解引擎如何通过组合而非继承把编排与映射委托出去。
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java` 第 155 行 `registerInlineDataSources`: 了解 XML 内联数据源的运行时注册机制（与多数据源特性的衔接点）。

设计文档：

- `docs/engine-architecture.md`: 完整记录引擎总览流程与详细执行流水线（项目自维护的权威设计文档，应与代码互证）。
- `README.md`: "XML DSL 执行底座 — 通过 XML 定义请求、查询、结果映射和响应结构，由运行引擎解释执行"章节，定位引擎价值。

背景读物（读者如有需要可先读）：

- `docs/project-knowledge/01-001-项目概览.md`: 项目整体定位与模块划分。
- `docs/project-knowledge/02-001-技术栈与架构.md`: 技术栈与分层架构，帮助理解引擎在分层中的位置。

## 学习目标（读者读完后应能回答）

1. 这个引擎解决什么问题？为什么 DSP 选择"XML DSL + 引擎"作为查询接口执行底座，而非每个接口都写 Controller 编码？（WHY）
2. `InterfaceConfig` 这个核心数据结构由哪几部分组成？XML 的 `<request>`/`<datasource>`/`<query>`/`<resultMap>`/`<responseData>` 各自对应哪个字段？
3. 四阶段流水线的执行顺序是什么？每个阶段的输入/输出分别是什么类型？
4. 阶段之间如何传递数据？（提示：`OrchestrationContext`、`Map<String, List<Map<String,Object>>>` 结果集、`mappedResults`）
5. `DebugContext` 是怎么实现"带调试零开销"的？两套 `executeWithConfig` 入口的设计意图是什么？
6. 引擎与 `QueryOrchestrator`、`ResultMapper` 是什么关系？为什么用组合（字段注入）而非把编排/映射逻辑直接写进 XmlEngine？

## 文档结构（严格按此 6 节输出）

1. **概览**：讲清这个引擎是什么、为什么 DSP 需要统一的查询接口执行底座、引擎在整个平台 6 模块中的核心位置（谁依赖它）。
2. **核心概念**：解释 `InterfaceConfig`、四阶段流水线、`DebugContext`、`OrchestrationContext`、`ResultMapConfig`/`ResponseDataConfig` 等关键术语与数据结构，画出 XML 五部分 → InterfaceConfig 字段的对应关系。
3. **如何工作**：按四阶段顺序走读 `executeWithConfig`（第 65-121 行）源码，讲清每阶段做什么、为什么这么设计（如阶段三对"无 resultMap 时自动 map/list"的兜底逻辑 WHY）。引真实路径与行号，讲 WHY 不止 WHAT。
4. **使用示例**：从 `template/` 目录任取一个 SQL 查询示例 XML，配合 `executeWithConfig` 的调用方代码，端到端演示"一份 XML → 一个接口响应"。
5. **与其它部分的关系**：讲清引擎与 DAG 编排（功能 #2）、结果映射与内置函数（功能 #6）、多源执行器路由（功能 #4）、XML 配置多级缓存（功能 #7）的衔接点，引出"学完引擎后下一步可探索什么"。
6. **延伸**：列出后续可深入学习的相关功能（DAG 编排、动态 SQL、多源执行器等），以及引擎可能的扩展点（如新增执行器类型、新增函数）。

## 撰写要求

- 讲设计思想与 WHY，不只堆代码。重点解释"为什么用四阶段流水线而非一个大方法"、"为什么 DebugContext 设计成零开销"、"为什么用组合委托"。
- 代码走读引用真实文件路径与关键片段（含行号），如 `XmlEngine.java:65-121`、`XmlConfigParser.java:25`。
- 假设读者懂 Java/Spring/DOM4J，不讲通用语法（如"什么是 CompletableFuture"），只讲项目特定的设计决策。
- 篇幅适中，聚焦讲透四阶段流水线这个核心，不灌水展开每条 XML 标签的细节（那是功能 #3 动态 SQL 等单独文档的职责）。
- 若某个学习目标证据不足，如实标注"待补"，不臆造路径。
