# 环境与材料准备

用途与读者：首次安装者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[dof.sh](../../dof.sh)；[manage.py](../../scripts/container/manage.py)；[build-inputs.lock.json](../../metadata/build-inputs.lock.json)。限制和当前验收边界见[当前状态](../status/README.md)。


1. 游戏部署必须在 Linux x86_64 主机进行；Ubuntu 24.04/26.04、Debian 12/13 的差异见[平台参考](../reference/platform-compatibility.md)。Windows 工作区可写代码和构建 WPF，不能直接运行 Linux 安装器。
2. 准备 Docker Engine、Compose、Buildx、Python 3.11+。菜单可询问安装缺失依赖；无外网环境先准备离线资源和镜像。两个频道建议至少 8 GiB 内存，Swap 不能替代性能验证。安装确认页检查磁盘预算。
3. 自行提供对应等级完整 `.pvf` 和客户端，文件至少通过安装器基础检查。公共构建输入 424 项、游戏资源锁 392 项、等级包 13 项；[资源参考](../reference/resources.md)解释来源与缓存。等级程序按锁从 Release 下载，PVF 不提供。
4. 选择[登录模式](login-modes.md)。原生派生资源已入库；只有重新派生时才需要私有固定原件。不要将 WPF 文件作为原生新格式读取器。
5. 选择镜像来源与数据库：菜单默认本机构建；高级命令默认拉取自己配置的同版本镜像。外置数据库要求同机私网 MySQL 5.6、管理员 0600 配置和专用 Docker 网段，见[外置 MySQL](../operations/external-mysql.md)。
6. 准备客户端可达 IPv4 和安全组规则。先用[端口参考](../reference/ports.md)对应自己的频道生成清单；菜单和高级命令默认频道 15,52，手动模板示例 11,52。

成功信号：材料来源、登录模式、网络、数据库、镜像版本、可用磁盘都明确；下一步[首次启动](quickstart.md)。材料不齐时停止安装并补齐，不重用另一实例的密钥或数据。
