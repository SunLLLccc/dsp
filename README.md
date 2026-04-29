# DSP 数据服务平台

数据服务基础设施平台，通过 XML 配置定义接口逻辑，实现"零代码"接口发布。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 8 + Spring Boot 2.7.18 + MyBatis-Plus + Dynamic-DS + Druid + DOM4J + EasyExcel + Dubbo 3.2 + Redis + JWT |
| 前端 | Vue 3.5 + Element Plus + Pinia + Vue Router 4 + Vite 8 + Axios |
| 包名 | com.fintechervision.dsp |

## 项目结构

```
DSP/
├── dsp/
│   ├── dsp-parent/               # Maven 父POM
│   │   ├── dsp-common/           # 公共模块 (jar)
│   │   ├── dsp-core/            # 核心代码模块 (jar)
│   │   ├── dsp-engine/          # 引擎模块 (jar)
│ │ ├── dsp-data-service/ # 数据服务 (可部署 jar, 端口=8080)
│ │ ├── dsp-offline-service/ # 离线导出服务 (可部署 jar, 端口=8081)
│ │ └── dsp-admin-service/ # 管理平台服务（可部署 jar，端口=8082）
│   └── dsp-admin-web/           # 前端项目 (Vue 3 + Vite)
└── CODEBUDDY.md
```

## 快速开始

### 后端

```bash
cd dsp/dsp-parent

# 编译整个项目
mvn clean compile

# 打包
mvn clean package

# 运行数据服务 (port=8080)
java -jar dsp-data-service/target/dsp-data-service-1.0.0.jar

# 运行离线导出服务 (端口=8081)
java -jar dsp-offline-service/target/dsp-offline-service-1.0.0.jar

# 运行管理平台服务 (端口=8082)
java -jar dsp-admin-service/target/dsp-admin-service-1.0.0.jar
```

### 前端

```bash
cd dsp/dsp-admin-web

# 安装依赖
npm install

# 开发模式 (Vite 热更新，代理到 localhost:8082)
npm 运行 dev

# 生产构建
npm 运行构建
```

## 核心特性

- **XML 配置化开发** — 通过 XML 定义接口逻辑，零代码发布接口
- **多数据源支持** — Dynamic-DS 动态数据源切换，支持 MySQL/HTTP/Dubbo
- **动态 SQL** — `<if>` 条件、`<foreach>` 循环、`#{}` 参数替换
- **查询并行编排** — CompletableFuture + DAG 依赖编排
- **分页查询** — 游标分页 + 优化分页自动改写
- **内置函数库** — 12个函数（日期/字符串/类型转换/JSON/条件判断等）
- **在线/离线导出** — 支持 XLSX/CSV/TXT 多格式
- **审批流程** — 接口发布需审批，支持提交/通过/驳回
- **JWT 鉴权** — 签名验证 + 过期校验 + 白名单 + 防重放

## 服务端点

| 服务 | 端口 | 路径前缀 | 说明 |
|------|------|----------|------|
| dsp-data-service | 8080 | `/dsp/api` | 数据查询统一入口 |
| dsp-admin-service | 8082 | `/dsp/admin` | 管理平台后台 |
| dsp-offline-service | 8081 | `/dsp/offline` | 离线导出服务 |

## 许可证

私有项目，未经授权禁止使用。
