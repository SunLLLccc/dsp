# DSP XML 引擎 — 完整执行流程文档 (v2)

> 基于源码完整分析，覆盖从 HTTP 请求到响应的全链路。（v2：修正与代码不一致的内容）

---

## 1. 总体架构

DSP 引擎采用 **XML 配置驱动 + 多数据源执行 + DAG 并行编排** 的架构。外部请求经过鉴权、解析、编排执行、结果映射四个阶段，最终返回结构化 JSON。

```mermaid
graph TB
    Client["客户端"] -->|"POST /dsp/api/transno"| JWT["JwtAuthAspect<br/>JWT鉴权+时间戳+权限"]
    JWT -->|鉴权通过| AUDIT["AuditLogAspect<br/>审计日志"]
    AUDIT --> CTRL["DataApiController"]

    CTRL -->|获取config| CACHE["XmlConfigCacheManager<br/>ConcurrentHashMap缓存"]
    CACHE -.->|缓存未命中| PARSER["XmlConfigParser<br/>DOM4J解析XML"]
    PARSER -.->|InterfaceConfig| CACHE

    CTRL -->|executeWithConfig| ENGINE["XmlEngine<br/>顶层编排"]
    ENGINE --> VALIDATE["validateParams<br/>参数校验"]
    VALIDATE --> ORCH["QueryOrchestrator<br/>DAG编排"]

    ORCH --> SQL["SqlExecutor<br/>JDBC + Dynamic-DS"]
    ORCH --> HTTPX["HttpExecutor<br/>Hutool HTTP"]
    ORCH --> DUBBOX["DubboExecutor<br/>泛化调用"]
    ORCH --> MONGOX["MongoExecutor<br/>MongoTemplate"]

    SQL --> DYN["DynamicSqlHandler<br/>if/foreach/参数替换"]
    DYN --> PAGE["PaginationHandler<br/>游标/优化分页"]

    ORCH -->|查询结果| MAPPER["ResultMapper<br/>字段映射"]
    MAPPER --> FUNC["FunctionRegistry<br/>29个内置函数"]
    MAPPER -->|响应数据| CTRL

    SCHED["CacheRefreshScheduler<br/>定时刷新5min<br/>(dsp-data-service模块)"] -.-> CACHE
    INVALID["CacheInvalidator<br/>(dsp-common接口)<br/>审批/发布时失效"] -.-> CACHE

    style CACHE fill:#69c,color:#fff
    style ENGINE fill:#fc3,color:#333
    style ORCH fill:#fc3,color:#333
    style MAPPER fill:#69c,color:#fff
```

---

## 2. 请求处理全流程

一个完整的 API 请求 `POST /dsp/api/{transno}` 经过以下阶段：

```mermaid
sequenceDiagram
    participant C as 客户端
    participant JWT as JwtAuthAspect
    participant AUDIT as AuditLogAspect
    participant CTRL as DataApiController
    participant CACHE as CacheManager
    participant ENGINE as XmlEngine
    participant ORCH as QueryOrchestrator
    participant MAPPER as ResultMapper

    C->>JWT: POST /dsp/api/{transno}
    Note over JWT: 1.校验token非空<br/>2.校验timestamp(±5min)<br/>3.解析JWT claims<br/>4.校验appId匹配<br/>5.校验transno白名单

    JWT->>AUDIT: 鉴权通过
    Note over AUDIT: 记录startTime<br/>提取appId/IP/transno

    AUDIT->>CTRL: proceed()
    Note over CTRL: 校验head.transno == pathVariable

    CTRL->>CACHE: get(transno)
    alt 缓存命中
        CACHE-->>CTRL: InterfaceConfig
    else 缓存未命中
        CACHE->>CACHE: loadAndCache()<br/>DB查询XML → parse → cache
        CACHE-->>CTRL: InterfaceConfig
    end

    CTRL->>ENGINE: executeWithConfig(config, requestData)
    Note over ENGINE: 1.validateParams<br/>2.registerInlineDataSources

    ENGINE->>ORCH: orchestrate(queries, executeFunc)
    Note over ORCH: DAG编排: 并行/串行执行

    ORCH-->>ENGINE: Map<queryId, List<Map>>

    ENGINE->>MAPPER: mapResult() + buildResponse()
    MAPPER-->>ENGINE: 最终响应Object

    ENGINE-->>CTRL: result
    CTRL-->>AUDIT: ApiResponse.success()
    Note over AUDIT: finally: auditLogService.log()

    AUDIT-->>C: JSON响应
```

---

## 3. 鉴权阶段 (JwtAuthAspect)

通过 Spring AOP `@Before` 拦截 `DataApiController` 所有方法，执行顺序在审计日志之前。

```mermaid
flowchart TD
    START([请求到达]) --> CHECK_HEAD{"head为null?"}
    CHECK_HEAD -->|是| ERR1["throw TOKEN_MISSING"]
    CHECK_HEAD -->|否| EXTRACT["提取token, appId,<br/>timestamp, traceId"]

    EXTRACT --> SET_ATTR["设置request属性:<br/>traceId, appId"]
    SET_ATTR --> CHECK_TOKEN{"token为空?"}
    CHECK_TOKEN -->|是| ERR2["throw TOKEN_MISSING"]

    CHECK_TOKEN -->|否| CHECK_TS{"timestamp非空?"}
    CHECK_TS -->|是| VALIDATE_TS["validateTimestamp()<br/>解析ISO_LOCAL_DATE_TIME<br/>偏差>5min → TIMESTAMP_INVALID"]
    CHECK_TS -->|否| PARSE_JWT["jwtUtil.parseToken(token)"]
    VALIDATE_TS --> PARSE_JWT

    PARSE_JWT --> PARSE_OK{"解析成功?"}
    PARSE_OK -->|否| ERR3["throw TOKEN_EXPIRED"]
    PARSE_OK -->|是| CHECK_APPID{"claims.appId<br/>== request.appId?"}
    CHECK_APPID -->|否| ERR4["throw TOKEN_MISSING"]
    CHECK_APPID -->|是| CHECK_TRANSNO{"allowedTransnos<br/>含 * 或 当前transno?"}
    CHECK_TRANSNO -->|否| ERR5["throw ACCESS_DENIED"]
    CHECK_TRANSNO -->|是| PASS([鉴权通过])

    style ERR1 fill:#f66,color:#fff
    style ERR2 fill:#f66,color:#fff
    style ERR3 fill:#f66,color:#fff
    style ERR4 fill:#f66,color:#fff
    style ERR5 fill:#f66,color:#fff
    style PASS fill:#6c6,color:#fff
```

---

## 4. 缓存与 XML 解析

### 4.1 缓存机制

`XmlConfigCacheManager`（dsp-engine 模块）以 `transno` 为 key 缓存已解析的 `InterfaceConfig`，实现了 `XmlConfigCacheInvalidator` 接口（dsp-common 模块）。

```mermaid
flowchart LR
    subgraph "缓存读取 (get)"
        GET["get(transno)"] --> HIT{"cache命中?"}
        HIT -->|是| RETURN["返回InterfaceConfig"]
        HIT -->|否| LOAD["loadAndCache()"]
        LOAD --> DB["interfaceInfoService<br/>.getActiveXmlConfig(transno)"]
        DB --> PARSE["xmlConfigParser<br/>.parse(xmlContent)"]
        PARSE --> PUT["cache.put(transno, config)"]
        PUT --> RETURN
    end

    subgraph "缓存失效 (invalidate)"
        INV["invalidate(transno)"] --> REMOVE["cache.remove(transno)"]
    end

    subgraph "全量刷新 (refreshAll)"
        REF["refreshAll(activeTransnos)"] --> LOOP["遍历每个transno<br/>loadAndCache()"]
        LOOP --> CLEAN["cache.keySet()<br/>.retainAll(activeTransnos)<br/>清除下线接口"]
    end
```

> **注：** `CacheRefreshScheduler` 和 `CacheLoadStrategy` 位于 **dsp-data-service** 模块。定时刷新默认关闭，需配置 `dsp.cache.xml.refresh-enabled=true` 启用。

### 4.2 XML 解析流程

`XmlConfigParser` 使用 DOM4J 将 XML 字符串解析为 `InterfaceConfig` 模型树。

```mermaid
flowchart TD
    XML["XML字符串"] --> DOM4J["DocumentHelper.parseText()"]
    DOM4J --> ROOT["获取根元素 <interface>"]
    ROOT --> LOOP["遍历子标签"]

    LOOP --> RD{"requestData?"}
    RD -->|是| PRD["parseRequestData()<br/>→ RequestDataConfig<br/>  List&lt;ParamConfig&gt;"]

    LOOP --> DS{"datasource?"}
    DS -->|是| PDS["parseDataSource()<br/>→ DataSourceConfig<br/>  name"]

    LOOP --> Q{"query?"}
    Q -->|是| PQ["parseQuery()"]
    PQ --> QT{"query.type?"}
    QT -->|"mysql/doris/sql<br/>/oracle/postgresql"| SQL["parseSqlContent()<br/>解析SQL + <if>/<foreach>"]
    QT -->|http| HQ["parseHttpQuery()<br/>url/method/headers/body/responsePath"]
    QT -->|dubbo| DQ["parseDubboQuery()<br/>service/method/version/params"]
    QT -->|mongo| MQ["parseMongoQuery()<br/>collection/filter/projection/sort/limit/skip"]

    LOOP --> RM{"resultMap?"}
    RM -->|是| PRM["parseResultMap()<br/>→ List&lt;FieldMapping&gt;<br/>  source/name/function"]

    LOOP --> RSP{"responseData?"}
    RSP -->|是| PRSP["parseResponseData()<br/>→ List&lt;ResponseFieldConfig&gt;<br/>  name/mapTo/function/as"]

    PRD & PDS & SQL & HQ & DQ & MQ & PRM & PRSP --> CONFIG["InterfaceConfig<br/>├ requestData<br/>├ dataSources<br/>├ queries<br/>├ resultMap<br/>└ responseData"]

    style CONFIG fill:#69c,color:#fff
```

---

## 5. 引擎核心执行 (XmlEngine)

`XmlEngine.executeWithConfig()` 是引擎的顶层入口，协调五个阶段。

```mermaid
flowchart TD
    START(["executeWithConfig(config, requestData)"]) --> V["① validateParams()"]
    V --> V_DETAIL["遍历requestData中的param:<br/>required=true 且 value为null<br/>→ 有defaultValue则回退<br/>→ 无defaultValue则throw"]

    V_DETAIL --> R["② registerInlineDataSources()"]
    R --> R_DETAIL["config中有内联datasource<br/>→ dataSourceRegistrar.register()<br/>动态注册到Dynamic-DS"]

    R_DETAIL --> O["③ queryOrchestrator.orchestrate()"]
    O --> O_DETAIL["DAG编排执行所有query<br/>（详见第6节）"]
    O_DETAIL --> RESULT_MAP["Map&lt;queryId, List&lt;Map&gt;&gt;"]

    RESULT_MAP --> M["④ resultMapper.mapResult()"]
    M --> M_DETAIL["遍历每个query结果<br/>按resultMap做字段映射+函数<br/>（详见第8节）"]

    M_DETAIL --> B["⑤ resultMapper.buildResponse()"]
    B --> B_DETAIL["按responseData配置<br/>组装最终JSON结构"]
    B_DETAIL --> END(["返回响应Object"])

    style START fill:#69c,color:#fff
    style END fill:#6c6,color:#fff
```

---

## 6. DAG 编排 (QueryOrchestrator)

### 6.1 编排主流程

`QueryOrchestrator` 分析 `<query depends="q1,q2">` 依赖关系，构建 `CompletableFuture` DAG。

```mermaid
flowchart TD
    START(["orchestrate(queries, executeFunc)"]) --> EMPTY{"queries为空?"}
    EMPTY -->|是| RETURN_EMPTY["返回空Map"]
    EMPTY -->|否| SINGLE{"只有1个query?"}
    SINGLE -->|是| SYNC["同步直接执行<br/>返回单结果"]
    SINGLE -->|否| MULTI["多query DAG编排"]

    MULTI --> VALIDATE["validateDependencies()<br/>校验depends引用的queryId存在"]
    VALIDATE --> CYCLE["detectCycle()<br/>DFS三色标记检测循环"]
    CYCLE --> HAS_CYCLE{"有环?"}
    HAS_CYCLE -->|是| ERR["throw RuntimeException<br/>'检测到循环依赖'"]
    HAS_CYCLE -->|否| BUILD["为每个query构建CompletableFuture"]

    BUILD --> WAIT["CompletableFuture.allOf().get()"]
    WAIT --> COLLECT["收集结果到LinkedHashMap<br/>保持query顺序"]
    COLLECT --> END(["返回结果Map"])

    style ERR fill:#f66,color:#fff
    style END fill:#6c6,color:#fff
```

### 6.2 DAG 执行示意

以下是一个包含 4 个查询的 DAG 示例，展示并行与串行的执行时序：

```mermaid
gantt
    title DAG查询执行时序示例
    dateFormat X
    axisFormat %s

    section 并行执行
    q0 (无依赖)          : q0, 0, 100
    q2 (无依赖)          : q2, 0, 120

    section 等待依赖
    q1 (依赖q0)          : q1, 100, 200
    q3 (依赖q0,q1)       : q3, 200, 280
```

```mermaid
flowchart LR
    Q0["q0<br/>(无依赖)"] --> Q1["q1<br/>(依赖q0)"]
    Q2["q2<br/>(无依赖)"] --> Q3["q3<br/>(依赖q0,q1)"]
    Q0 --> Q3

    style Q0 fill:#69c,color:#fff
    style Q2 fill:#69c,color:#fff
    style Q1 fill:#fc3,color:#333
    style Q3 fill:#fc3,color:#333
```

### 6.3 循环检测（DFS 三色标记）

```mermaid
flowchart TD
    START(["遍历每个节点"]) --> COLOR0{"节点颜色?"}
    COLOR0 -->|"灰色(1)"| CYCLE["发现后向边<br/>→ 存在循环依赖"]
    COLOR0 -->|"黑色(2)"| SKIP["跳过(已完成)"]
    COLOR0 -->|"白色(0)"| GRAY["标记为灰色(访问中)"]

    GRAY --> NEIGHBORS["遍历所有邻居节点"]
    NEIGHBORS --> RECURSE["递归DFS"]
    RECURSE --> BLACK["标记为黑色(已完成)"]

    style CYCLE fill:#f66,color:#fff
    style BLACK fill:#6c6,color:#fff
```

---

## 7. 各类型查询执行

### 7.1 SQL 查询（mysql/doris/sql/oracle/postgresql）

```mermaid
flowchart TD
    START(["executeSqlQuery()"]) --> DYN["DynamicSqlHandler.process()"]

    subgraph "动态SQL处理"
        DYN --> CTX["buildContext()<br/>合并requestData + previousResults"]
        CTX --> ITER["遍历dynamicSqls"]
        ITER --> TYPE{"type?"}
        TYPE -->|IF| IF_PROC["processIf():<br/>test表达式 $→# 替换<br/>SpEL求值Boolean<br/>true→追加SQL"]
        TYPE -->|FOREACH| FE_PROC["processForeach():<br/>解析collection集合<br/>遍历拼接 open+?+separator+?+close<br/>收集参数值"]
        ITER --> REPLACE["replaceParameters():<br/>正则匹配 #{...}<br/>SpEL求值→替换为?占位符"]
    end

    REPLACE --> SQL_RESULT["SqlResult(sql, params)"]

    SQL_RESULT --> PAGE{"有分页配置?"}
    PAGE -->|否| EXEC
    PAGE -->|是| PG["PaginationHandler.rewrite()"]

    subgraph "分页改写"
        PG --> MODE{"pagination mode?"}
        MODE -->|CURSOR| CURSOR["游标分页:<br/>有lastId → AND {orderBy} > ?<br/>ORDER BY {orderBy} LIMIT ?"]
        MODE -->|OPTIMIZED| OPT["优化分页:<br/>offset=0 → ORDER BY + LIMIT<br/>offset>0 → 子查询延迟关联"]
    end

    CURSOR --> EXEC
    OPT --> EXEC

    EXEC["SqlExecutor.query()"] --> DS["DynamicDataSourceContextHolder<br/>.push(datasource)"]
    DS --> JDBC["jdbcTemplate<br/>.queryForList(sql, params)"]
    JDBC --> CLEAN["finally: .poll() 清理"]
    CLEAN --> END(["List&lt;Map&lt;String,Object&gt;&gt;"])

    style END fill:#6c6,color:#fff
```

### 7.2 HTTP 查询

```mermaid
flowchart TD
    START(["HttpExecutor.execute()"]) --> CTX["构建SpEL上下文"]
    CTX --> REPLACE["replaceExpressions():<br/>双轮替换<br/>① #\{$...} DSP格式<br/>② ${...} Spring格式"]

    REPLACE --> METHOD{"method?"}
    METHOD -->|POST| POST["HttpRequest.post(url)<br/>.timeout(10000)<br/>.body(body).execute()"]
    METHOD -->|GET| GET["HttpRequest.get(url)<br/>.timeout(10000)<br/>.execute()"]

    POST --> CHECK{"isOK?"}
    GET --> CHECK
    CHECK -->|否| ERR["throw RuntimeException"]
    CHECK -->|是| EXTRACT["extractData():<br/>按responsePath提取JSON子路径<br/>路径为空→返回全部"]

    EXTRACT --> NORMALIZE["normalizeToList():<br/>List→逐元素转换<br/>Map→单元素List<br/>其他→{value: data}"]
    NORMALIZE --> END(["List&lt;Map&gt;"])

    style ERR fill:#f66,color:#fff
    style END fill:#6c6,color:#fff
```

### 7.3 Dubbo 查询

```mermaid
flowchart TD
    START(["DubboExecutor.execute()"]) --> REG["getRegistryConfig():<br/>DB查询datasource<br/>解析extra_config JSON<br/>提取registry地址"]

    REG --> CTX["构建SpEL上下文"]
    CTX --> PARAMS["遍历dubboConfig.params:<br/>resolveParamValue() SpEL替换<br/>convertType() 类型转换"]

    PARAMS --> REF["getGenericService():<br/>cacheKey=service:version:group<br/>computeIfAbsent创建ReferenceConfig<br/>setGeneric(true), setCheck(false)"]

    REF --> INVOKE["genericService.$invoke(<br/>method, paramTypes, paramValues)"]
    INVOKE --> INVOKE_OK{"调用成功?"}
    INVOKE_OK -->|否| ERR["throw RuntimeException"]
    INVOKE_OK -->|是| NORMALIZE["normalizeToList()"]
    NORMALIZE --> END(["List&lt;Map&gt;"])

    style ERR fill:#f66,color:#fff
    style END fill:#6c6,color:#fff
```

### 7.4 MongoDB 查询

```mermaid
flowchart TD
    START(["MongoExecutor.execute()"]) --> CHECK{"mongoTemplate<br/>是否存在?"}
    CHECK -->|否| ERR["throw DATASOURCE_ERROR"]
    CHECK -->|是| RESOLVE["resolveParams():<br/>合并requestData + previousResults<br/>替换filter/projection/sort中的#{param}"]

    RESOLVE --> BUILD_Q["构建BasicQuery:<br/>filter非空→BasicQuery(filter)<br/>否则→BasicQuery('{}')"]
    BUILD_Q --> PROJ["projection非空?→设置投影"]
    PROJ --> SORT["sort非空?→设置排序"]
    SORT --> LIMIT["limit>0?→query.limit()"]
    LIMIT --> SKIP["skip>0?→query.skip()"]

    SKIP --> FIND["mongoTemplate.find(<br/>query, Map.class, collection)"]
    FIND --> CONVERT["转换key为String<br/>放入LinkedHashMap保持顺序"]
    CONVERT --> END(["List&lt;Map&lt;String,Object&gt;&gt;"])

    style ERR fill:#f66,color:#fff
    style END fill:#6c6,color:#fff
```

---

## 8. 结果映射与响应组装

### 8.1 字段映射 (ResultMapper.mapResult)

```mermaid
flowchart TD
    START(["mapResult(queryResult, resultMap)"]) --> EMPTY{"queryResult为空?"}
    EMPTY -->|是| EMPTY_RET["返回 Collections.emptyMap()"]
    EMPTY -->|否| HAS_FIELDS{"resultMap.fields<br/>为空?"}

    HAS_FIELDS -->|是| NO_MAP["无映射配置,原样返回:<br/>单行→返回Map<br/>多行→返回List&lt;Map&gt;"]
    HAS_FIELDS -->|否| MAPPING["遍历每行数据"]

    subgraph "字段映射循环"
        MAPPING --> FIELD_LOOP["遍历每个FieldMapping"]
        FIELD_LOOP --> GET_VAL["row.get(field.column)<br/>获取原始值"]
        GET_VAL --> HAS_FN{"有function?"}
        HAS_FN -->|是| APPLY["applyFunction():<br/>解析 fn:FUNC_NAME,arg1,arg2<br/>FunctionRegistry.invoke()"]
        HAS_FN -->|否| PUT["mappedRow.put(field.name, value)"]
        APPLY --> PUT
    end

    PUT --> RESULT{"结果行数?"}
    RESULT -->|"1行"| SINGLE["返回第一个mappedRow(Map)"]
    RESULT -->|"多行"| MULTI["返回mappedList(List&lt;Map&gt;)"]

    style EMPTY_RET fill:#999,color:#fff
    style SINGLE fill:#6c6,color:#fff
    style MULTI fill:#6c6,color:#fff
```

### 8.2 响应组装 (ResultMapper.buildResponse)

```mermaid
flowchart TD
    START(["buildResponse(config, mappedResults)"]) --> HAS_CONFIG{"config为null?"}
    HAS_CONFIG -->|是| DIRECT["直接返回mappedResults"]
    HAS_CONFIG -->|否| HAS_FIELDS{"fields为空?"}

    HAS_FIELDS -->|是| CHECK_RM{"resultMap非空?"}
    CHECK_RM -->|是| GET_RM["mappedResults.get(resultMap)"]
    CHECK_RM -->|否| EMPTY["返回空Map"]

    HAS_FIELDS -->|否| FIELD_LOOP["遍历ResponseFieldConfig"]

    subgraph "响应字段组装"
        FIELD_LOOP --> RESOLVE["resolveFieldValue()"]
        RESOLVE --> IS_MAPTO{"mapTo是resultMap引用?"}
        IS_MAPTO -->|是| GET_DATA["取整个resultMap数据"]
        IS_MAPTO -->|否| DEFAULT{"有默认resultMap?"}
        DEFAULT -->|是| FROM_DEF["从默认resultMap取子字段"]
        DEFAULT -->|否| NULL["值为null"]

        GET_DATA --> FN_CHECK{"有function?"}
        FROM_DEF --> FN_CHECK
        FN_CHECK -->|是| APPLY_FN["applyFunction()"]
        APPLY_FN --> AS["applyAs(field, value)<br/>as='map'→取首条为单对象<br/>as='list'或空→Map包装为List"]
        FN_CHECK -->|否| AS
        AS --> RESP_PUT["response.put(field.name, value)"]
    end

    RESP_PUT --> END(["返回组装后的响应Object"])

    style DIRECT fill:#69c,color:#fff
    style END fill:#6c6,color:#fff
```

### 8.3 内置函数 (FunctionRegistry)

共 **29** 个内置函数，通过 `fn:FUNC_NAME,arg1,arg2` 语法在 resultMap 中调用：

```mermaid
mindmap
  root((FunctionRegistry<br/>29个内置函数))
    日期
      DATE_FORMAT(date, pattern)
      DATE_ADD(date, days)
      DATE_SUB(date, days)
      WORKDAYS(start, end)
    字符串
      CONCAT(p1, p2, ...)
      CONCAT_WS(sep, p1, ...)
      SUBSTRING(str, start, end)
      TRIM / UPPER / LOWER
      LENGTH / REPLACE
      PAD_LEFT / PAD_RIGHT
      LIKE_MATCH / REGEX_MATCH
    空值处理
      NVL(p1, p2, ...)
      IFNULL(value, default)
      IFF(cond, true, false)
    类型转换
      TYPE_CONVERT(val, type)
      JSON_EXTRACT(json, path)
    聚合
      SUM / AVG
      COUNT / MAX / MIN
    数学
      ROUND / CEIL / FLOOR
```

---

## 9. 审计日志 (AuditLogAspect)

通过 `@Around` 环绕通知，在请求前后均执行，确保无论成功失败都记录审计日志。

```mermaid
flowchart TD
    START(["audit(JoinPoint)"]) --> RECORD_START["记录startTime"]
    RECORD_START --> EXTRACT_OP["提取操作类型:<br/>export方法→EXPORT<br/>其他→QUERY"]
    EXTRACT_OP --> EXTRACT_INFO["提取transno, appId,<br/>requestData"]
    EXTRACT_INFO --> GET_IP["获取客户端IP:<br/>X-Forwarded-For ><br/>X-Real-IP ><br/>getRemoteAddr()"]

    GET_IP --> PROCEED["joinPoint.proceed()<br/>执行目标方法"]
    PROCEED --> SUCCESS{"执行成功?"}
    SUCCESS -->|是| CODE_OK["responseCode = '0000'"]
    SUCCESS -->|否| CODE_ERR["responseCode = '5000'<br/>throw异常"]

    CODE_OK --> LOG["finally:<br/>auditLogService.log(<br/>appId, transno, operation,<br/>requestData, responseCode,<br/>costTime, ip)"]
    CODE_ERR --> LOG

    LOG --> LOG_OK{"日志记录成功?"}
    LOG_OK -->|否| WARN["log.warn(不影响主流程)"]
    LOG_OK -->|是| END([审计完成])
    WARN --> END

    style END fill:#6c6,color:#fff
```

---

## 10. 异常处理体系

```mermaid
flowchart TD
    START(["异常抛出"]) --> TYPE{"异常类型?"}

    TYPE -->|"BusinessException"| BE["GlobalExceptionHandler:<br/>提取ErrorCode<br/>ApiResponse.fail(code, msg)<br/>HTTP 200"]
    TYPE -->|"MethodArgumentNotValidException"| VE["参数校验异常:<br/>HTTP 400"]
    TYPE -->|"其他Exception"| UE["未知异常:<br/>HTTP 500<br/>ApiResponse.fail(5000, 系统异常)"]

    BE --> RESP["统一ApiResponse格式:<br/>{head: {transno, traceId,<br/>code, msg}, body: null}"]
    VE --> RESP
    UE --> RESP

    style BE fill:#fc3,color:#333
    style VE fill:#fc3,color:#333
    style UE fill:#f66,color:#fff
```

---

## 11. 统一报文格式

### 请求报文

```json
{
  "head": {
    "transno": "TRAN001",
    "appId": "app001",
    "token": "eyJhbGciOi...",
    "timestamp": "2026-05-07T10:30:00",
    "traceId": "uuid-xxx"
  },
  "requestData": {
    "param1": "value1",
    "ids": ["1", "2", "3"]
  }
}
```

### 响应报文

```json
{
  "head": {
    "transno": "TRAN001",
    "traceId": "uuid-xxx",
    "code": "0000",
    "msg": "成功"
  },
  "body": {
    "list": [...],
    "total": 100
  }
}
```

---

## 12. XML 配置完整示例

以下 XML 展示了引擎支持的所有特性：

```xml
<interface transno="TRAN001">
  <!-- 请求参数定义 -->
  <requestData>
    <param name="userId" type="string" required="true"/>
    <param name="status" type="string" required="false" defaultValue="1"/>
    <param name="ids" type="array" required="false"/>
    <param name="pageNo" type="integer" required="false" defaultValue="1"/>
    <param name="pageSize" type="integer" required="false" defaultValue="20"/>
  </requestData>

  <!-- 数据源引用（name指向数据库已配置的数据源） -->
  <datasource name="mysql_ds"/>

  <!-- 无依赖查询，可并行执行 -->
  <query id="q0" type="mysql" datasource="mysql_ds">
    <sql>SELECT id, name FROM users WHERE status = #{requestData.status}</sql>
  </query>

  <!-- 依赖q0，等待q0完成后执行 -->
  <query id="q1" type="mysql" datasource="mysql_ds" depends="q0">
    <sql>
      SELECT o.id, o.amount, o.create_time
      FROM orders o
      WHERE o.user_id = #{q0.id}
      AND status = #{requestData.status}
      <if test="requestData.ids != null">
        AND o.id IN
        <foreach collection="requestData.ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
      </if>
    </sql>
  </query>

  <!-- HTTP查询 -->
  <query id="q2" type="http" datasource="http_ds">
    <http url="http://api.internal/user/${requestData.userId}/detail" method="GET" responsePath="$.data"/>
  </query>

  <!-- 游标分页查询 -->
  <query id="q3" type="mysql" datasource="mysql_ds"
         pagination="cursor" order-by="id"
         page-size-param="pageSize" last-id-param="lastId"
         max-page-size="1000">
    <sql>SELECT * FROM logs WHERE user_id = #{requestData.userId}</sql>
  </query>

  <!-- 结果映射 -->
  <resultMap id="rm1" query="q1">
    <field column="id" name="orderId"/>
    <field column="amount" name="amount" function="fn:ROUND,2"/>
    <field column="create_time" name="createTime" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss"/>
  </resultMap>

  <resultMap id="rm2" query="q2">
    <field column="name" name="userName"/>
    <field column="avatar" name="avatarUrl" function="fn:IFNULL,https://default.avatar"/>
  </resultMap>

  <!-- 响应组装 -->
  <responseData resultMap="rm1">
    <field name="orders" mapTo="rm1" as="list"/>
    <field name="userInfo" mapTo="rm2" as="map"/>
    <field name="total" mapTo="rm3" function="fn:COUNT"/>
  </responseData>
</interface>
```

---

## 13. 执行阶段总览

| 阶段   | 组件              | 输入                       | 输出                        | 关键能力                                                    |
| ------ | ----------------- | -------------------------- | --------------------------- | ----------------------------------------------------------- |
| ① 鉴权 | JwtAuthAspect     | ApiRequest                 | —                           | JWT 校验、时间戳防重放、transno 白名单                      |
| ② 审计 | AuditLogAspect    | ProceedingJoinPoint        | —                           | @Around 环绕，记录请求/响应/耗时/IP                         |
| ③ 缓存 | CacheManager      | transno                    | InterfaceConfig             | ConcurrentHashMap 缓存，定时刷新，按需失效                  |
| ④ 解析 | XmlConfigParser   | XML 字符串                 | InterfaceConfig             | DOM4J 解析，支持 SQL/HTTP/Dubbo/Mongo 四种查询类型          |
| ⑤ 校验 | XmlEngine         | requestData                | —                           | 必填参数校验，defaultValue 回退                             |
| ⑥ 编排 | QueryOrchestrator | List\<QueryConfig\>        | Map\<queryId, List\<Map\>\> | DAG 依赖排序，CompletableFuture 并行，DFS 循环检测          |
| ⑦ 执行 | 各 Executor       | 查询配置+参数              | List\<Map\>                 | SQL(动态 SQL+分页)、HTTP(SpEL 替换)、Dubbo(泛化调用)、Mongo |
| ⑧ 映射 | ResultMapper      | 查询结果+resultMap         | 映射后数据                  | 字段重命名、fn:函数调用                                     |
| ⑨ 组装 | ResultMapper      | mappedResults+responseData | 最终响应 Object             | 多 resultMap 合并、嵌套结构                                 |
