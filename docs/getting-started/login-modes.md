# 选择登录模式

用途与读者：安装者与客户端维护者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[manage.py](../../scripts/container/manage.py)；[menu.py](../../scripts/container/menu.py)；[README.md](../../clients/launcher/README.md)。限制和当前验收边界见[当前状态](../status/README.md)。


| 模式 | 新建默认入口 | 配套 | 验收边界 |
|---|---|---|---|
| native | 菜单默认 | 固定来源派生的原生网关、插件、dof-launcher.exe 与 ijl15.dll | 格式 2 / 配方 2.2 已实现；新读取端完整消费与游戏流程待验 |
| dof | 高级 install 默认 | Java 后台和 .NET WPF `DNFLogin.exe` | 按所选等级和实际客户端另验；不读取原生格式 2 包 |

已有实例保留自己的配置，不能因入口默认变化而判断实际模式。先用 `bash dof.sh status --instance 名称` 核对实例，再查私有配置。两种登录链的客户端、密钥布局和发布协议不能混用；普通 DP2/Frida 开关与原生登录必需组件不同。

新建实例可在高级 install 中显式 `--launcher native` 或 `--launcher dof`；不得将此例当成已有实例无损模式迁移承诺。原生正式派生文件已随 Git 提供；重新派生所用固定原件仍为私有材料。

下一步：[原生发布](../operations/native-client-publication.md)、[WPF 部署](../operations/wpf-client.md)、[兼容性定义](../reference/clients/compatibility.md)。配置生成、文件回读与真实 Windows 消费是不同验收层级。
