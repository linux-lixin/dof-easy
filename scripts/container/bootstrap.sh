#!/usr/bin/env bash
# Only install missing packages from distro / Docker official signed APT repositories.
set +x
set -euo pipefail
export LC_ALL=C
fail() { echo "$*" >&2; exit 1; }
[[ $EUID == 0 ]] || fail '请使用 sudo bash dof.sh。'
[[ $(uname -m) == x86_64 && -d /run/systemd/system ]] || fail '需要 amd64 / systemd Linux。'
source /etc/os-release
case "$ID:$VERSION_ID" in ubuntu:24.04|ubuntu:26.04|debian:12|debian:13) ;; *) fail '支持 Ubuntu 24.04/26.04、Debian 12/13。';; esac
[[ -z ${DOCKER_HOST:-} && -z ${DOCKER_CONTEXT:-} ]] || fail '请取消远程 Docker 环境变量。'
install_missing() {
    local pending=() package
    for package in "$@"; do
        [[ $(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true) == 'install ok installed' ]] || pending+=("$package")
    done
    if ((${#pending[@]})); then
        apt-get update
        local simulation
        simulation=$(apt-get -s install -y --no-upgrade --no-install-recommends "${pending[@]}")
        if grep -Eq '^Remv |^Inst [^ ]+ \[' <<< "$simulation"; then
            fail '补齐依赖需要移除或升级现有包，请先人工处理；未执行安装。'
        fi
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-upgrade --no-install-recommends "${pending[@]}"
    fi
}
case "${1:-}" in
--python)
    install_missing python3
    python3 -c 'import sys; assert sys.version_info >= (3,11)' || fail '需要 Python 3.11+，不会替换系统 Python。'
    exit 0;;
--dependencies|--base) ;;
*) fail '需要 --python、--base 或 --dependencies';;
esac
install_missing python3 git ca-certificates curl gnupg iproute2 util-linux e2fsprogs
python3 -c 'import sys; assert sys.version_info >= (3,11)'
[[ $1 != --base ]] || exit 0
if command -v docker >/dev/null; then
    docker info >/dev/null || fail '现有 Docker 无法连接；请先检查服务，不会自动重启已有引擎。'
    missing=()
    if [[ $(dpkg-query -W -f='${Status}' docker-ce 2>/dev/null || true) == 'install ok installed' ]]; then
        docker compose version >/dev/null 2>&1 || missing+=(docker-compose-plugin)
        docker buildx version >/dev/null 2>&1 || missing+=(docker-buildx-plugin)
    elif [[ $(dpkg-query -W -f='${Status}' docker.io 2>/dev/null || true) == 'install ok installed' ]]; then
        docker compose version >/dev/null 2>&1 || missing+=(docker-compose-v2)
        docker buildx version >/dev/null 2>&1 || missing+=(docker-buildx)
    else
        docker compose version >/dev/null && docker buildx version >/dev/null || fail '已有 Docker 安装来源未知；请从原来源补齐 Compose/Buildx。'
    fi
    install_missing "${missing[@]}"
    docker compose version
    docker buildx version
    exit 0
fi
for package in docker.io docker-compose docker-compose-v2 docker-doc docker-buildx podman-docker containerd runc docker-ce docker-ce-cli; do
    [[ $(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true) != 'install ok installed' ]] || fail "发现冲突或残留包 $package，请先处理；未自动卸载。"
done
install -d -m 0755 /etc/apt/keyrings
key=/etc/apt/keyrings/dof-docker.asc
repo=/etc/apt/sources.list.d/dof-docker.sources
# Reuse only an exact matching managed source; do not overwrite user sources.
tmp=$(mktemp -d)
trap 'rm -rf -- "$tmp"' EXIT
curl --proto '=https' --tlsv1.2 -fsSL --retry 3 --connect-timeout 20 --max-time 120 "https://download.docker.com/linux/$ID/gpg" -o "$tmp/key"
fingerprint=$(gpg --batch --with-colons --import-options show-only --import "$tmp/key" 2>/dev/null | awk -F: '$1=="fpr" {print $10; exit}')
[[ $fingerprint == 9DC858229FC7DD38854AE2D88D81803C0EBFCD88 ]] || fail 'Docker APT 签名密钥指纹不匹配。'
cat > "$tmp/source" <<EOF
Types: deb
URIs: https://download.docker.com/linux/$ID
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: amd64
Signed-By: $key
EOF
[[ ! -L $key && ! -L $repo ]] || fail '拒绝覆盖源配置符号链接。'
[[ ! -e $repo ]] || cmp -s "$repo" "$tmp/source" || fail '已有源配置不同，未覆盖。'
install -m 0644 "$tmp/key" "$key"
install -m 0644 "$tmp/source" "$repo"
install_missing docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
docker compose version
docker buildx version
docker info >/dev/null
