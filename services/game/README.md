# Ubuntu 游戏服务

唯一游戏镜像基于 Ubuntu 24.04，支持 ALL、CORE、GAME、P2P 四种角色，游戏容器连接独立 MySQL；通用安装器提供数据库容器，手动模板使用自行管理的外部数据库。

| 目录 | 用途 |
|---|---|
| `docker/ubuntu` | Dockerfile、必要兼容源码和许可证 |
| `assets` | 公共游戏资源、模板和初始化 SQL；不含 PVF 与所选等级程序 |
| `resources/plugins` | 固定 DP2、Frida 正式输入 |
| `resources/native` | 已改名原生载荷，安装器校验后写入实例 |
| `runtime/ubuntu` | Ubuntu 入口、校验、健康检查和启动完成提示 |

在仓库根目录构建：

```bash
docker build -f services/game/docker/ubuntu/Dockerfile -t dnf-game:ubuntu24.04 services/game
```

启动脚本已直接保存为 Ubuntu 版本，不再依靠构建时剪裁旧平台脚本。
默认部署见 [根 README](../../README.md)，高级手动部署见 [整体](../../deploy/ubuntu/all/README.md) / [拆分](../../deploy/ubuntu/split/README.md)，依赖见 [兼容说明](../../docs/ubuntu/compatibility.md)。
`assets` 是构建输入，不是线上运行目录。实际 PVF、公钥、配置和用户 hook 保存在部署的 `runtime` 中。

## dof-easy 网关目录

运行源码集中在 `runtime/ubuntu/gateway/`，包含 `service.py`、`binary.py`、`assets.py`、`schema.py`、`db_adapter.js`，统一清单 `runtime.lock.json` 和配置模板 `defaults/`。镜像内安装到 `/opt/dof-easy/runtime/gateway/`。

| 运行对象 | 路径／名称 |
|---|---|
| 网关进程 | `/opt/dof-easy/bin/dof-gateway` |
| 原生插件／内联库 | `/opt/dof-easy/lib/libdof-plugin.so`、`libdof-inline.so` |
| ELF RPATH | `$ORIGIN/../lib` |
| 配置、密钥 | `/data/dof-easy/config/`、`/data/dof-easy/keys/` |
| 原生数据库 | `dof_native`，配置表 `config`、`config_v2` |
| Supervisor | `dof-gateway`，配置 `dof-gateway.conf` |
| 就绪标记 | `/run/dof-easy/gateway-ready` |

`import_assets.py` 在本地对固定哈希免授权输入进行确定性派生；启动时 `binary.py` 只复制并验证已派生文件，不再临时修改旧程序。32 位与 64 位库搜索分开，游戏加载链使用新名。安装器不创建旧品牌路径兼容链接。

默认配置为 `plugin.ini`、`anton-raid.ini`、`luke-raid.ini`、`dungeon.lst`，`gateway.ini` 由运行时生成。当前未上线项目直接使用新配置；已有显式离线迁移工具仅保留供历史材料导入，不是本次部署任务。

当前固定资源可确定性派生，受影响的 101 项本地检查通过；86 级实际网关、模块加载和初始化已有证据。网页配置及 PVF 处理已有通过记录，签到记录查询尚有结构不兼容问题；Windows 读取端和实际游戏流程仍待完成（`releaseReady=false`）。详情见 [验收报告](../../docs/dof/server-runtime-validation.md) 和 [资源导入](../../docs/dof/launcher.md)。

## 普通插件与团本

DP2、Frida 和原生资源统一放在 `resources/`，完整的源码、镜像、持久化及加载路径见 [资源布局](../../docs/resource-layout.md)。安装菜单可选 DP2＋Frida、仅 DP2、仅 Frida 或关闭；团本独立开关且依赖 DP2。原生登录必需的凭据适配不随普通插件关闭。

实例插件修改保存在 `/data/dp/` 与 `/data/plugins/frida/`；`/dp2` 保留上游加载约定。模板不带数据库密码，启动时按当前实例注入。原始材料由维护者本地保管，不再入库。安装菜单自动选择仓库正式资源；DP2 配套 PVF 补丁随 DP2 选装提供。
