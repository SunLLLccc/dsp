# CI/CD 流程设计方案

## 概述

为 DSP 项目搭建基于 GitHub Actions 的 CI/CD 流水线，采用"GitHub Actions CI + 服务器端构建部署"方案。

## 需求摘要

- **CI 平台**：GitHub Actions（项目托管于 GitHub）
- **部署方式**：Docker 容器化部署（docker-compose 编排）
- **镜像分发**：无法推送到镜像仓库，采用服务器端构建
- **部署触发**：PR 合并到 main 自动 CI，部署由 GitHub Actions 自动执行
- **部署范围**：全部服务 — data-service、admin-service、offline-service、admin-web
- **数据库**：CI 用 Docker MySQL 跑集成测试，生产用外部数据库

## 整体流程

```
开发者 push PR → GitHub Actions CI（自动）
  ├── 后端编译 (mvn compile)
  ├── 后端测试 (mvn test，Docker MySQL)
  └── 前端构建验证 (npm run build)

PR 合并到 main → GitHub Actions CD（自动）
  ├── 构建后端 jar (mvn package -DskipTests)
  ├── 构建前端 (npm run build)
  ├── SCP 传输构建产物到服务器
  └── SSH 执行部署脚本 (docker-compose up -d)
```

## GitHub Actions 工作流

### CI 工作流 (`ci.yml`)

**触发条件**：PR 目标分支为 main

**Job: backend**
- 运行环境：ubuntu-latest
- 服务容器：MySQL 8.0（端口 3306）
- 步骤：
  1. checkout 代码
  2. 设置 JDK 8（actions/setup-java）
  3. Maven 缓存
  4. `mvn clean compile -pl dsp-parent`
  5. `mvn test -pl dsp-engine,dsp-core,dsp-data-service,dsp-admin-service,dsp-offline-service`
  6. 测试结果上报（可选）

**Job: frontend**
- 运行环境：ubuntu-latest
- 步骤：
  1. checkout 代码
  2. 设置 Node.js 18（actions/setup-node）
  3. `cd dsp-admin-web && npm ci`
  4. `npm run build`

### CD 工作流 (`deploy.yml`)

**触发条件**：push 到 main 分支

**Job: build-and-deploy**
- 运行环境：ubuntu-latest
- 步骤：
  1. checkout 代码
  2. 设置 JDK 8 + Maven 缓存
  3. `mvn clean package -DskipTests` — 构建 3 个 jar 包
  4. 设置 Node.js 18
  5. `cd dsp-admin-web && npm ci && npm run build` — 构建前端
  6. SCP 传输产物到服务器 `/opt/dsp/`
     - jar 包 → `/opt/dsp/jars/`
     - 前端 dist → `/opt/dsp/web/dist/`
     - Dockerfile、docker-compose.yml、nginx 配置（首次或有变更时）
  7. SSH 执行远程部署脚本：
     ```bash
     cd /opt/dsp
     docker-compose down
     docker-compose build
     docker-compose up -d
     ```

## Docker 配置

### Dockerfile.java（后端通用）

```dockerfile
FROM openjdk:8-jre-slim
ARG JAR_FILE
ARG PORT=8080
WORKDIR /app
COPY ${JAR_FILE} app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

每个后端服务使用同一个 Dockerfile，通过 docker-compose 的 build args 传入不同的 jar 和端口。

### Dockerfile.nginx（前端）

```dockerfile
FROM nginx:alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### nginx/default.conf

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Vue Router history 模式
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端代理
    location /dsp/admin/ {
        proxy_pass http://admin-service:8082/;
    }
    location /dsp/api/ {
        proxy_pass http://data-service:8080/;
    }
    location /dsp/offline/ {
        proxy_pass http://offline-service:8081/;
    }
}
```

### docker-compose.yml

```yaml
version: '3.8'
services:
  data-service:
    build:
      context: .
      dockerfile: Dockerfile.java
      args:
        JAR_FILE: jars/dsp-data-service.jar
        PORT: "8080"
    ports:
      - "8080:8080"
    env_file: .env
    restart: unless-stopped

  admin-service:
    build:
      context: .
      dockerfile: Dockerfile.java
      args:
        JAR_FILE: jars/dsp-admin-service.jar
        PORT: "8082"
    ports:
      - "8082:8082"
    env_file: .env
    restart: unless-stopped

  offline-service:
    build:
      context: .
      dockerfile: Dockerfile.java
      args:
        JAR_FILE: jars/dsp-offline-service.jar
        PORT: "8081"
    ports:
      - "8081:8081"
    env_file: .env
    restart: unless-stopped

  admin-web:
    build:
      context: .
      dockerfile: Dockerfile.nginx
    ports:
      - "80:80"
    depends_on:
      - admin-service
      - data-service
      - offline-service
    restart: unless-stopped
```

## 服务器部署目录

```
/opt/dsp/
├── docker-compose.yml
├── Dockerfile.java
├── Dockerfile.nginx
├── .env                    # 环境变量（不提交到 git）
├── nginx/
│   └── default.conf
├── jars/
│   ├── dsp-data-service.jar
│   ├── dsp-offline-service.jar
│   └── dsp-admin-service.jar
└── web/
    └── dist/               # 前端构建产物
```

## GitHub Secrets 配置

需要在 GitHub 仓库 Settings → Secrets and variables → Actions 中配置：

| Secret 名称 | 说明 |
|-------------|------|
| `SERVER_HOST` | 服务器 IP 或域名 |
| `SERVER_USER` | SSH 用户名 |
| `SERVER_SSH_KEY` | SSH 私钥 |
| `SERVER_DEPLOY_PATH` | 部署目录，默认 `/opt/dsp` |

服务器端 `.env` 文件需配置：

| 变量 | 说明 |
|------|------|
| `DB_HOST` | 数据库地址 |
| `DB_PORT` | 数据库端口 |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `DSP_JWT_SECRET` | JWT 密钥 |
| `DSP_SECURITY_ENCRYPT_KEY` | AES 加密密钥 |

## 需要新增/修改的文件

```
.github/workflows/ci.yml          # CI 工作流
.github/workflows/deploy.yml       # CD 工作流
deploy/Dockerfile.java             # 后端 Dockerfile
deploy/Dockerfile.nginx            # 前端 Dockerfile
deploy/docker-compose.yml          # 编排配置
deploy/nginx/default.conf          # Nginx 配置
deploy/deploy.sh                   # 远程部署脚本
```

## deploy.sh 远程部署脚本

```bash
#!/bin/bash
set -e

DEPLOY_PATH=${SERVER_DEPLOY_PATH:-/opt/dsp}
cd "$DEPLOY_PATH"

echo "=== 停止旧容器 ==="
docker-compose down

echo "=== 重新构建镜像 ==="
docker-compose build

echo "=== 启动服务 ==="
docker-compose up -d

echo "=== 清理悬空镜像 ==="
docker image prune -f

echo "=== 部署完成 ==="
docker-compose ps
```

## 依赖与前置条件

1. 服务器已安装 Docker + docker-compose
2. 服务器已安装 Java 8、Maven、Node.js（构建环境）
3. GitHub 仓库配置好 Secrets
4. 服务器创建好 `/opt/dsp/` 目录
5. 服务器配置好 SSH 公钥认证（用于 GitHub Actions SSH 登录）
6. 数据库已在服务器或外部可访问
