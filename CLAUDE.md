# CLAUDE.md

## 1. 语言

Language=Chinese

## 2. 构建与运行

```bash
# 后端 (Maven, Java 8, Spring Boot 2.7.18)
cd dsp-parent
mvn clean compile                          # 编译全部模块
mvn clean compile -pl dsp-engine           # 编译单个模块
mvn clean compile -pl dsp-engine -am       # 编译单个模块及其依赖
mvn test                                   # 运行全部测试
mvn test -pl dsp-engine                    # 运行单个模块的测试
mvn test -pl dsp-engine -Dtest=XmlEngineTest  # 运行单个测试类
mvn clean package -DskipTests              # 打包（跳过测试）

# 启动各服务
java -jar dsp-data-service/target/dsp-data-service-1.0.0.jar       # 数据查询服务, 端口 8080
java -jar dsp-offline-service/target/dsp-offline-service-1.0.0.jar # 离线导出, 端口 8081
java -jar dsp-admin-service/target/dsp-admin-service-1.0.0.jar     # 管理后台, 端口 8082

# 前端 (Vue 3 + Vite)
cd dsp-admin-web
npm install       # 安装依赖
npm run dev       # 开发服务器 :3000，代理 /dsp/admin→:8082，/dsp/offline→:8081
npm run build     # 生产构建
```

## 3. 模块架构与依赖

包名统一为 `com.fintechervision.dsp`，由 `dsp-parent/pom.xml` 管理六个子模块。

**模块依赖关系：**

```
dsp-common（无依赖）
dsp-core   → dsp-common
dsp-engine → dsp-common, dsp-core
dsp-data-service    → dsp-common, dsp-core, dsp-engine
dsp-offline-service → dsp-common, dsp-core, dsp-engine
dsp-admin-service   → dsp-common, dsp-core, dsp-engine
```

**各模块结构：**

```
dsp-parent/
├── dsp-common/      共享模型 — 不被 Spring 组件扫描
│   model/           ApiRequest, ApiResponse, RequestHead, ResponseHead（统一报文模型）
│   enums/ErrorCode  TOKEN_MISSING, TOKEN_EXPIRED, TIMESTAMP_INVALID, ACCESS_DENIED
│   util/JwtUtil     HMAC-SHA256 签名/验签，支持过期时间和 allowedTransnos 白名单
│   service/DataQueryService  接口定义，解耦 data-service 与 engine 的直接依赖
│
├── dsp-core/        CRUD 层 — 实体、Mapper、Service
│   entity/          InterfaceInfo(接口), InterfaceVersion(版本), DatasourceConfig(数据源),
│                    AppAuth(应用授权), ApprovalRecord(审批), AuditLog(审计), ExportTask(导出)
│   mapper/          MyBatis-Plus BaseMapper
│   service/impl/    业务逻辑实现，如 InterfaceInfoService.getActiveXmlConfig() 获取当前生效的 XML 配置
│
├── dsp-engine/      XML 执行引擎 — 平台核心
│   parser/XmlConfigParser   DOM4J 将 XML 解析为 InterfaceConfig 模型树
│   XmlEngine                顶层编排: 解析 → 校验 → 执行 → 映射 → 响应
│   executor/
│   │   SqlExecutor          JDBC 查询，通过 Dynamic-DS 按 datasource name 路由数据源
│   │   HttpExecutor         HTTP 调用（GET/POST + headers/body + responsePath 提取）
│   │   DubboExecutor        Dubbo 泛化调用（GenericService.$invoke），ReferenceConfig 按 service:version:group 缓存
│   │   MongoExecutor        MongoDB 查询（filter/projection/sort/limit/skip）
│   │   DynamicSqlHandler    <if>/<foreach> 处理 + #{param} → ? 参数替换（使用 SpEL 引擎）
│   │   PaginationHandler    分页改写：游标分页（cursor）+ 优化分页（optimized/子查询）
│   │   QueryOrchestrator    CompletableFuture DAG 编排：无依赖并行执行，有依赖等待上游；DFS 循环检测
│   │   ResultMapper         字段映射 + fn: 函数调用
│   function/FunctionRegistry  内置函数的静态注册表
│   model/                  配置 POJO：InterfaceConfig, QueryConfig, DynamicSqlConfig 等
│
├── dsp-data-service/  数据查询 API (端口 8080)
│   controller/DataApiController  POST /dsp/api/{transno} — 获取活跃 XML → 引擎执行 → 返回结果
│   interceptor/JwtAuthAspect     JWT 校验 + 时间戳防重放（±5分钟）+ transno 白名单
│   interceptor/AuditLogAspect    请求/响应审计日志
│
├── dsp-admin-service/  管理后台 API (端口 8082)
│   controller/   InterfaceAdminController(接口 CRUD + 审批), DatasourceAdminController,
│                  AppAuthAdminController(JWT Token 生成), ExportAdminController, AuditLogController
│
└── dsp-offline-service/  离线导出 (端口 8081)
    controller/OfflineExportController  异步导出 XLSX/CSV/TXT，轮询下载
```

## 4. 引擎架构与执行流程

**总览流程：**

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

**详细执行流水线：**

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

**各包的关键类：**
- `engine.parser` — `XmlConfigParser`、`DynamicSqlHandler`、`PaginationHandler`
- `engine.executor` — `SqlExecutor`、`HttpExecutor`、`DubboExecutor`、`MongoExecutor`
- `engine.service` — `QueryOrchestrator`
- `engine.cache` — `XmlConfigCacheManager`、`CacheRefreshScheduler`、`CacheLoadStrategy`
- `engine.model` — `InterfaceConfig`、查询/字段映射模型
- `engine.function` — 内置函数注册表（DATE_FORMAT、CONCAT、NVL、JSON_EXTRACT 等）

## 5. XML 配置格式

`template/` 目录下有覆盖所有场景的模板。核心结构：

```xml
<interface transno="xxx">
  <request>  <param name="xxx" type="string" required="true"/> </request>
  <queries>
    <query id="q1" type="mysql" datasource="ds1" depends="q0">  <!-- depends 用于 DAG 依赖排序 -->
      <sql>SELECT ... WHERE col = #{paramName} AND <if test="param != null">...</if>
        <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
      </sql>
    </query>
  </queries>
  <result-map>  <!-- 字段映射，支持 function/alias/format -->
    <field source="q1.col_a" alias="userName" function="UPPER"/>
  </result-map>
</interface>
```

**DSL 关键语法：**

- **参数引用**: `#{requestData.paramName}`（请求参数）、`#{queryId.columnName}`（上游查询结果）
- **动态 SQL**: `<if test="...">...`、`<foreach collection="#{requestData.ids}" item="id" separator="," open="(" close=")">#{id}</foreach>`
- **SpEL 注意**: XML 中用 `$` 前缀（如 `#{requestData.xxx}`），`DynamicSqlHandler` 内部将 `$` 替换为 `#` 后交给 SpEL 解析
- **分页**: `<query pagination="cursor" order-by="id" page-size-param="pageSize" last-id-param="lastId" max-page-size="1000">` 或 `pagination="optimized"`（子查询分页）
- **数据源类型**: `mysql`/`doris`/`sql`/`oracle`/`postgresql`（走 SqlExecutor）、`http`（HttpExecutor）、`dubbo`（DubboExecutor 泛化调用）、`mongo`（MongoExecutor）
- **函数**: `fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss`、`fn:JSON_EXTRACT,data.status`、`fn:NVL,默认值`、`fn:IFF,条件,true值,false值`

## 6. 缓存机制

- `XmlConfigCacheManager` 以 transno 为 key 缓存已解析的 XML 配置
- `CacheRefreshScheduler` 每 5 分钟刷新全部活跃接口
- `XmlConfigCacheInvalidator` 审批通过/发布/下线时立即失效

## 7. 数据库

初始化脚本: `dsp-parent/dsp-core/src/main/resources/db/init.sql`

核心表: `interface_info`（接口定义）、`interface_version`（XML 版本，status: 待审批→已通过→已发布）、`approval_record`（审批记录）、`datasource_config`（Dynamic-DS 数据源，包含 HTTP/Dubbo 等非 JDBC 类型，extra_config 存 JSON）、`app_auth`（应用密钥）、`audit_log`（审计日志）、`export_task`（导出任务）

所有表使用逻辑删除（`deleted` 字段），MyBatis-Plus 自动追加 `deleted=0` 条件。

## 8. 前端路由

Vue 3 SPA，路由懒加载：`/interface`（接口列表）、`/interface/edit/:id?`（新建/编辑）、`/interface/debug`（调试）、`/datasource`（数据源管理）、`/appauth`（应用授权）、`/export`（导出管理）。默认重定向到 `/interface`。

## 9. 技术栈

- Java 8、Spring Boot 2.7.18、MyBatis-Plus 3.5.5（逻辑删除字段 `deleted`，驼峰命名自动转换）
- Dynamic-DS 3.6.1 实现多数据源切换
- DOM4J 解析 XML、Druid 连接池
- JWT（jjwt 0.11.5）、Dubbo 3.2.11、EasyExcel 3.3.4
- 前端：Vue 3 + Element Plus + Pinia + Vite

## 10. 关键约定

- 数据源密码以 AES 加密的 `ENC(base64)` 格式存储，密钥配置在 `dsp.security.encrypt-key`
- JWT 密钥通过 `${DSP_JWT_SECRET:默认值}` 环境变量设置，生产环境必须更换
- 错误码定义在 `ErrorCode` 枚举中（0000=成功、4xxx=客户端错误、5xxx=服务端错误）
- 请求/响应统一使用 `ApiRequest<T>` / `ApiResponse<T>` 包装，标准化的 head/body 结构
- 审计日志通过 AOP 切面（`AuditLogAspect`）记录
- 三个服务均通过 JWT 拦截器/切面进行鉴权
- Dubbo 数据源通过 `datasource_config.extra_config` JSON 中的 `registry` 字段指定注册中心地址
- Dynamic-DS 的 datasource name 来自 `datasource_config.ds_name`，运行时可通过 `DatasourceInitRunner` 动态注册

## 11. 开发规范

### 11.1 分支策略

| 分支 | 用途 | 创建来源 | 合并目标 | 保护规则 |
|------|------|---------|---------|---------|
| `main` | 生产分支 | — | — | 禁止 push，仅接受 PR，使用 Squash and Merge |
| `feature/<type>-<desc>-<date>` | 功能开发 | `main` | `main` | 无 |
| `hotfix/<desc>-<date>` | 紧急线上修复 | `main` | `main` | 合并后必须打 tag |
| `release/<version>` | 发布候选 | `main` | `main` | 冻结功能，仅修 bug |

`type` = `feat` | `fix` | `refactor` | `docs` | `perf`

示例：`feature-feat-approval-flow-20260430`、`hotfix-jwt-expiry-20260501`、`release/v1.1.0`

### 11.2 提交规范

Commit Message 格式：`<type>: <中文描述>`

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat: 新增审批管理页面` |
| `fix` | Bug 修复 | `fix: 修复分页参数解析异常` |
| `docs` | 文档变更 | `docs: 更新README缓存架构说明` |
| `style` | 代码格式（不影响逻辑） | `style: 统一缩进为4空格` |
| `refactor` | 重构 | `refactor: 拆分XmlEngine职责` |
| `perf` | 性能优化 | `perf: 优化ResultMapper字段映射` |
| `test` | 测试相关 | `test: 添加QueryOrchestrator单元测试` |
| `chore` | 构建/工具/配置变更 | `chore: 升级Spring Boot至2.7.18` |
| `build` | CI/CD 变更 | `build: 添加PR模板` |

PR 标题与 commit message 格式一致。

### 11.3 代码审查

**PR 前置条件**：
- PR 描述填写完整（变更类型 checklist + 影响范围 + 测试验证 + 风险说明）
- 至少 1 人 review 批准
- 无未解决的 review 评论
- 编译通过 + 测试通过

**审查清单**：
- 功能正确性：实现是否满足需求，逻辑是否正确
- 代码规范：命名、缩进、注释是否符合项目约定
- 安全性：是否有 SQL 注入、XSS、硬编码密钥等问题
- 性能影响：是否有不必要的全表扫描、循环、重复计算
- 测试覆盖：核心逻辑是否有测试，边界条件是否覆盖
- 兼容性：是否影响已有接口、配置、数据库结构

**PR 大小控制**：单个 PR 变更控制在 400 行以内，大型功能拆分为多个 PR。

### 11.4 版本管理

采用 Semantic Versioning：`vMAJOR.MINOR.PATCH`
- MAJOR：不兼容的 API 变更（如 XML DSL 语法升级）
- MINOR：向后兼容的新功能（如新增 Dubbo 支持）
- PATCH：向后兼容的 Bug 修复

发布流程：创建 `release/vX.Y.Z` 分支 → 冻结功能 → 修复问题 → 打 tag → 合并回 `main` → 生成 CHANGELOG

## 12. 注意事项

- 修改引擎逻辑需充分手动验证
- JWT secret 和 DB 密码硬编码在 `application.yml` 中，生产部署需外置配置
