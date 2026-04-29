# DSP 数据服务平台

DSP（Data Service Platform）是公司级统一数据服务基础设施平台，通过 XML 配置定义接口逻辑，实现"零代码"接口发布。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 8 + Spring Boot 2.7.18 + MyBatis-Plus + Dynamic-DS + Druid + DOM4J + EasyExcel + Dubbo 3.2 + Redis + JWT |
| 前端 | Vue 3.5 + Element Plus + Pinia + Vue Router 4 + Vite 8 + Axios |
| 包名 | com.fintechervision.dsp |

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

- JDK 8+
- Maven 3.6+
- Node.js 16+
- MySQL 5.7+
- Redis 5.0+

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

- **XML 配置化开发** — 通过 XML 定义接口逻辑，零代码发布接口
- **多数据源支持** — MySQL / Doris / PostgreSQL / HTTP / Dubbo / MongoDB
- **动态 SQL** — `<if>` 条件判断、`<foreach>` 集合遍历、`#{}` 参数替换
- **查询并行编排** — CompletableFuture + DAG 依赖编排，支持并行/串行/混合模式
- **分页查询** — 游标分页（cursor）+ 深分页优化（子查询改写）
- **内置函数库** — 29个内置函数（日期/字符串/类型转换/JSON/空值/条件/聚合/数学）
- **在线/离线导出** — 支持 XLSX / CSV / TXT 多格式导出
- **审批流程** — 接口版本发布支持提交审批、通过、驳回流程
- **JWT 鉴权** — 签名验证 + 过期校验 + 白名单 + 防重放
- **应用授权** — AppId/AppSecret 管理，接口级别授权控制

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

### 支持的查询类型

| 类型 | 执行器 | 说明 |
|------|--------|------|
| mysql / doris / sql / oracle / postgresql | SqlExecutor | SQL 查询，Dynamic-DS 数据源切换 |
| http | HttpExecutor | HTTP 外部接口调用（GET/POST） |
| dubbo | DubboExecutor | Dubbo 泛化调用 |
| mongo | MongoExecutor | MongoDB 查询 |

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

## 配置说明

- **MyBatis-Plus** — 逻辑删除字段 `deleted`，自动驼峰命名转换
- **JWT 密钥** — 配置在 `dsp.jwt.secret`（生产环境必须更换）
- **前端代理** — Vite 将 `/dsp` 请求代理到 `localhost:8082`
- **导出目录** — 离线导出文件存储在 `./export-files`

## 许可证

私有项目，未经授权禁止使用。
