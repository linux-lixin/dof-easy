# WPF 登录器开发与构建

用途与读者：Windows 客户端开发者。适用范围：dof / WPF。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[MainWindow.xaml.cs](../../clients/launcher/MainWindow.xaml.cs)；[README.md](../../clients/launcher/README.md)。限制和当前验收边界见[当前状态](../status/README.md)。


工作目录 `clients/launcher`，需要 Windows 与 .NET 10 Windows 桌面工具链；目标框架 `net10.0-windows7.0`。在该目录运行：

```powershell
dotnet restore
dotnet build -c Release
```

产物在 bin/Release 对应目标框架目录；发行所需配置与资源按[WPF 操作指南](../operations/wpf-client.md)准备。项目设置及发布参数以[项目文件](../../clients/launcher)为准。构建成功不证明所选等级进服和更新消费成功。

这是 dof 链的 `DNFLogin.exe`，不是原生 `dof-launcher.exe`。当前更新列表仍做字符串排序且不消费后台 SHA-256 元数据；修改更新路径后应单独验证排序、下载完整性与失败恢复。实例公钥与地址放私有配置，不能把完整客户端包提交仓库。
