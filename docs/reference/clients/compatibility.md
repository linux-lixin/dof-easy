# 客户端配套矩阵

用途与读者：客户端发行维护者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[MainWindow.xaml.cs](../../../clients/launcher/MainWindow.xaml.cs)；[client_reader.py](../../../scripts/dof/client_reader.py)；[README.md](../../../metadata/dof/README.md)。限制和当前验收边界见[当前状态](../../status/README.md)。


| 链路 | 客户端产物 | 配置/更新 | 不能混用的对象 |
|---|---|---|---|
| native | `dof-launcher.exe`、实际加载 `ijl15.dll` | 格式 2 配套；`dof-client.dll` 为配置容器 | WPF 的 DNFLogin.exe 和其更新元数据 |
| dof / WPF | Windows .NET 10 `DNFLogin.exe` | 自己的后台地址、公钥、更新列表 | 原生格式 2 配方读取端 |

原生配方 2.2 已实现且去掉强制版本字段；旧 2.1 真实读取证据只归旧版本。当前生成和离线校验不等于实际消费通过。详见[原生格式](../native/client-protocol.md)与[原生状态](../../status/native-client.md)。

后台 WPF 更新生成端已有数值版本与 SHA-256 元数据，但当前 WPF 仍按字符串排序，未消费 SHA-256；不能宣传端到端更新完整性或新格式等价。实际代码见 `MainWindow.xaml.cs` 的更新列表排序与下载路径。

发布前核对服务端模式、资源配方、客户端实际版本、密钥与实例 UUID，并在私有环境进行普通账号登录/选角/进图/返回/重登；不同等级另验。操作入口：[原生发布](../../operations/native-client-publication.md)、[WPF 指南](../../operations/wpf-client.md)。
