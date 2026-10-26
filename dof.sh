#!/usr/bin/env bash
set +x
set -euo pipefail
cd -- "$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v python3 >/dev/null || ! python3 -c 'import sys; assert sys.version_info >= (3,11)' 2>/dev/null; then
    echo '需要 Python 3.11+。可先从系统 APT 安装缺少的 Python，再继续当前操作。'
    if ! read -r -p '安装 Python？[y/N] ' answer; then
        echo '未安装依赖；请在交互终端运行 sudo bash dof.sh。' >&2
        exit 1
    fi
    case "$answer" in
        y|Y) bash scripts/container/bootstrap.sh --python ;;
        *) if [ "$#" -gt 0 ]; then exit 1; else exit 0; fi ;;
    esac
    python3 -c 'import sys; assert sys.version_info >= (3,11)' || {
        echo '仍未找到 Python 3.11+，请检查 Python 安装及 PATH。' >&2
        exit 1
    }
fi
if [ "$#" -eq 0 ]; then
    exec python3 -m scripts.container.menu
fi
exec python3 scripts/container/manage.py "$@"
