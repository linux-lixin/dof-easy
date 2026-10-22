$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
# ================= 配置区域 =================
# 默认镜像仓库地址 (如果脚本运行时检测到是这个默认值，会提示你输入)
$DEFAULT_REPO = "your-docker-username/dnf-server"
$IMAGE_REPO = $DEFAULT_REPO
$TAG = "latest"

# 使用 Docker 默认平台构建后台镜像。
# ===========================================

# 检查是否修改了镜像名，如果没有，则提示输入
if ($IMAGE_REPO -eq $DEFAULT_REPO) {
    Write-Host "========================================================"
    Write-Host "错误: 你还没有配置镜像仓库地址！"
    Write-Host "本脚本会构建并推送镜像到 Docker Hub 或私有仓库。"
    Write-Host "--------------------------------------------------------"
    $USER_INPUT_REPO = Read-Host "请输入你的镜像仓库地址 (例如: guoshengkai/dnf-server)"

    if ([string]::IsNullOrEmpty($USER_INPUT_REPO)) {
        Write-Host "错误: 输入不能为空。"
        exit 1
    }

    # 检测用户输入是否已包含tag（含有冒号）
    if ($USER_INPUT_REPO -match ":") {
        # 用户输入已包含tag，直接使用
        $IMAGE_REPO = $USER_INPUT_REPO
        $TAG = ""  # 清空TAG，避免重复追加
    } else {
        # 用户只输入了仓库名，使用默认tag
        $IMAGE_REPO = $USER_INPUT_REPO
    }
}

Write-Host "准备使用 Docker 默认平台构建镜像"
if ([string]::IsNullOrEmpty($TAG)) {
    Write-Host "目标仓库: $IMAGE_REPO"
} else {
    Write-Host "目标仓库: ${IMAGE_REPO}:${TAG}"
}
Write-Host "----------------------------------------------------"
Write-Host "构建结果使用当前 Docker Builder 的默认架构。"
Write-Host "----------------------------------------------------"

# 确认是否继续
$REPLY = Read-Host "是否继续构建并推送到仓库? (y/n)"
if ($REPLY -ne "y" -and $REPLY -ne "Y") {
    Write-Host "已取消。"
    exit 1
}

# 使用当前 Buildx Builder 构建并推送，不切换全局 Builder。
Write-Host "开始构建并推送..."
# 构建完整的镜像tag
if ([string]::IsNullOrEmpty($TAG)) {
    $FULL_IMAGE_TAG = $IMAGE_REPO
} else {
    $FULL_IMAGE_TAG = "${IMAGE_REPO}:${TAG}"
}

# --push 会把构建好的镜像推送到远程仓库
# --no-cache 强制重新构建，避免使用缓存的旧层
docker buildx build `
  -f (Join-Path $ProjectRoot "services/admin/docker/Dockerfile") `
  -t "$FULL_IMAGE_TAG" `
  --no-cache `
  --push `
  (Join-Path $ProjectRoot "services/admin")

if ($LASTEXITCODE -eq 0) {
    Write-Host "=========================================="
    Write-Host "构建并推送成功!"
    Write-Host "同架构主机可使用以下命令拉取:"
    Write-Host "docker pull $FULL_IMAGE_TAG"
    Write-Host "=========================================="
} else {
    Write-Host "构建失败!"
    exit 1
}
