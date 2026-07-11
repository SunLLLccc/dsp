# 教学提示词 — DAG 多查询并行编排（CompletableFuture + 依赖拓扑 + 环检测）

> 由 lp-prompt-gen 生成。供 doc 生成 subagent 执行，产出 `docs/learning-docs/docs/02-dag-orchestration.md`。
> 可复用/重跑：修改本文件后重新 dispatch 即可重新生成文档。

## 任务

你是技术文档撰写者。目标读者：**有经验的开发者，但不熟悉本项目**（假设 Java 并发/CompletableFuture 熟练，缺的是 DSP 项目特定知识）。
请阅读下列证据代码，撰写关于「DAG 多查询并行编排」的学习文档，写到 `{PROJECT_ROOT}/docs/learning-docs/docs/02-dag-orchestration.md`。

## 功能背景

- 功能名：DAG 多查询并行编排（CompletableFuture + 依赖拓扑 + 环检测）
- 简介：QueryOrchestrator 解析 `<query depends="q1,q2">` 构建有向无环图，无依赖查询并行提交线程池、有依赖查询等待上游 Future 完成，启动时 DFS 检测循环依赖直接抛异常。
- 核心度：核心（README 核心特性"查询并行编排"；docs/engine-architecture.md 专节描述；区别于串行执行的普通查询框架）
- 复杂度：高
- 前置依赖：XML DSL 数据查询引擎（QueryOrchestrator 是 XmlEngine 阶段 2 的执行组件，建议先学功能 #1 引擎主线）

## 必读证据（请实际打开阅读，勿臆造）

核心代码（路径已核实存在，均为该功能核心）：

- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/QueryOrchestrator.java`: DAG 编排主类，整个功能的核心。重点读：
  - 构造器（第 17-23 行）：`ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS, LinkedBlockingQueue(100), CallerRunsPolicy)` —— 固定参数线程池的选型理由（核心 4、最大 8、拒绝策略 CallerRunsPolicy）。
  - `orchestrate(List<QueryConfig>, Function)`（第 25-74 行）：编排总入口。注意三个分支：空查询直接返回、单查询走快路径（第 33-39 行，不走线程池）、多查询走完整 DAG 流程（validateDependencies → detectCycle → buildFuture → allOf().get() 汇聚）。
  - `buildFuture`（第 76-123 行）：递归构建 CompletableFuture 链的核心。无依赖走 `supplyAsync`（第 85 行），有依赖走 `allOf(dependFutures).thenApplyAsync`（第 111 行）—— 这是 DAG 依赖等待的关键。
  - `validateDependencies`（第 125 行起）：校验 `depends` 引用的 queryId 必须存在。
  - `detectCycle`（第 137 行起）：DFS 环检测，循环依赖直接抛异常，防止无限递归与死锁。
- `template/09-parallel-orchestration.xml`: 并行编排 DSL 示例（多个无依赖 `<query>` 并行执行），用于演示"无 depends"分支。
- `template/10-dependency-orchestration.xml`: 依赖编排 DSL 示例（`<query depends="q1,q2">` 显式声明依赖），用于演示"有 depends"分支与 DAG 拓扑。
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/model/QueryConfig.java`: `QueryConfig` 数据结构，重点关注 `getDepends()` 返回的 `List<String>` 与 `getId()`/`getType()`/`getDatasource()`，理解 depends 字段如何承载拓扑信息。

设计文档：

- `docs/xml-dsl-reference.md`: "depends 用于 DAG 依赖排序"章节，DSL 层面依赖声明的语法权威说明。
- `docs/engine-architecture.md`: 引擎架构文档中关于查询并行编排的专节（应与代码互证）。

背景读物（读者如有需要可先读）：

- `docs/project-knowledge/01-001-项目概览.md`、`docs/project-knowledge/02-001-技术栈与架构.md`: 帮助理解引擎在分层中的位置与并发模型选型背景。

## 学习目标（读者读完后应能回答）

1. 为什么一个接口里多个查询需要并行编排？串行执行会有什么问题？（WHY：延迟、I/O 等待重叠）
2. `depends` 字段是如何表达 DAG 依赖关系的？一个 query 依赖多个上游时，是等待"全部完成"还是"任一完成"？（提示：`allOf`）
3. 无依赖查询和有依赖查询的 Future 构建方式有何不同？分别用了 `supplyAsync` 还是 `thenApplyAsync`？为什么这样选？
4. 环检测（`detectCycle`）为什么必须在编排前执行？如果不检测会怎样？（死锁/栈溢出）
5. 线程池参数（核心 4、最大 8、队列 100、CallerRunsPolicy）的取舍是什么？为什么用 CallerRunsPolicy 而非 AbortPolicy？
6. 单查询为什么有独立的快路径（第 33-39 行）不走线程池？这个优化解决了什么问题？
7. 编排异常是如何向上传播的？（提示：`ExecutionException` 解包，第 62-71 行）

## 文档结构（严格按此 6 节输出）

1. **概览**：讲清这个功能是什么、为什么多查询接口需要并行编排、它在引擎四阶段流水线中处于阶段 2 的位置（被 XmlEngine 调用）。
2. **核心概念**：解释 DAG（有向无环图）、`depends` 拓扑、CompletableFuture 依赖链、`QueryConfig`、线程池（corePoolSize/maxPoolSize/拒绝策略）等关键术语，画出"查询节点 + depends 边"的拓扑示意。
3. **如何工作**：走读 `orchestrate` → `buildFuture` → `validateDependencies`/`detectCycle` 源码（引行号），讲清三个关键设计决策的 WHY：(a) 单查询快路径为何不走线程池；(b) 无依赖 supplyAsync vs 有依赖 allOf+thenApplyAsync 的选择理由；(c) 环检测为何前置。讲清异常从 Future 到调用方的传播链。
4. **使用示例**：用 `template/09-parallel-orchestration.xml`（并行）和 `template/10-dependency-orchestration.xml`（依赖）两个真实 DSL 示例，画出对应的 DAG 图，说明哪些查询并行、哪些串行等待。
5. **与其它部分的关系**：讲清与 XML DSL 引擎（功能 #1，作为阶段 2 执行器）、多源执行器路由（功能 #4，`executeFunc` 实际分发到 SQL/HTTP/Dubbo/Mongo）、结果映射（功能 #6，消费编排产出的 `Map<String, List<Map>>`）的衔接。
6. **延伸**：列出可深入的方向，如线程池参数如何按接口调优、CompletableFuture 异常处理的进阶模式、为何不直接用现成 DAG 框架（如 Guava Graph）等。

## 撰写要求

- 讲设计思想与 WHY，不只堆代码。重点解释"为什么用 CompletableFuture 而非显式 Thread/ExecutorService.submit"、"为什么单查询要快路径"、"CallerRunsPolicy 的反压语义"。
- 代码走读引用真实文件路径与关键片段（含行号），如 `QueryOrchestrator.java:25-74`、`QueryOrchestrator.java:111`。
- 假设读者懂 Java 并发与 CompletableFuture 基础，不讲"什么是 Future/线程池"通用概念，只讲项目特定的编排设计与取舍。
- 篇幅适中，聚焦讲透 DAG 构建 + 依赖等待 + 环检测这三件事，不灌水展开线程池所有参数的通用含义。
- 若某个学习目标证据不足，如实标注"待补"，不臆造路径。
