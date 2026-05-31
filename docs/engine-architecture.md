# 引擎架构与执行流程

## 总览流程

```
DataApiController → XmlConfigCacheManager → XmlEngine.execute()
    → XmlConfigParser（解析 XML 为 InterfaceConfig）
    → DynamicSqlHandler（处理 <if>/<foreach>/#{} 占位符）
    → PaginationHandler（游标分页或优化子查询分页）
    → QueryOrchestrator（CompletableFuture + DAG 依赖排序）
        → SqlExecutor / HttpExecutor / DubboExecutor / MongoExecutor
    → ResultMapper（字段映射 + 内置函数）
    → ApiResponse
```

## 详细执行流水线

1. **XML 解析**: `XmlConfigParser` 将 XML 字符串解析为 `InterfaceConfig`（含 `requestData`/`datasource`/`query`/`resultMap`/`responseData` 五部分）

2. **参数校验**: 必填参数校验，支持 `defaultValue` 回退

3. **DAG 编排**: `QueryOrchestrator` 分析 `<query depends="q1,q2">` 构建依赖图：
   - 无依赖的 query 并行提交到线程池（core=4, max=8）
   - 有依赖的 query 等待上游 `CompletableFuture` 完成后执行
   - 启动时 DFS 检测循环依赖，有环直接抛异常

4. **单查询执行** (`XmlEngine.executeQuery`):
   - `DynamicSqlHandler`: 用 SpEL 处理 `<if test="...">`（条件成立则追加 SQL）和 `<foreach>`（展开 ? 占位符），再将 `#{requestData.xxx}` 或 `#{queryId.col}` 替换为 `?` 参数化
   - `PaginationHandler`: 若配置了 `pagination="cursor"` 追加 `AND id > ? ORDER BY id LIMIT ?`；若 `pagination="optimized"` 用子查询 `WHERE id >= (SELECT id FROM t ... LIMIT ?, 1)` 改写
   - 路由到对应 executor（SQL/HTTP/Dubbo/Mongo）执行

5. **结果映射**: `ResultMapper` 按 `<resultMap>` 定义做 column→name 映射，支持 `fn:FUNC_NAME,arg1,arg2` 调用内置函数

6. **响应组装**: 按 `<responseData>` 将多个 resultMap 结果拼装为最终 JSON

## 各模块关键类

### engine.parser
- `XmlConfigParser` — DOM4J 将 XML 解析为 InterfaceConfig 模型树
- `DynamicSqlHandler` — `<if>`/`<foreach>` 处理 + `#{param}` → `?` 参数替换（SpEL）
- `PaginationHandler` — 分页改写：游标分页（cursor）+ 优化分页（optimized/子查询）

### engine.executor
- `SqlExecutor` — JDBC 查询，通过 Dynamic-DS 按 datasource name 路由数据源
- `HttpExecutor` — HTTP 调用（GET/POST + headers/body + responsePath 提取）
- `DubboExecutor` — Dubbo 泛化调用（GenericService.$invoke），ReferenceConfig 按 service:version:group 缓存
- `MongoExecutor` — MongoDB 查询（filter/projection/sort/limit/skip）
- `ResultMapper` — 字段映射 + fn: 函数调用

### engine.service
- `QueryOrchestrator` — CompletableFuture DAG 编排

### engine.cache
- `XmlConfigCacheManager` — 以 transno 为 key 缓存已解析的 XML 配置
- `CacheRefreshScheduler` — 每 5 分钟刷新全部活跃接口
- `XmlConfigCacheInvalidator` — 审批通过/发布/下线时立即失效
- `CacheLoadStrategy` — 缓存加载策略

### engine.model
- `InterfaceConfig`、查询/字段映射模型等配置 POJO

### engine.function
- `FunctionRegistry` — 内置函数注册表（DATE_FORMAT、CONCAT、NVL、JSON_EXTRACT 等）

## 缓存机制

- `XmlConfigCacheManager` 以 transno 为 key 缓存已解析的 XML 配置
- `CacheRefreshScheduler` 每 5 分钟刷新全部活跃接口
- `XmlConfigCacheInvalidator` 审批通过/发布/下线时立即失效
