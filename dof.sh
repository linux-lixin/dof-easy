#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
command -v python3 >/dev/null 2>&1 || { echo '请先安装 Python 3.9+ 和 Docker Compose v2。' >&2; exit 1; }
exec python3 scripts/container/manage.py "$@"
