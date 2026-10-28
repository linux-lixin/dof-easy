# 原生客户端实现与验收边界

用途与读者：原生客户端维护与验收者。适用范围：native 格式 2 / 配方 2.2。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[client_reader.py](../../scripts/dof/client_reader.py)；[README.md](../../services/game/resources/native/README.md)；[reader-validation.md](../history/clients/reader-validation.md)。限制和当前验收边界见[当前状态](README.md)。


当前代码：格式 2，配方 `dof-launcher-reader-2.2`，取消强制 `token_dof_version=2` 字段。派生登录器 SHA-256 为 `da078f1883a83c64438fdaf96d7147770254d7b63310f74a5c31d5bd512b2554`，派生加载 DLL 为 `dd5b3a9be419b910fb82f01d432fdf9a12d374d491c554e10103ba345942f66b`。锁定义与源码见来源，操作见[发布指南](../operations/native-client-publication.md)。

已有旧配方 2.1 的部分真实 Windows 读取与连接记录；不能套用到 2.2。当前源代码和离线格式校验不等于实际消费通过。`dof-client.dll` 是配置容器，实际加载者为 `ijl15.dll`。

待验：当前配方的新名读取、完整字段消费、加载 DLL、普通账号登录/选角/进城/进图/返回/重登、广播、随机盒、P2P 与团本。原生自动更新和压缩私有参数也需独立证据。缺私有客户端材料时应记未执行，不能用 WPF 或静态校验替代。

原格式与批次记录见[重组前协议](../history/clients/protocol-before-format2.md)，原证据见[验收索引](../validation/README.md)。本轮未重新运行 Windows 客户端。
