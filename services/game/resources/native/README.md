# 正式原生服务端资源

安装菜单资源目录直接回车；高级命令使用 `--launcher native` 时省略 `--dof-assets`，即选择本目录。只接受正式清单及固定哈希载荷。

`manifest.json` 与 [运行锁](../../runtime/ubuntu/gateway/runtime.lock.json) 完全一致。程序为 `bin/dof-gateway`、`lib/libdof-plugin.so`、`lib/libdof-inline.so`；第三方依赖保留上游库名。默认配置无实例秘密，密钥另行生成，PVF 由操作者提供。

[原件与复现](../../../../metadata/dof/source-audit/server-inputs/README.md)、[派生证据和来源说明](../../../../metadata/dof/source-audit/server-derived/README.md) 都在审计区，不作为运行依赖。打包工具从审计区取证据并附在包的 `source-audit/`，安装器仍只复制正式载荷和配置。

本目录可部署不代表客户端进游戏已通过，见 [当前状态](../../../../docs/dof/status.md)。
