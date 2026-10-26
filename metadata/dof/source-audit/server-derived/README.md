# 正式载荷派生证据

`derivation.json` 和 `NOTICES.txt` 从原正式资源包的 `source-audit/` 原样归档，字节未变。部署载荷位于 `services/game/resources/native/`，仍受原 `runtime.lock.json` 约束。

维护者可用原件重新派生并比较载荷。`package_server.py` 和 `stage_server_probe.py` 对仓库正式目录读取这里的证据；对独立导入目录要求该目录自带 `source-audit/derivation.json`，不会借用其他版本证据。
