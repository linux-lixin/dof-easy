$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (Get-Command python -ErrorAction SilentlyContinue) {
        & python scripts/container/manage.py @args
    } elseif (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3 scripts/container/manage.py @args
    } else {
        throw '请先安装 Python 3.9+ 和 Docker Desktop（Linux 容器模式）。'
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
