#!/bin/bash

set -e
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

# ================= 配置区域 =================
# 默认镜像仓库地址 (如果脚本运行时检测到是这个默认值，会提示你输入)
DEFAULT_REPO="your-docker-username/dnf-server"
IMAGE_REPO="$DEFAULT_REPO"
TAG="latest"

# 使用 Docker 默认平台，不构建多架构镜像。
# ===========================================

# 检查是否修改了镜像名，如果没有，则提示输入
if [ "$IMAGE_REPO" == "$DEFAULT_REPO" ]; then
    echo "========================================================"
    echo "错误: 你还没有配置镜像仓库地址！"
    echo "本脚本会构建并推送镜像到 Docker Hub 或私有仓库。"
    echo "--------------------------------------------------------"
    read -p "请输入你的镜像仓库地址 (例如: guoshengkai/dnf-server): " USER_INPUT_REPO

    if [ -z "$USER_INPUT_REPO" ]; then
        echo "错误: 输入不能为空。"
        exit 1
    fi
    IMAGE_REPO="$USER_INPUT_REPO"
fi

echo "准备使用 Docker 默认平台构建镜像"
echo "目标仓库: $IMAGE_REPO:$TAG"
echo "----------------------------------------------------"
echo "构建结果使用当前 Docker Builder 的默认架构。"
echo "----------------------------------------------------"

# 检查是否登录 (简单的检查，不一定准确)
# docker login ...

read -p "是否继续构建并推送到仓库? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo "已取消。"
    exit 1
fi

# 使用当前 Buildx Builder 构建并推送，不切换全局 Builder。
echo "开始构建并推送..."
# --push 会把构建好的镜像推送到远程仓库
docker buildx build \
  -f "$PROJECT_ROOT/services/admin/docker/Dockerfile" \
  -t "$IMAGE_REPO:$TAG" \
  --push \
  "$PROJECT_ROOT/services/admin"

if [ $? -eq 0 ]; then
    echo "=========================================="
    echo "构建并推送成功!"
    echo "同架构主机可使用以下命令拉取:"
    echo "docker pull $IMAGE_REPO:$TAG"
    echo "=========================================="
else
    echo "构建失败!"
fi
