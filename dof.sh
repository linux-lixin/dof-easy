#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ "$#" -eq 0 ]; then
    exec bash ./install.sh
fi
command -v python3 >/dev/null 2>&1 || { echo '请先运行 bash install.sh 安装依赖。' >&2; exit 1; }
exec python3 scripts/container/manage.py "$@"
