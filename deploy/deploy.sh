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
