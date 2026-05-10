# CI/CD 流水线实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 DSP 项目搭建 GitHub Actions CI/CD 流水线，实现 PR 自动检查、合并自动构建、服务器端 Docker 部署。

**Architecture:** GitHub Actions 负责 CI（编译+测试）和 CD（构建产物+SCP传输+SSH部署）。服务器端通过 Docker Compose 编排 4 个容器（3 个后端 Java 服务 + 1 个 Nginx 前端）。后端服务共享同一个 Dockerfile，通过 build args 区分 jar 和端口。

**Tech Stack:** GitHub Actions, Docker, Docker Compose, Nginx, Maven, Node.js 18, JDK 8

**Spec:** `docs/superpowers/specs/2026-05-10-cicd-design.md`

---

### Task 1: 创建 Docker 配置文件

**Files:**
- Create: `deploy/Dockerfile.java`
- Create: `deploy/Dockerfile.nginx`
- Create: `deploy/nginx/default.conf`
- Create: `deploy/docker-compose.yml`
- Create: `deploy/.env.example`
- Create: `deploy/deploy.sh`

- [ ] **Step 1: 创建 deploy 目录**

```bash
mkdir -p deploy/nginx
```

- [ ] **Step 2: 创建 Dockerfile.java**

文件: `deploy/Dockerfile.java`

```dockerfile
FROM openjdk:8-jre-slim
ARG JAR_FILE
ARG PORT=8080
WORKDIR /app
COPY ${JAR_FILE} app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: 创建 Dockerfile.nginx**

文件: `deploy/Dockerfile.nginx`

```dockerfile
FROM nginx:alpine
COPY web/dist/ /usr/share/nginx/html/
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 4: 创建 nginx/default.conf**

文件: `deploy/nginx/default.conf`

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

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

- [ ] **Step 5: 创建 docker-compose.yml**

文件: `deploy/docker-compose.yml`

```yaml
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

- [ ] **Step 6: 创建 .env.example**

文件: `deploy/.env.example`

```bash
# 数据库配置
DSP_DB_HOST=host.docker.internal
DSP_DB_PORT=3306
DSP_DB_NAME=dsp_config
DSP_DB_USERNAME=root
DSP_DB_PASSWORD=your_db_password

# Redis 配置
DSP_REDIS_HOST=host.docker.internal

# JWT 密钥（生产环境必须更换）
DSP_JWT_SECRET=your-jwt-secret-key-must-be-at-least-32-characters-long

# AES 加密密钥
DSP_ENCRYPT_KEY=your_encrypt_key

# MongoDB（可选）
DSP_MONGO_URI=mongodb://host.docker.internal:27017/dsp
```

- [ ] **Step 7: 创建 deploy.sh**

文件: `deploy/deploy.sh`

```bash
#!/bin/bash
set -e

DEPLOY_PATH=${1:-/opt/dsp}
cd "$DEPLOY_PATH"

echo "=== 停止旧容器 ==="
docker compose down

echo "=== 重新构建镜像 ==="
docker compose build

echo "=== 启动服务 ==="
docker compose up -d

echo "=== 清理悬空镜像 ==="
docker image prune -f

echo "=== 部署完成 ==="
docker compose ps
```

- [ ] **Step 8: 赋予 deploy.sh 执行权限并提交**

```bash
chmod +x deploy/deploy.sh
git add deploy/
git commit -m "feat: 添加 Docker 部署配置文件"
```

---

### Task 2: 创建 CI 工作流

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: 创建 .github/workflows 目录**

```bash
mkdir -p .github/workflows
```

- [ ] **Step 2: 创建 ci.yml**

文件: `.github/workflows/ci.yml`

```yaml
name: CI

on:
  pull_request:
    branches: [main]

jobs:
  backend:
    name: Backend Build & Test
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: dsp_config
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping -h localhost"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 8
        uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
          cache: maven

      - name: Build backend
        run: mvn clean compile -f dsp-parent/pom.xml

      - name: Run tests
        run: mvn test -f dsp-parent/pom.xml
        env:
          DSP_DB_HOST: localhost
          DSP_DB_PORT: 3306
          DSP_DB_NAME: dsp_config
          DSP_DB_USERNAME: root
          DSP_DB_PASSWORD: root

  frontend:
    name: Frontend Build
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Install dependencies
        working-directory: dsp-admin-web
        run: npm install

      - name: Build
        working-directory: dsp-admin-web
        run: npm run build
```

- [ ] **Step 3: 提交**

```bash
git add .github/workflows/ci.yml
git commit -m "feat: 添加 CI 工作流（编译+测试+前端构建）"
```

---

### Task 3: 创建 CD 工作流

**Files:**
- Create: `.github/workflows/deploy.yml`

- [ ] **Step 1: 创建 deploy.yml**

文件: `.github/workflows/deploy.yml`

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    name: Build & Deploy
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 8
        uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
          cache: maven

      - name: Build backend JARs
        run: mvn clean package -DskipTests -f dsp-parent/pom.xml

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Build frontend
        working-directory: dsp-admin-web
        run: npm install && npm run build

      - name: Prepare deploy directory
        run: |
          mkdir -p staging/jars staging/web/dist staging/nginx
          cp dsp-parent/dsp-data-service/target/dsp-data-service-1.0.0.jar staging/jars/
          cp dsp-parent/dsp-offline-service/target/dsp-offline-service-1.0.0.jar staging/jars/
          cp dsp-parent/dsp-admin-service/target/dsp-admin-service-1.0.0.jar staging/jars/
          cp -r dsp-admin-web/dist/* staging/web/dist/
          cp deploy/Dockerfile.java staging/
          cp deploy/Dockerfile.nginx staging/
          cp deploy/docker-compose.yml staging/
          cp deploy/nginx/default.conf staging/nginx/
          cp deploy/deploy.sh staging/

      - name: Copy files to server
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          source: "staging/*"
          target: ${{ secrets.SERVER_DEPLOY_PATH || '/opt/dsp' }}
          strip_components: 1

      - name: Deploy on server
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            chmod +x ${{ secrets.SERVER_DEPLOY_PATH || '/opt/dsp' }}/deploy.sh
            bash ${{ secrets.SERVER_DEPLOY_PATH || '/opt/dsp' }}/deploy.sh ${{ secrets.SERVER_DEPLOY_PATH || '/opt/dsp' }}
```

- [ ] **Step 2: 提交**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat: 添加 CD 工作流（构建+SCP传输+SSH部署）"
```

---

### Task 4: 本地验证

- [ ] **Step 1: 验证后端编译**

```bash
cd dsp-parent && mvn clean package -DskipTests
```

Expected: BUILD SUCCESS，3 个 jar 包生成在各模块的 `target/` 目录下。

- [ ] **Step 2: 验证前端构建**

```bash
cd dsp-admin-web && npm install && npm run build
```

Expected: `dist/` 目录生成，包含 `index.html` 和静态资源。

- [ ] **Step 3: 本地 Docker 测试（可选）**

将构建产物复制到 deploy 目录，本地运行：

```bash
mkdir -p deploy/jars deploy/web/dist
cp dsp-parent/dsp-data-service/target/dsp-data-service-1.0.0.jar deploy/jars/
cp dsp-parent/dsp-offline-service/target/dsp-offline-service-1.0.0.jar deploy/jars/
cp dsp-parent/dsp-admin-service/target/dsp-admin-service-1.0.0.jar deploy/jars/
cp -r dsp-admin-web/dist/* deploy/web/dist/

# 复制 .env.example 为 .env 并填写实际值
cp deploy/.env.example deploy/.env

cd deploy
docker compose up -d
docker compose ps
```

Expected: 4 个容器运行，`http://localhost` 访问前端页面。
