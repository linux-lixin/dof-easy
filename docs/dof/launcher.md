# 原生服务端内置资源与重新派生

正常部署直接使用仓库的 `services/game/resources/native`，无需另行准备免授权原件。菜单自动选择内置正式资源，不询问目录；高级安装命令指定 `--launcher native`、省略 `--dof-assets` 即使用此目录。原始文件由维护者本地保存，不再入库；普通部署和打包均不需要原件或来源转储。

以下仅供重新派生和打包使用。工具使用 Python 3.11+。仅接受 `metadata/dof/derivation/inputs.lock.json` 锁定的免授权源文件。旧名称只用于识别显式导入材料，不会作为运行时回退路径。输入与输出必须是不同的真实本地目录。

```bash
python3 scripts/dof/import_assets.py --source /local/private/native-inputs --output /local/build/dof-runtime
python3 scripts/dof/package_server.py --runtime /local/build/dof-runtime --validation docs/dof/server-runtime-validation.json --output /local/build/dof-server-runtime.tar.gz
```

派生后资源必须与 `services/game/runtime/ubuntu/gateway/runtime.lock.json` 完全一致；输入不匹配会拒绝构建。包内保留第三方来源和版权说明，不包含私钥、数据库、Windows 客户端或原管理工具。此包为资源包；游戏镜像仍须从同一 main 提交构建。

安装器使用 `--launcher native --dof-assets /local/build/dof-runtime`，现有 `dof` 登录模式保持原意。新部署前先阅读 [当前状态](status.md)。86 级真实加载和网页 PVF 处理已有记录；Windows 读取端与生成端仍需成套适配，完整联调通过前不能宣布发布就绪。更新现有绑定测试实例使用 [限定目标部署入口](game-test-deployment.md)，不要重跑新实例初始化。


## 正式运行目录

| 对象 | 路径 |
|---|---|
| 网关 | `/opt/dof-easy/bin/dof-gateway` |
| 插件与内联库 | `/opt/dof-easy/lib/libdof-plugin.so`、`/opt/dof-easy/lib/libdof-inline.so` |
| 服务代码 | `/opt/dof-easy/runtime/gateway/` |
| 配置 | `/data/dof-easy/config/gateway.ini`、`plugin.ini`、`anton-raid.ini`、`luke-raid.ini`、`dungeon.lst` |
| 身份密钥 | `/data/dof-easy/keys/` |
| 就绪和日志 | `/run/dof-easy/gateway-ready`、`/var/log/dof-easy/` |

网关使用 `$ORIGIN/../lib`；32 位游戏库与 64 位网关依赖分别处理，依赖库保留上游名字。Supervisor 任务为 `dof-gateway`。不创建旧品牌兼容路径，不选择或回退到授权版。

`package_server.py` 把验收摘要放入资源包；更新摘要后重新打包会得到新的归档哈希，必须记录实际结果。已有部署的哈希只对应当时的包。Windows 输入准备、配置包生成及真实读取验收是另一条流程，见 [客户端接口与格式](protocol.txt)。
