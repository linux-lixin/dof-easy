# 正式原生服务端资源

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/services/game/resources/native/README.md)。限制和当前验收边界见[当前状态](../../../../docs/status/README.md)。


安装菜单自动选用本目录，不再询问资源路径；高级命令使用 `--launcher native` 时省略 `--dof-assets`，即选择本目录。只接受正式清单及固定哈希载荷。

`manifest.json` 与 [运行锁](../../runtime/ubuntu/gateway/runtime.lock.json) 完全一致。程序为 `bin/dof-gateway`、`lib/libdof-plugin.so`、`lib/libdof-inline.so`；第三方依赖保留上游库名。默认配置无实例秘密，密钥另行生成，PVF 由操作者提供。

原始材料由维护者本地保存，仓库不存原件或派生转储。正常打包只需正式资源和匹配的验收摘要；显式重新派生见 [维护入口](../../../../docs/development/native-resource-derivation.md)。

本目录可部署不代表客户端进游戏已通过，见 [当前状态](../../../../docs/status/README.md)。
