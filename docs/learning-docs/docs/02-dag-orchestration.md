# DAG 多查询并行编排（CompletableFuture + 依赖拓扑 + 环检测）

> 学习对象：`dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/QueryOrchestrator.java`
> 适用读者：熟练掌握 Java 并发与 CompletableFuture、但首次接触 DSP 项目的开发者。
> 前置：建议先学功能 #1（XML DSL 引擎主线），理解 `XmlEngine` 四阶段流水线的位置。

---

## 1. 概览

### 这个功能是什么

DSP 的一个接口（`<interface transno="...">`）在 DSL 里可以声明**多个 `<query>`**，每个 query 路由到一个数据源（MySQL / HTTP / Dubbo / Mongo）执行。`QueryOrchestrator` 负责把这组 query **按依赖关系组织成有向无环图（DAG）**，并调度执行：

- **无依赖**的 query 并行提交到线程池，I/O 等待时间重叠；
- **有依赖**的 query（`<query depends="q1,q2">`）等待**全部**上游 query 完成后再执行；
- 启动时做 DFS 环检测，发现循环依赖直接抛异常，避免死锁与栈溢出。

### 为什么多查询接口需要并行编排

接口聚合是 DSP 的核心场景。典型案例如 `template/09-parallel-orchestration.xml` 的"仪表盘概览"：一个接口要同时拿 `q_user_stats`、`q_order_stats`、`q_api_stats` 三组统计。这三条 SQL 在 MySQL 里彼此独立、没有数据依赖。

- **串行执行**：总耗时 = t(user) + t(order) + t(api)。如果每条 80ms，接口要 240ms；其中绝大部分是网络往返与数据库 I/O 等待，CPU 基本闲着。
- **并行编排**：总耗时 ≈ max(t(user), t(order), t(api)) ≈ 80ms。三条 I/O 在不同线程上重叠等待，吞吐与延迟同时优化。

这就是经典的"I/O 密集型任务的并行化"。对 DSP 这类数据聚合中台，一个接口拉多个数据源是常态，并行编排带来的延迟改善是数量级的。

### 在引擎流水线中的位置

根据 `docs/engine-architecture.md`（第 5-14 行），`XmlEngine.execute()` 是一条四阶段流水线：

```
DataApiController → XmlConfigCacheManager → XmlEngine.execute()
    → XmlConfigParser（解析 XML → InterfaceConfig）
    → DynamicSqlHandler（<if>/<foreach>/#{} 占位符）+ PaginationHandler（分页改写）
    → QueryOrchestrator（CompletableFuture + DAG 依赖排序）   ← 本文档主角，阶段 2
        → SqlExecutor / HttpExecutor / DubboExecutor / MongoExecutor
    → ResultMapper（字段映射 + 内置函数）
    → ApiResponse
```

`QueryOrchestrator` 是**阶段 2 的执行调度器**：它本身不直接连数据库，而是接收一个 `Function<QueryConfig, List<Map<String, Object>>> executeFunc` 回调（由 `XmlEngine` 注入），回调内部按 `query.type` 路由到具体的多源执行器。这种"调度与执行分离"的设计让它只关心 DAG 与并行，不耦合具体协议。

---

## 2. 核心概念

### DAG（有向无环图）

把每个 query 看作一个**节点**，把 `depends="..."` 看作一条**从上游指向下游的边**，全体 query 的依赖关系构成一张图。"无环"是硬约束——出现环就没有合法的执行顺序。`QueryOrchestrator` 用 `depends` 字段表达这张图，并用 DFS 在编排前确认无环。

### `depends` 拓扑字段

`QueryConfig.java:17`：

```java
private List<String> depends = new ArrayList<>();
```

每个 query 携带一个 `depends` 列表，元素是被依赖 query 的 `id`（字符串）。例如 `<query id="q3_manager" depends="q2_dept">` 表示 `q3_manager` 必须在 `q2_dept` 完成后才能执行；`depends="q1,q2"` 则表示必须等 **q1 与 q2 都完成**（AND 语义，见 §3 的 `allOf`）。

### 拓扑示意

以 `template/10-dependency-orchestration.xml` 为例，五个 query 的依赖关系是：

```
              q1_user                ← 第 0 层（无依赖）
             /        \
        q2_dept      q2_perms        ← 第 1 层（都依赖 q1_user，二者互不依赖 → 并行）
            |
        q3_manager                   ← 第 2 层（依赖 q2_dept）
```

实线 = depends 边。**同一层内**的 query（如 `q2_dept` 与 `q2_perms`）没有相互依赖，会被并行调度；**跨层**则必须等待。这就是 DAG 调度要表达的全部信息。

### CompletableFuture 依赖链

`QueryOrchestrator` 用 JDK 的 `CompletableFuture` 把每个 query 包成一个异步任务，并通过组合算子（`supplyAsync` / `allOf + thenApplyAsync`）把依赖关系编码进 Future 链本身——不需要外部调度器，Future 完成时会自动触发依赖它的下游。

### 线程池（corePoolSize / maxPoolSize / 拒绝策略）

`QueryOrchestrator.java:17-23`，编排器持有一个**自建**的 `ThreadPoolExecutor`：

```java
new ThreadPoolExecutor(
    4, 8, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

参数取舍见 §3 设计决策 (c)。这里只需先记住：**核心 4、最大 8、有界队列 100、饱和时由调用线程自己跑（CallerRunsPolicy）**。

### `QueryConfig`

DSL `<query>` 节点解析后的 POJO（`QueryConfig.java`）。编排器关心的字段：

| 字段 | 用途 |
|---|---|
| `id` (`String`) | DAG 节点唯一标识，`depends` 引用它 |
| `type` (`String`，默认 `"mysql"`) | 决定 `executeFunc` 路由到哪个 executor |
| `datasource` (`String`) | 数据源名（Dynamic-DS 路由） |
| `depends` (`List<String>`) | 承载拓扑边，编排的核心输入 |

---

## 3. 如何工作

入口 `orchestrate(List<QueryConfig>, Function)`（`QueryOrchestrator.java:25-74`）分三段：**前置处理 → Future 构建 → 汇聚取数**。

### 3.1 三个分支与"单查询快路径"

`QueryOrchestrator.java:29-44`：

```java
if (queries == null || queries.isEmpty()) {
    return Collections.emptyMap();           // 分支 A：空查询，直接返回空 Map
}

if (queries.size() == 1) {                   // 分支 B：单查询，快路径
    QueryConfig query = queries.get(0);
    List<Map<String, Object>> result = executeFunc.apply(query);
    Map<String, List<Map<String, Object>>> results = new LinkedHashMap<>();
    results.put(query.getId(), result);
    return results;
}

// 分支 C：多查询，走完整 DAG 流程
Map<String, QueryConfig> queryMap = new LinkedHashMap<>();
for (QueryConfig q : queries) { queryMap.put(q.getId(), q); }

validateDependencies(queries, queryMap);      // 校验 depends 引用存在
detectCycle(queries, queryMap);               // DFS 环检测
```

> **设计决策 (a)：单查询为什么有独立快路径，不走线程池？**
>
> 单查询没有并行收益——并行编排的价值在"多个 I/O 重叠"，只有一个 query 时无 I/O 可重叠。如果照样走 `supplyAsync(...).get()`，相当于把任务提交到池里再阻塞当前线程等它跑完，徒增：
> 1. 一次线程切换（提交任务 → 工作线程拉起 → 调用方唤醒）；
> 2. 一次任务入队/出队开销；
> 3. 一层 Future 包装对象。
>
> 快路径（第 33-39 行）直接在调用线程同步 `executeFunc.apply(query)`，对 DSP 大量"单查询接口"场景是稳定无开销的优化。注意它仍返回 `Map<String, List<Map>>` 形态，与多查询路径完全一致，对下游 `ResultMapper` 透明。

### 3.2 `buildFuture`：递归构建依赖链

`QueryOrchestrator.java:76-123` 是 DAG 调度的心脏。它按 query 的 `depends` 分两种 Future 构造方式。

#### 无依赖 → `supplyAsync`（第 83-95 行）

```java
if (depends == null || depends.isEmpty()) {
    return CompletableFuture.supplyAsync(() -> {
        List<Map<String, Object>> result = executeFunc.apply(query);
        return result;
    }, executor);                            // ← QueryOrchestrator.java:85
}
```

直接把 query 丢给线程池异步执行，**立刻返回一个 in-flight 的 Future**。所有无依赖 query 在循环里被这样构造，它们彼此独立，于是天然并行——并行性来自"全部塞进同一个池"。

#### 有依赖 → `allOf + thenApplyAsync`（第 98-122 行）

```java
CompletableFuture<List<Map<String, Object>>>[] dependFutures = depends.stream()
    .map(depId -> {
        CompletableFuture<...> depFuture = existingFutures.get(depId);
        if (depFuture == null) {
            throw new RuntimeException("查询[" + query.getId() + "]依赖[" + depId + "]不存在");
        }
        return depFuture;
    })
    .toArray(CompletableFuture[]::new);

return CompletableFuture.allOf(dependFutures)          // ← QueryOrchestrator.java:111
    .thenApplyAsync(v -> {
        List<Map<String, Object>> result = executeFunc.apply(query);
        return result;
    }, executor);
```

关键三点：

1. **`allOf`：等待"全部完成"而非"任一完成"。** 学习目标 2 的答案——`depends="q1,q2"` 是 AND 语义，必须等 q1 和 q2 都完成才执行下游。若要"任一完成"语义需用 `anyOf`，但 DSP 不支持（级联查询天然要求所有上游数据到位）。
2. **`thenApplyAsync(..., executor)`：上游完成后，下游任务也提交回同一个线程池执行**，而不是在完成上游的那个线程上同步续跑。这一点很关键——若用同步 `thenApply`，下游的 I/O 会占用上游执行线程，长链路下会让线程池的并发度被依赖链"压扁"。`thenApplyAsync` 保证每段查询都重新走池调度。
3. **依赖查表 `existingFutures.get(depId)`：** 调用方 `orchestrate` 用 `for` 循环依次为每个 query 构造 Future 并放入 `ConcurrentHashMap futures`（第 49-54 行）。当某 query 的依赖恰好在它之前已被构造过，直接复用同一个 Future 对象——这是把依赖关系编码为 Future 共享引用，避免重复执行。

> **为什么用 CompletableFuture 而非显式 `Thread` / `executor.submit`？**
>
> 三个理由：(1) **依赖组合是声明式的**，`allOf(A, B).thenApplyAsync(C)` 一行表达"A、B 都完成后跑 C"，显式线程模型要手写 `CountDownLatch`/`join`/回调嵌套，DAG 越深越丑；(2) **取消、超时、异常传播有现成语义**，`CompletableFuture.allOf(...).get()` 一处 try/catch 就能捕获整张图里任何一个节点的失败；(3) **不阻塞调度线程**——编排器线程只需 `supplyAsync` 立即返回，I/O 等待全发生在池里。

### 3.3 `validateDependencies`：引用完整性

`QueryOrchestrator.java:125-135`，遍历每个 query 的 `depends`，若引用的 depId 不在 `queryMap` 里就抛 `"查询[id]依赖[depId]不存在"`。这是 fail-fast：拼写错的 depends 必须在编排前暴露，而不是在 `buildFuture` 里 `existingFutures.get(depId)` 返回 null 时才报。

### 3.4 `detectCycle`：DFS 三色标记环检测

`QueryOrchestrator.java:137-170`：

```java
private void detectCycle(List<QueryConfig> queries, Map<String, QueryConfig> queryMap) {
    Map<String, Integer> visited = new HashMap<>();          // 三色：0=未访问, 1=访问中(灰), 2=访问完(黑)
    for (QueryConfig q : queries) { visited.put(q.getId(), 0); }
    for (QueryConfig q : queries) {
        if (visited.get(q.getId()) == 0) {
            if (hasCycle(q.getId(), queryMap, visited)) {
                throw new RuntimeException("检测到循环依赖，请检查query的depends配置");
            }
        }
    }
}

private boolean hasCycle(String queryId, Map<String, QueryConfig> queryMap, Map<String, Integer> visited) {
    visited.put(queryId, 1);                                 // 进入节点：染灰
    QueryConfig query = queryMap.get(queryId);
    if (query.getDepends() != null) {
        for (String depId : query.getDepends()) {
            int state = visited.get(depId);
            if (state == 1) return true;                     // 遇到灰节点 → 回边 → 有环
            if (state == 0 && hasCycle(depId, queryMap, visited)) return true;
        }
    }
    visited.put(queryId, 2);                                 // 离开节点：染黑
    return false;
}
```

经典三色 DFS：白（0）未访问、灰（1）在当前递归栈中、黑（2）已彻底处理。沿 depends 边递归，若再次撞到灰节点说明出现了**回边**，即存在环。

> **设计决策 (c)：环检测为什么必须在编排前执行？**
>
> 如果不检测，会发生两种灾难之一（学习目标 4）：
>
> 1. **栈溢出**：`buildFuture` 不直接递归（依赖查表方式），但若改成"按需递归构造依赖 Future"的实现，环会无限递归；即便当前实现，环会让某 query 的依赖 Future 永远处于 `existingFutures` 未完成状态。
> 2. **死锁**：环中所有 query 都在 `allOf(...).thenApplyAsync` 等待对方完成，整张图没有任何节点能推进。配合线程池有限容量，环甚至会把池占满，让其他接口也卡死。
>
> 前置环检测把"非法配置"在调度前一次性 fail-fast，是 DAG 调度器的标配防御。

### 3.5 汇聚：`allOf(...).get()` 与异常传播

`QueryOrchestrator.java:56-71`：

```java
Map<String, List<Map<String, Object>>> results = new LinkedHashMap<>();
try {
    CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).get();   // 第 58 行
    for (QueryConfig query : queries) {
        results.put(query.getId(), futures.get(query.getId()).get());                    // 逐个取结果
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new RuntimeException("查询编排被中断", e);
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;                                                  // 直接解包抛回
    }
    throw new RuntimeException("查询编排执行失败: " + cause.getMessage(), cause);
}
return results;
```

学习目标 7 的答案——异常传播链：

1. 任一 query 在工作线程里抛异常（如 SQL 失败），异常被 CompletableFuture 捕获，该 Future 进入 **completed exceptionally** 状态；
2. 依赖它的 `allOf + thenApplyAsync` 链短路，下游 Future 也异常完成；
3. 顶层 `allOf(...).get()` 检测到至少一个异常 Future，抛 `ExecutionException`，**真实原因包在 `getCause()` 里**；
4. 编排器解包：若是 `RuntimeException` 直接原样抛（保留业务异常类型，如 DSL 解析/数据源异常），否则包一层 `RuntimeException`。

注意：`allOf` 语义是"全部完成"，但当任一 Future 异常完成时 `allOf` 也会立即异常完成（不必等其他 Future）。这带来一个权衡——**第一个失败即短路返回**，未完成的 query 仍可能继续在池里跑（CompletableFuture 本身不会主动取消其它 Future），但调用方已拿到异常返回。这对 DSP 是合理取舍：避免一个慢查询拖住错误反馈，代价是池里可能残留少量"事后跑完被丢弃"的任务。

### 3.6 线程池参数的取舍（学习目标 5）

`QueryOrchestrator.java:18-22` 选了 `corePoolSize=4, maxPoolSize=8, queueCapacity=100, keepAlive=60s, 拒绝策略=CallerRunsPolicy`。

- **core=4 / max=8**：DSP 单实例的接口并发由网关/上游限流，4 个常驻线程覆盖常态；突发流量扩到 8 是上限保护，避免数据库连接池被编排线程打爆。8 这个数字也常见匹配 MySQL 默认连接池配置（如 HikariCP 10）——编排线程 ≤ 8，留余量给其他直连查询。
- **有界队列 100**：避免无界队列（如 `Executors.newFixedThreadPool` 用的 `LinkedBlockingQueue` 无界）在突发下堆积 OOM。100 是经验上限，超了就触发拒绝策略。
- **`CallerRunsPolicy` 而非 `AbortPolicy`**：这是**反压语义**的选择。
  - `AbortPolicy` 会抛 `RejectedExecutionException`，对一个数据查询接口意味着"系统忙时接口直接 500"，体验差；
  - `CallerRunsPolicy` 让调用 `supplyAsync`/`thenApplyAsync` 的那个线程**自己同步执行任务**，相当于"队列满了就让请求线程自己跑"，自然降低提交速率（调用方被阻塞，无法继续往池里塞），形成隐式背压。
  - 副作用：饱和时部分 query 在调用线程上跑，并行度暂时退化——但对 DSP 这是可接受的降级，远好于抛错。`thenApplyAsync` 走 `CallerRunsPolicy` 时退化为同步续跑，依赖链正确性不变。

---

## 4. 使用示例

### 示例 A：纯并行（`template/09-parallel-orchestration.xml`）

三个独立查询，**没有任何 `depends`**：

```xml
<query id="q_user_stats"  type="mysql" datasource="ds_main"> ... </query>
<query id="q_order_stats" type="mysql" datasource="ds_main"> ... </query>
<query id="q_api_stats"   type="mysql" datasource="ds_main"> ... </query>
```

DAG：

```
q_user_stats   q_order_stats   q_api_stats       ← 三个孤立的根节点
```

执行：`buildFuture` 对三者都走 `supplyAsync` 分支（第 85 行），三个 Future 几乎同时塞进线程池。若池里至少有 3 个空闲线程，三条 SQL 同时下发到 MySQL，I/O 等待重叠，总耗时 ≈ max 三条。最后 `allOf(...).get()` 汇聚，按 id 装进 `Map`，交 `ResultMapper` 用 `userStatsMap`/`orderStatsMap`/`apiStatsMap` 三个 resultMap 映射并组装到响应。

### 示例 B：依赖 + 同层并行（`template/10-dependency-orchestration.xml`）

五个 query，显式 `depends`：

```xml
<query id="q1_user"    ...>                                                   <!-- 无依赖 -->
<query id="q2_dept"    depends="q1_user"  ... WHERE id = #{$q1_user['department_id']}">
<query id="q2_perms"   depends="q1_user"  ... WHERE min_level <= #{$q1_user['level']}">
<query id="q3_manager" depends="q2_dept"  ... WHERE id = #{$q2_dept['manager_id']}">
```

DAG（已在 §2 画过）：

```
          q1_user
         /        \
    q2_dept      q2_perms          ← 都 depends q1_user，互不依赖
        |
    q3_manager                     ← depends q2_dept
```

执行时序：

1. `q1_user`：无依赖，`supplyAsync` 立即提交，池里跑 SQL。
2. `q2_dept` 与 `q2_perms`：各自 `allOf([q1_user 的 Future]).thenApplyAsync`。两者**都只等 `q1_user`**，不互相等。当 `q1_user` 完成的瞬间，`q2_dept` 和 `q2_perms` 同时被提交回线程池——**第二层并行**。
3. `q3_manager`：`allOf([q2_dept 的 Future]).thenApplyAsync`，必须等 `q2_dept` 完成。注意它**不等 `q2_perms`**——`q2_perms` 可能比 `q3_manager` 先跑完或后跑完，互不阻塞。
4. 顶层 `allOf(all five).get()` 等所有 5 个 Future 完成，按 id 收集结果。

这个例子展示了 DAG 编排相对纯并行的真正价值：**既能让同层查询重叠 I/O（q2_dept ∥ q2_perms），又能保证跨层数据依赖（q3_manager 用 q2_dept 的结果作为 WHERE 条件）**。`#{$q1_user['department_id']}` 这类占位符由前置阶段 `DynamicSqlHandler` 在执行前替换为上游结果值——编排器只负责"在 q1_user 完成后再让 q2_dept 执行"，保证替换时上游结果已就绪。

---

## 5. 与其它部分的关系

### 与 XML DSL 引擎（功能 #1）的衔接

`QueryOrchestrator` 是 `XmlEngine.execute()` 流水线**阶段 2 的执行调度器**。引擎主线（`XmlEngine`）按 `engine-architecture.md` 描述：解析 XML（`XmlConfigParser`）→ 参数处理（`DynamicSqlHandler` + `PaginationHandler`）→ **编排调度（本类）** → 结果映射（`ResultMapper`）。本类通过构造时注入的 `Function<QueryConfig, List<Map<String, Object>>> executeFunc` 回调与 `XmlEngine` 解耦——编排器只调度，具体"怎么执行一个 query"由 `XmlEngine` 在 `executeFunc` 内实现（含 SQL 模板渲染、分页改写、参数注入等）。

### 与多源执行器路由（功能 #4）的衔接

`executeFunc` 内部按 `query.getType()` 路由到 `SqlExecutor`（mysql）、`HttpExecutor`（http）、`DubboExecutor`（dubbo）、`MongoExecutor`（mongo）。**编排器对协议完全无感知**——它看到的只是 `Function` 返回 `List<Map<String, Object>>`。这带来一个重要性质：一个接口里可以混用不同协议的 query，例如先 `mysql` 查用户、再 `http` 调风控、最后 `mysql` 落库，DAG 编排与依赖等待机制对它们一视同仁。`QueryConfig` 的 `type` 字段（默认 `"mysql"`）就是路由依据。

### 与结果映射（功能 #6）的衔接

编排器产出 `Map<String, List<Map<String, Object>>>`（queryId → 行列表）。`ResultMapper` 消费这个 Map，按 `<resultMap query="q1_user">` 把每个 query 的原始列结果映射为目标字段名（支持 `fn:TYPE_CONVERT,LONG`/`fn:ROUND,2` 等内置函数）。`<responseData>` 再按 `mapTo="resultMapId"` 把多个映射后的对象拼装成最终 `ApiResponse`。**编排器产出的 Map 顺序是 `LinkedHashMap`（按 query 声明顺序，`QueryOrchestrator.java:56`），保证下游组装稳定可预测**。

### 与单查询执行（`XmlEngine.executeQuery`）的衔接

`engine-architecture.md` 第 27-31 行还描述了"单查询执行"的细节（`XmlEngine.executeQuery`）：`DynamicSqlHandler` 处理 `<if>`/`<foreach>`/`#{}` → `PaginationHandler` 改写分页 → 路由 executor。这其实就是 `executeFunc` 的实现内部——编排器调 `executeFunc.apply(query)` 时，执行的就是这条单查询路径。换言之，**编排器把"多查询 DAG"降级为"反复调单查询执行"**，并行性与依赖性全靠 Future 组合实现，不污染单查询执行逻辑。

---

## 6. 延伸

可深入的方向：

- **线程池参数按接口调优**：当前 `QueryOrchestrator` 用一个全局单例池（`@Component`，`core=4/max=8/queue=100`）。不同接口的 query 数、单 query 耗时差异巨大（如一个 `http` query 可能 500ms，一个 `mysql` 50ms）。可以考虑按 transno 维度配置池参数，或对长耗时接口隔离独立池，避免"慢接口独占常驻线程"拖累快接口。代价是池数量爆炸，需要权衡。
- **`CompletableFuture` 异常处理的进阶模式**：当前是"首失败即短路"。若需要"尽力而为"（部分 query 失败仍返回其他结果），可改用 `handle`/`exceptionally` 在 Future 层吞掉异常并返回空结果，由 `ResultMapper` 标记缺失字段。注意这会改变 `allOf(...).get()` 的语义，需重构汇聚逻辑。
- **超时控制缺失**：当前 `get()` 无超时，理论上一个挂死的下游会无限阻塞接口。生产建议加 `allOf(...).get(timeout, unit)` 或对每个 query Future 用 `orTimeout`（JDK 9+）。这是当前实现的明显短板，值得在工程化时补上。
- **为何不直接用 Guava `Graph`/`common-graph` 等现成 DAG 框架？** DSP 的 DAG 极其简单——拓扑只来自 `depends` 字符串列表，环检测是标准三色 DFS。引入 Guava Graph 反而要付出"POJO ↔ Graph 节点转换 + 额外依赖"的成本，而 JDK 原生 `CompletableFuture` 已能完整表达依赖链与并行性，三色 DFS 也只有十几行（`QueryOrchestrator.java:152-170`）。这是典型的"用对工具，不为简单问题引重框架"的取舍。
- **取消传播**：`CompletableFuture` 默认不级联取消。若接口因超时被网关断开，已提交到池的 query 不会自动取消（除非 JDBC `Statement.cancel`）。可考虑在编排器层暴露 `cancel()`，配合中断响应实现级联取消。
- **与 Future 续算的隔离**：`thenApplyAsync(..., executor)` 保证每段查询回池重调度，但若依赖链极深（10 层），仍会串行通过池。可考虑对长链路接口用 `Stream.parallel()` 或显式并行 fork 点优化，但 DSP 实际场景里 3-4 层依赖已是上限，当前设计足够。

---

## 附：学习目标自检

| # | 问题 | 答案位置 |
|---|---|---|
| 1 | 为什么多查询需要并行编排？串行的问题？ | §1（I/O 等待重叠，延迟从 sum 变 max） |
| 2 | `depends` 如何表达 DAG？多上游是全部还是任一完成？ | §2、§3.2（`allOf` → 全部完成，AND 语义） |
| 3 | 无依赖 vs 有依赖的 Future 构造方式？为何这样选？ | §3.2（`supplyAsync` vs `allOf+thenApplyAsync`） |
| 4 | 环检测为什么必须前置？不检测会怎样？ | §3.4（死锁 / 栈溢出） |
| 5 | 线程池参数取舍？为何 CallerRunsPolicy？ | §3.6（反压语义） |
| 6 | 单查询快路径为何不走池？解决了什么？ | §3.1（无并行收益，省线程切换与包装开销） |
| 7 | 编排异常如何向上传播？ | §3.5（Future exceptionally → `ExecutionException` → 解包 `getCause`） |

所有 7 个学习目标均已覆盖，无"待补"项。
