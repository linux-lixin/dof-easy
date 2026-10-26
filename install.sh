#!/usr/bin/env bash
set +x
set -euo pipefail
cd -- "$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v python3 >/dev/null || ! python3 -c 'import sys; assert sys.version_info >= (3,11)' 2>/dev/null; then
    echo '菜单需要 Python 3.11+。先从系统 APT 安装缺少的 Python，随后进入安装配置菜单。'
    read -r -p '安装 Python？[y/N] ' answer
    [[ $answer == y ]] || exit 0
    bash scripts/container/bootstrap.sh --python
fi
exec python3 -m scripts.container.menu "$@"
