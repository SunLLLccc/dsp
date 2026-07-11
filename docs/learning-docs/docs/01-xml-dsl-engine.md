# XML DSL 数据查询引擎（四阶段执行流水线）

> 目标读者：有经验的 Java/Spring 开发者，但不熟悉 DSP 平台。本文假设你已熟练掌握 Java 21、Spring DI、DOM4J、CompletableFuture；只讲 DSP 特定的设计决策，不讲通用语法。
>
> 配套阅读：`dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java`、`docs/engine-architecture.md`。

---

## 1. 概览

### 1.1 这个引擎是什么

DSP 当前定位是**面向企业查询型数据接口的 AI 辅助设计、受控生成与全生命周期治理平台**。在这条链路里，XML DSL 不是第一卖点，而是查询接口的执行底座：平台把已确认的接口配置（入参、查询、数据源、字段映射、响应拼装）交给运行时解释执行，最终形成可调用的 HTTP 查询接口。承担这个"解释并执行"职责的，就是 **`XmlEngine`**。

> 当前产品价值定位见 `README.md` 与 `docs/product-positioning.md`：AI 负责候选内容生成，Schema Evidence 与模板负责约束，平台负责确认、验证、发布、授权、审计和运行治理。

`XmlEngine` 把执行过程切成了**四个串行的阶段**（参数校验 → DAG 查询编排 → 结果映射 → 响应构建），每个阶段都是一个边界清晰、可单独替换的步骤。这就是本文反复强调的"**四阶段执行流水线**"。

### 1.2 为什么是 XML DSL + 引擎，而不是 Controller 编码

传统写 Controller 的成本在 DSP 这类平台里被放大：一个数据接口往往只是"几个参数 → 一段 SQL → 几个字段映射"，但每次都要走 Controller / Service / DTO / 单测 / 打包 / 上线的全流程，业务方与开发来回沟通，发布周期以天计。DSP 的选择是把这些"同构但参数不同"的接口抽象成一种 **DSL（XML）**，把"如何执行"沉淀成一个稳定内核（`XmlEngine`），从而：

- **接口执行结构可配置**：接口的入参、查询、映射和响应结构可以沉淀为 XML，由统一引擎执行；
- **运行时统一治理**：鉴权、审批、审计、缓存、调试追踪等横切关注点可以集中加在 `XmlEngine` 周围，而不用每个接口各写一遍；
- **并行编排天然支持**：因为执行语义被 DSL 化了，引擎才能感知到"这几个查询互相独立"，进而用 DAG 自动并行（见第 5 节）。

这是典型的"**把变化（业务接口）与不变（执行引擎）分离**"的决策——XML 承担变化，`XmlEngine` 承担不变。

### 1.3 引擎在 6 模块中的核心位置

`XmlEngine` 住在 `dsp-engine` 模块，是上层 3 个可部署服务的共同依赖（见 `README.md` 模块依赖图）：

```
dsp-common  →  dsp-core  →  dsp-engine  →  dsp-data-service   (数据查询/导出，调 executeWithConfig)
                                      └─→  dsp-offline-service  (离线导出，复用引擎执行单查询)
                                      └─→  dsp-admin-service     (接口调试，调 execute + DebugContext)
```

`dsp-engine` 之内的几乎所有类（`XmlConfigParser`、`QueryOrchestrator`、`SqlExecutor`/`HttpExecutor`/`DubboExecutor`/`MongoExecutor`、`ResultMapper`、`DynamicSqlHandler`、`PaginationHandler`）都是被 `XmlEngine` 直接或间接驱动的。换言之，**学透 `XmlEngine` 的四阶段流水线，就拿到了理解整个 DSP 引擎层的钥匙**。

---

## 2. 核心概念

### 2.1 `InterfaceConfig`：XML 的内存投影

XML 解析后的产物是 `InterfaceConfig`（`dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/model/InterfaceConfig.java`）。它的字段与 XML 的五部分**一一对应**，这是整个引擎最重要的数据结构：

```java
// InterfaceConfig.java:6-15
public class InterfaceConfig {
    private String transno;                                  // interface@transno
    private String name;                                     // interface@name
    private String description;                              // interface@description
    private RequestDataConfig requestData;                   // <requestData>
    private List<DataSourceConfig> dataSources = ...;        // <datasource>（可多个，内联数据源）
    private List<QueryConfig> queries = ...;                 // <query>（可多个，构成 DAG）
    private List<ResultMapConfig> resultMaps = ...;          // <resultMap>（可多个）
    private ResponseDataConfig responseData;                 // <responseData>
}
```

**XML 五部分 → InterfaceConfig 字段对应关系**：

| XML 标签 | InterfaceConfig 字段 | 对应 POJO | 作用 |
|---|---|---|---|
| `<requestData>` | `requestData` | `RequestDataConfig`（含 `List<ParamConfig>`） | 入参定义：name / type / required / defaultValue |
| `<datasource>` | `dataSources` (List) | `DataSourceConfig` | 引用或内联数据源，按 name 索引 |
| `<query>` | `queries` (List) | `QueryConfig` | 一条查询：id / type / datasource / depends / sql / dynamicSqls / paginationConfig |
| `<resultMap>` | `resultMaps` (List) | `ResultMapConfig`（含 `List<FieldMapping>`） | 列名 → 输出字段映射，可挂 `function` |
| `<responseData>` | `responseData` | `ResponseDataConfig`（含 `List<ResponseFieldConfig>`） | 把多个 resultMap 拼成最终响应，`as` 控制单对象/列表 |

解析器 `XmlConfigParser`（`dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/parser/XmlConfigParser.java`）用 DOM4J 完成 XML → POJO 的映射。`parseInterface`（`XmlConfigParser.java:25-54`）按 `switch(tagName)` 把五种子标签分发到 `parseRequestData` / `parseDataSource` / `parseQuery` / `parseResultMap` / `parseResponseData`（`XmlConfigParser.java:56-279`），未知标签只 `log.warn` 不报错——这是一个**前向兼容**设计：新版本加新标签，旧引擎跑老 XML 不会崩。

### 2.2 四阶段流水线

`executeWithConfig`（`XmlEngine.java:65-121`）的执行顺序：

| 阶段 | 名称 | 干什么 | 输入 | 输出 |
|---|---|---|---|---|
| 0 | （预处理） | 设置 transno / startTime | `InterfaceConfig`, `requestData`, `DebugContext?` | — |
| 1 | **PARAM_VALIDATE** | 必填参数校验，支持 `defaultValue` 回退 | `RequestDataConfig`, `requestData` | void（缺失则抛异常） |
| 2 | **QUERY_EXECUTE** | DAG 编排 + 实际执行（SQL/HTTP/Dubbo/Mongo） | `List<QueryConfig>`, 单查询执行回调 | `Map<String, List<Map<String,Object>>>`（queryId → 行集） |
| 3 | **RESULT_MAP** | 列名映射 + 函数转换；无 resultMap 时兜底 | 上一步结果集 + `List<ResultMapConfig>` | `Map<String, Object> mappedResults`（resultMapId → 映射后数据） |
| 4 | **RESPONSE_BUILD** | 按 `<responseData>` 拼装最终 JSON | `ResponseDataConfig`, `mappedResults` | `Object`（最终响应体） |

### 2.3 `OrchestrationContext`：阶段之间的查询结果桥梁

这是 `XmlEngine` 内部的静态类（`XmlEngine.java:342-366`），承担两件事：

- 持有不可变的 `requestData`（原始入参）；
- 持有 `ConcurrentHashMap previousResults`，让"依赖了上游 query 的下游 query"能拿到上游结果。

**关键设计**（`XmlEngine.java:359-365`）：上游结果**只有一行时**被压缩成单 `Map`，多行时保留 `List<Map>`。这让下游 SQL 里写 `#{q1.userId}`（拿单值）和 `#{q1.id}` 都能自然 work，是动态 SQL 跨查询引用的语义基础。

### 2.4 `DebugContext`：带调试零开销

`DebugContext`（`dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/DebugContext.java`）只在调试模式下由调用方 `new` 出来传入。它的 `traces` / `steps` 用 `CopyOnWriteArrayList`（`DebugContext.java:19-20`），因为 `QueryOrchestrator` 在线程池里并发写。生产查询**传 null**，引擎走"零开销"分支（见 3.5）。

---

## 3. 如何工作（四阶段走读）

本节按 `executeWithConfig`（`XmlEngine.java:65-121`）的实际行号走读，重点讲 **WHY**。

### 3.1 预处理：transno 与计时锚点

```java
// XmlEngine.java:66
log.info("XML解析完成: transno={}, queries={}", config.getTransno(), config.getQueries().size());

if (debugContext != null) {              // 行 68-71
    debugContext.setTransno(config.getTransno());
    debugContext.setStartTimeMs(System.currentTimeMillis());
}
```

**WHY**：`log.info` 这条日志是引擎入口的"心跳"，运维侧通过它能看到 QPS 与查询数。`startTimeMs` 只在 `debugContext != null` 时记录——生产路径根本不构造 `DebugContext`，连一次 `System.currentTimeMillis()` 都不浪费。

### 3.2 阶段 1：参数校验（PARAM_VALIDATE）

```java
// XmlEngine.java:74-75
recordVoidStep(debugContext, "PARAM_VALIDATE", () ->
        validateParams(config.getRequestData(), requestData));
```

`validateParams` 的实现（`XmlEngine.java:328-340`）：

```java
for (ParamConfig param : requestDataConfig.getParams()) {
    if (param.isRequired() && !requestData.containsKey(param.getName())) {
        if (param.getDefaultValue() != null) {
            continue;                 // 有默认值则放过，由下游 SQL 自己处理
        }
        throw new RuntimeException("必填参数缺失: " + param.getName());
    }
}
```

**设计要点**：校验是"**白名单 + 必填 + 默认值回退**"的最小语义。注意它**不校验类型**——类型校验交给下游 SQL 的参数绑定和 executor 去兜，避免引擎层重复实现一套类型系统。

### 3.3 阶段 2：查询执行（QUERY_EXECUTE，DAG 编排）

```java
// XmlEngine.java:80-85
OrchestrationContext context = new OrchestrationContext(requestData);

Map<String, List<Map<String, Object>>> queryResults = recordStep(debugContext, "QUERY_EXECUTE", () ->
        queryOrchestrator.orchestrate(config.getQueries(),
                query -> executeQueryWithContext(query, context, debugContext)));
```

**WHY 用回调而不是直接传 queries**：`QueryOrchestrator.orchestrate` 接收的是 `Function<QueryConfig, List<Map<...>>> executeFunc`（`QueryOrchestrator.java:25-27`）——它只关心"**怎么决定并行/串行**"，不关心"**单条查询具体怎么执行**"。引擎把 `executeQueryWithContext` 作为回调注入，编排器拿到一张可执行的"任务表"。

`QueryOrchestrator`（`QueryOrchestrator.java`）的行为：

- **单查询快路径**（`QueryOrchestrator.java:33-39`）：只有一条 query 时直接同步执行，不进线程池——避免无谓的线程切换开销。
- **依赖校验**（`validateDependencies`，行 125-135）：`depends` 指向不存在的 query 立即抛异常。
- **循环检测**（`detectCycle`，行 137-150）：用 DFS 三色标记法（0=未访问 / 1=访问中 / 2=已完成），遇灰色节点即环，**启动期 fail-fast**，不会留到运行时死锁。
- **并行编排**（`buildFuture`，行 76-123）：无依赖的 query 用 `CompletableFuture.supplyAsync(..., executor)` 提交到线程池（core=4, max=8，`CallerRunsPolicy` 背压，行 17-23）；有依赖的用 `allOf(depFutures).thenApplyAsync(...)` 等待上游全部完成后才执行。线程池用 `CallerRunsPolicy` 而非 `AbortPolicy`——**宁可降级到调用线程跑，也不丢任务**，对查询场景更稳。

`executeQueryWithContext`（`XmlEngine.java:169-203`）在编排器线程里被调，它：

1. 从 `context` 取出 `previousResults`（上游结果的快照）；
2. 调 `executeQuery`（行 205-227）按 `type` 路由到 `executeSqlQuery` / `executeHttpQuery` / `executeDubboQuery` / `executeMongoQuery`；
3. 把本查询结果塞回 `context.putPreviousResult(...)`（行 184）——这就是"阶段内数据传递"的真正落点；
4. 若 `DebugContext` 非空，把 SQL/参数/分页模式/行数/耗时写入 `DebugTrace`（行 172-202），`finally` 里 `addTrace`。

**单查询执行细节（SQL 路径，`executeSqlQuery`，行 229-296）**：依次走 `DynamicSqlHandler`（处理 `<if>`/`<foreach>`/`#{}` 占位符）→ 可选的导出批次参数覆盖 → 可选的 `PaginationHandler.rewrite`（游标/优化分页改写）→ 路由到 `SqlExecutor.query(datasource, sql, params)`。这条链路是动态 SQL（功能 #3）和分页（独立特性）的入口，本文不展开。

**阶段输出**：`Map<String, List<Map<String,Object>>>`，key=queryId，value=该查询的原始行集（未做字段映射）。

### 3.4 阶段 3：结果映射（RESULT_MAP）

```java
// XmlEngine.java:88-107
Map<String, Object> mappedResults = new LinkedHashMap<>();
recordVoidStep(debugContext, "RESULT_MAP", () -> {
    for (ResultMapConfig resultMap : config.getResultMaps()) {
        List<Map<String, Object>> queryData = queryResults.get(resultMap.getQuery());
        if (queryData != null) {
            Object mapped = resultMapper.mapResult(queryData, resultMap);
            mappedResults.put(resultMap.getId(), mapped);
        }
    }
    if (config.getResultMaps().isEmpty()) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : queryResults.entrySet()) {
            List<Map<String, Object>> data = entry.getValue();
            if (data.size() == 1) {
                mappedResults.put(entry.getKey(), data.get(0));   // 单行压成 Map
            } else {
                mappedResults.put(entry.getKey(), data);          // 多行保持 List
            }
        }
    }
});
```

**两个 WHY**：

1. **为什么单行压成 Map、多行保留 List**（同样出现在 `OrchestrationContext.putPreviousResult` 与 `ResultMapper.mapResult`）：DSP 把"1 行 = 对象，N 行 = 列表"作为一条**贯穿全栈的隐式契约**。这样接口的"返回单对象还是列表"不用显式声明，由数据本身决定——配置极简，代价是调用方要自己判断类型。
2. **为什么有"无 resultMap 时自动兜底"分支**（行 97-106）：很多简单接口（如字典查询、ID 查详情）只想"原样把结果吐出去"，再让他们写 `<resultMap>` 是负担。引擎让 `<resultMap>` **可选**——没有就把原始 queryResults 按"单行/多行"规则直接搬进 `mappedResults`，下游 `<responseData>` 仍能按 queryId 引用。这是**配置最小化**原则的体现，对应模板 `template/15-no-resultmap-query.xml`。

有 `<resultMap>` 时走 `ResultMapper.mapResult`（`ResultMapper.java:14-43`）：逐行遍历，按 `column` 取原始值，按 `name` 写入新行，遇 `function` 则调 `applyFunction`（行 103-131）。函数调用走 `FunctionRegistry`（`FunctionRegistry.exists` / `invoke`），格式 `fn:FUNC_NAME,arg1,arg2`，第一参数是行数据值，后续是 XML 配置常量——这是内置函数（功能 #6）的衔接点。

`mapResult` 内部也复用了"单行压 Map、多行返回 List"的规则（`ResultMapper.java:39-42`）。

### 3.5 阶段 4：响应构建（RESPONSE_BUILD）

```java
// XmlEngine.java:110-111
Object responseData = recordStep(debugContext, "RESPONSE_BUILD", () ->
        resultMapper.buildResponse(config.getResponseData(), mappedResults));
```

`ResultMapper.buildResponse`（`ResultMapper.java:46-67`）的核心逻辑：

- 若 `<responseData>` 没有 `<field>`、只有 `resultMap="xxx"`（行 51-54）：直接把 `mappedResults.get(resultMapId)` 透传——这是模板 01 那种"单 resultMap 直接返回"场景。
- 否则构造一个 `LinkedHashMap`（**保序**），每个 `<field>` 按 `resolveFieldValue`（行 70-101）解析数据源、`applyFunction` 做函数转换、`applyAs`（行 139-155）按 `as="map"/"list"` 强制单对象或列表。

**`as` 属性的 WHY**：阶段 3 的"单行 Map / 多行 List"是隐式的，但有时调用方需要**强制**——比如一个查询恰好返回 1 行，但语义上是"列表"，配置 `as="list"` 会把单 Map 包回成 `[{...}]`；反之 `as="map"` 取列表首元素成单对象。这是对隐式契约的**显式覆盖出口**。

### 3.6 阶段后的收尾

```java
// XmlEngine.java:113-117
if (debugContext != null) {
    debugContext.setEndTimeMs(System.currentTimeMillis());
    debugContext.setTotalTimeMs(...);
    debugContext.setSuccess(true);
}
```

同样**只在调试路径**记录——生产路径完全不进这个分支。

### 3.7 零开销调试的实现：`recordStep` / `recordVoidStep`

这是"零开销调试"承诺的落点（`XmlEngine.java:124-153`）：

```java
private <T> T recordStep(DebugContext ctx, String name, Supplier<T> action) {
    if (ctx == null) {
        return action.get();          // 生产路径：直接执行，不计时、不构造对象
    }
    long start = System.currentTimeMillis();
    try {
        T result = action.get();
        ctx.addStep(DebugContext.DebugStep.success(name, System.currentTimeMillis() - start));
        return result;
    } catch (Exception e) {
        ctx.addStep(DebugContext.DebugStep.error(name, System.currentTimeMillis() - start, e.getMessage()));
        throw e;
    }
}
```

**WHY 这么设计而不是用 AOP/拦截器**：

- **无侵入**：阶段逻辑写在 lambda 里，调试开关只是一个 `if (ctx == null)` 短路，对业务零侵入；
- **真正零开销**：生产路径连 `System.currentTimeMillis()`、`new DebugStep(...)`、`addStep` 都不执行，只是多一次 null 比较；
- **不依赖代理**：AOP 方案要么走 CGLIB 代理（要 `@EnableAspectJAutoProxy`、final 类失效），要么走接口代理（`XmlEngine` 要拆接口）。这里用"传 ctx 参数 + 包 lambda"的方式，没有代理开销，也没有运行时反射。

**两套 `executeWithConfig` 入口的设计意图**（`XmlEngine.java:58-65`）：

| 入口 | 调用方 | 路径 | 调试开销 |
|---|---|---|---|
| `executeWithConfig(config, data)` | 生产数据查询 `DataApiController.java:54`（配合 `XmlConfigCacheManager.get(transno)` 拿缓存好的 `InterfaceConfig`） | `debugContext = null` | 零开销 |
| `executeWithConfig(config, data, debugContext)` | 管理后台调试 `InterfaceAdminController.java:359-365`（`new DebugContext(true)`） | 走 `recordStep` 计时分支 | 完整 trace |

`execute(String xml, data, debugContext)`（`XmlEngine.java:50-53`）则是"从 XML 字符串开始"的入口——先 `parse` 再走 `executeWithConfig`，调试路径用它（因为调试要拿用户刚改的 XML，不一定走缓存）。**生产路径选 `executeWithConfig` 而非 `execute`**，是因为生产侧已经用 `XmlConfigCacheManager` 把 XML 解析结果缓存住了（功能 #7），跳过解析能省一次 DOM4J 解析——这是双入口存在的根本原因。

---

## 4. 使用示例（端到端）

取最简单的模板 `template/01-simple-sql-query.xml`（根据用户 ID 查详情），配合生产调用链演示"一份 XML → 一个接口响应"。

### 4.1 XML 配置

```xml
<interface transno="USER_GET_BY_ID" name="根据ID查询用户" ...>
    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
    </requestData>

    <datasource name="ds_main" />

    <query id="q1" type="mysql" datasource="ds_main">
        SELECT id, user_name, email, phone, status, created_at, updated_at
        FROM users
        WHERE id = #{$requestData['userId']}
    </query>

    <resultMap id="userMap" query="q1">
        <field name="userId" column="id" />
        <field name="userName" column="user_name" />
        ...
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
    </resultMap>

    <responseData resultMap="userMap">
        <field name="user" as="map" />
    </responseData>
</interface>
```

### 4.2 调用方代码（生产路径）

```java
// DataApiController.java:54（dsp-data-service）
Object result = xmlEngine.executeWithConfig(
        xmlConfigCacheManager.get(transno),   // 命中缓存，拿 InterfaceConfig（跳过 parse）
        requestData);                          // 不传 DebugContext → 零开销
```

另一条入口 `DataQueryServiceImpl.execute`（`dsp-parent/dsp-engine/.../service/DataQueryServiceImpl.java:32-44`）走的是 `xmlEngine.execute(xmlConfig, requestData)`——从 XML 字符串开始，会触发 `XmlConfigParser.parse`，适合没缓存或调试场景。

### 4.3 引擎内部走一遍

假设 `requestData = {"userId": "U001"}`，DB 返回 1 行 `{id:U001, user_name:alice, ..., created_at:<Timestamp>}`：

| 阶段 | `recordStep` 名 | 输入 | 输出 |
|---|---|---|---|
| 预处理 | — | config, `{"userId":"U001"}`, `null` | 设 transno（调试路径才设 startTime） |
| 1 PARAM_VALIDATE | "PARAM_VALIDATE" | `ParamConfig(userId, required=true)` | `userId` 存在，通过 |
| 1.5（行 78） | — | `[DataSourceConfig(name=ds_main)]` | `registerInlineDataSources` 仅在引擎装配了 `DataSourceRegistrar` 时注册；生产 `ds_main` 已在管理后台配置，此处主要是校验/补注册 |
| 2 QUERY_EXECUTE | "QUERY_EXECUTE" | `[QueryConfig(q1, mysql, ds_main)]` | `{"q1": [ {id:U001,...} ]}`（单查询走快路径，不进线程池） |
| 3 RESULT_MAP | "RESULT_MAP" | 上面结果 + `userMap` | `{"userMap": {userId:U001, userName:alice, createdAt:"2025-01-02 03:04:05", ...}}`（单行压成 Map，`created_at` 经 `DATE_FORMAT` 函数转字符串） |
| 4 RESPONSE_BUILD | "RESPONSE_BUILD" | `ResponseDataConfig(resultMap=userMap, fields=[{name=user, as=map}])` | `{"user": {userId:U001, ...}}`（`as="map"` 强制取首元素成单对象，此处已是 Map，原样返回） |

最终 `DataApiController` 把 `result` 包进 `ApiResponse.success(...)` 返回给调用方。

### 4.4 多查询场景的差别

若 XML 里有多个 `<query>` 且声明了 `depends`（见 `template/10-dependency-orchestration.xml`、`template/09-parallel-orchestration.xml`），阶段 2 会进入 `QueryOrchestrator` 的 DAG 路径：无依赖的并行提交、有依赖的等上游 `CompletableFuture`。`executeQueryWithContext` 把每个上游结果经 `context.putPreviousResult` 暴露给下游，下游 SQL 用 `#{q1.userId}` 引用（`DynamicSqlHandler` 解析）。这部分细节属于 DAG 编排（功能 #2），本文点到为止。

---

## 5. 与其它部分的关系

`XmlEngine` 是"枢纽"，每个阶段都对接一个独立特性。学完引擎，下一步可以从这些衔接点切入：

| 引擎阶段/位置 | 对接的特性 | 衔接点（真实路径） |
|---|---|---|
| 阶段 2 调用 `queryOrchestrator.orchestrate` | **功能 #2 DAG 编排** | `QueryOrchestrator.java` 全文：依赖校验、DFS 环检测、CompletableFuture 并行；`XmlEngine.java:28`（字段注入）、`:80-85`（调用点） |
| `executeSqlQuery` 内 `dynamicSqlHandler.process` | **功能 #3 动态 SQL**（`<if>`/`<foreach>`/`#{}`） | `XmlEngine.java:233-234`；处理器 `DynamicSqlHandler.java` |
| `executeSqlQuery` 内 `paginationHandler.rewrite` | **分页特性**（cursor / optimized） | `XmlEngine.java:261-269`；`PaginationHandler.java` |
| `executeQuery` 按 `type` 路由 4 个 executor | **功能 #4 多源执行器路由** | `XmlEngine.java:205-227`；`SqlExecutor` / `HttpExecutor` / `DubboExecutor` / `MongoExecutor` |
| `registerInlineDataSources` + `DataSourceRegistrar` | **多数据源特性**（内联数据源运行时注册） | `XmlEngine.java:37-41, 155-167`；实现 `dsp-data-service/.../InlineDataSourceRegistrar.java` |
| `resultMapper.mapResult` / `applyFunction` | **功能 #6 内置函数库** | `ResultMapper.java:103-131`；`FunctionRegistry.java`（29 个函数） |
| `executeWithConfig(config, ...)` vs `execute(xml, ...)` | **功能 #7 XML 配置多级缓存** | 生产侧 `DataApiController.java:54` 配合 `XmlConfigCacheManager.get(transno)` 跳过解析；缓存模块 `engine.cache.XmlConfigCacheManager` |
| `DebugContext` 路径 | **接口调试特性**（管理后台） | `InterfaceAdminController.java:353-380` 的 `/debug` 端点 |

**为什么用组合（字段注入）而非把编排/映射写进 XmlEngine**（对应学习目标 6）：

看 `XmlEngine.java:21-31` 的字段声明——`XmlConfigParser`、`SqlExecutor`、`HttpExecutor`、`DubboExecutor`、`PaginationHandler`、`DynamicSqlHandler`、`ResultMapper`、`QueryOrchestrator` 全是 `@RequiredArgsConstructor` 注入的协作对象，`MongoExecutor` 还用了 `@Autowired(required = false)`（行 30-31）表达"可选依赖"。这是典型的"**组合优于继承 + 单一职责**"：

- **可替换**：每个协作对象都是 `@Component`，单测时可注入 mock；线上要换实现只改 Bean 配置。
- **可演进**：`MongoExecutor` 走可选注入，说明引擎允许"功能按需启用"——未来加 Elasticsearch 执行器也是同一模式，引擎主流程不动。
- **职责不膨胀**：若把 DAG 编排、字段映射、4 种协议执行都塞进 `XmlEngine`，这个类会变成几千行的上帝类；现在它只做"**编排四个阶段**"这一件事（约 120 行的 `executeWithConfig`），每个阶段的细节都委托出去。
- **`MongoExecutor` 的可选注入是关键信号**：它说明引擎有意把"是否支持某种数据源"从编译期下沉到运行期，平台可以按部署形态（带不带 Mongo）裁剪能力——这是组合模式带来的灵活性红利。

---

## 6. 延伸

### 6.1 下一步可深入的相关功能

按依赖顺序，建议学完本文后探索：

1. **DAG 查询编排（功能 #2）**：`QueryOrchestrator.java` 全文 + `template/09-parallel-orchestration.xml`、`template/10-dependency-orchestration.xml`。重点看 DFS 环检测（`detectCycle`）、`CompletableFuture.allOf` 串联、`CallerRunsPolicy` 背压。
2. **动态 SQL（功能 #3）**：`DynamicSqlHandler.java` + `template/02-dynamic-sql-query.xml`。重点看 SpEL 求值 `<if test>`、`<foreach>` 展开 `?` 占位符、`#{}` 参数化。
3. **多源执行器路由（功能 #4）**：`SqlExecutor`（Dynamic-DS 数据源切换）、`HttpExecutor`、`DubboExecutor`（泛化调用 + ReferenceConfig 缓存）、`MongoExecutor`。配套 `template/05~08`。
4. **内置函数库（功能 #6）**：`FunctionRegistry.java`，29 个函数的注册与调用约定。
5. **XML 配置多级缓存（功能 #7）**：`XmlConfigCacheManager` + `dsp-data-service` 的 `CacheRefreshScheduler`、`CacheLoadStrategy`，以及 `dsp-common` 的 `XmlConfigCacheInvalidator` 失效接口。

### 6.2 引擎可能的扩展点

从 `XmlEngine` 现有结构可以推断出的扩展位：

- **新增执行器类型**：在 `executeQuery` 的 `switch(type)`（`XmlEngine.java:211-226`）加新分支 + 在 `XmlConfigParser.parseQuery` 的类型分发（`XmlConfigParser.java:92-105`）加解析 + 新增一个 `XxxExecutor` Bean。可选依赖走 `@Autowired(required = false)` 模式。
- **新增内置函数**：往 `FunctionRegistry` 注册一个新函数即可，`ResultMapper.applyFunction` 自动识别 `fn:NAME` 调用——`<resultMap>` 和 `<responseData>` 两侧都能用。
- **新增阶段**：四阶段流水线目前是硬编码顺序，若要插一个新阶段（如结果脱敏），最干净的位置是在 `executeWithConfig` 里再加一个 `recordVoidStep(...)` 块；若想做得更灵活，可以把阶段抽象成 `List<Stage>` 顺序执行（当前未这样抽象，YAGNI）。
- **新增 `as` 类型**：`ResultMapper.applyAs`（`ResultMapper.java:139-155`）目前只认 `map` / `list`，要支持如 `count`、`first-value` 等可在这一处扩展。

### 6.3 待补 / 存疑

- `registerInlineDataSources`（`XmlEngine.java:155-167`）的实际注册行为依赖外部注入的 `DataSourceRegistrar` 实现（`dsp-data-service/.../InlineDataSourceRegistrar.java`）。本文未深入该实现的注册细节（向 Dynamic-DS 注册的具体 API），属于"多数据源特性"文档的职责，此处如实标注为待补。
- `executeSqlQuery` 中"导出批次模式"（`exportMode`，`XmlEngine.java:240-278`）的参数覆盖逻辑（`_exportPageSize` / `_exportPageNum` / `_exportLastId`）服务于在线/离线导出，与导出特性强耦合；生产数据查询路径在 `DataApiController.java:51` 已用 `removeIf(key -> key.startsWith("_export"))` 过滤掉这些参数，正常调用不会触发。完整语义留待导出特性文档展开。

---

**引用文件清单**（均为绝对/工程相对路径）：

- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/XmlEngine.java`
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/DebugContext.java`
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/parser/XmlConfigParser.java`
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/QueryOrchestrator.java`
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/executor/ResultMapper.java`
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/model/InterfaceConfig.java`
- `dsp-parent/dsp-engine/src/main/java/com/sunlc/dsp/engine/service/DataQueryServiceImpl.java`
- `dsp-parent/dsp-data-service/src/main/java/com/sunlc/dsp/dataservice/controller/DataApiController.java`
- `dsp-parent/dsp-admin-service/src/main/java/com/sunlc/dsp/adminservice/controller/InterfaceAdminController.java`
- `template/01-simple-sql-query.xml`、`template/15-no-resultmap-query.xml`、`template/09-parallel-orchestration.xml`、`template/10-dependency-orchestration.xml`
- `docs/engine-architecture.md`、`README.md`
