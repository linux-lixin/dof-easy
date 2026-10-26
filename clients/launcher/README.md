# Windows 登录器

此工程用于 `LAUNCHER_MODE=dof` 默认模式，产物为 `DNFLogin.exe`。它不是 `native` 原生登录器的新名读取端；原生配套状态见 [当前状态](../../docs/dof/status.md)。

本目录保留 WPF 工程 `DNFLogin.slnx` / `DNFLogin.csproj`，命名空间、资源名称和业务逻辑不因目录整理改变。
`DNFLogin/LoginConfig.cs` 处理本地登录器配置，UI 在各 `*Window.xaml` 及其 C# 文件中。
项目当前目标为 `net10.0-windows7.0`，需要匹配的 .NET 10 SDK 和 Windows 桌面构建环境。

在 Windows 仓库根目录构建：

```powershell
dotnet build clients/launcher/DNFLogin.csproj -c Release
```

依赖还原需要访问 NuGet。构建产物在本目录 `bin/`、`obj/`，不提交；发布配置位于 `Properties/PublishProfiles/`，
发布前应按实际目标检查路径，不把源码自带设置当作生产发行配置。

使用、注册、服务器地址和更新发布说明见 [登录器文档](../../docs/launcher/README.md)。
此次目录整理不代表已验证真实 Windows 客户端进游戏或自动更新。

## 服务器配置与记住密码

将本目录的 `login.ini.example` 复制为启动器同目录的 `login.ini`，在 `[Login]` 中填写自己的
`BaseUrl`（例如 `http://127.0.0.1:8080`；远程部署应使用自己的 HTTPS 地址）。示例故意留空，
启动器没有默认服务器，也不再读取其他目录的备用配置。地址缺失、重复、无效或文件不可读时，
更新、登录、注册界面显示配置错误，并停止网络请求。修复配置后重启启动器。
读取配置不会生成或重写文件；无效配置不会被自动修复或覆盖。

勾选“记住密码”后，密码由 Windows DPAPI 的当前用户范围加密，保存为 `PasswordDpapi`，
同时绑定当前服务器地址。换 Windows 用户、机器或服务器地址后须重新输入密码。
旧版的明文 `Password` 在有效且可写配置中会被删除，保留账号并要求手动重新输入密码；
旧密码从不自动填入或发送。配置无效或不可写时不会使用旧密码，也不会为清理密码而覆盖无效配置。
取消记住后登录成功会删除保存的密码。请不要分发含个人账号配置的 `login.ini`。

Windows 上可直接使用 PowerShell 7（.NET 10 运行时）执行配置与真实 DPAPI 回归测试：

```powershell
pwsh -NoProfile -File tests/unit/launcher/run-tests.ps1
python -m unittest discover -s tests/unit -p test_launcher.py -v
dotnet run --project tests/unit/launcher/wpf/Launcher.WpfSmoke.csproj -c Release
```

最后一项在 Windows 上实例化真实 WPF 窗口，验证缺失和无效配置时显示错误、不改写配置，且不发起 HTTP 请求。

生成无需预装 .NET 的 Windows x64 发布目录：

```powershell
dotnet publish clients/launcher/DNFLogin.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o clients/launcher/artifacts/win-x64
```

将 `login.ini.example` 和发布文件一起交付，由使用者填写实际地址；不要把构建环境或个人凭据打包。
