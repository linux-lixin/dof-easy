# 服务端资源布局

部署只使用 `services/game/resources/` 下的正式插件和原生文件。仓库保留全部正式插件、配套文件和安装校验清单；原始材料由维护者本地保存，不再存入仓库。安装自动选择内置资源，不再询问原生资源目录。

## 仓库位置

```text
services/game/
├── assets/                       公共游戏模板、初始化资源
├── resources/
│   ├── plugins/
│   │   ├── dp2/                  DP2 库、Lua、SQL 辅助脚本
│   │   └── frida/                Gadget、JS、无密码配置模板
│   └── native/
│       ├── bin/dof-gateway
│       ├── lib/                  libdof-plugin.so、libdof-inline.so 及上游依赖
│       ├── config/               默认配置，不包含实例密钥
│       ├── runtime/gateway/      schema.sql、equipment.js、equipment-abi.json
│       └── manifest.json
└── runtime/ubuntu/gateway/        服务代码、正式锁和配置模板的维护入口
metadata/dof/
├── derivation/                   显式导入工具必需的哈希与改名规则（无原件）
├── native-name-contract.json     与正式清单绑定的加载测试断言
└── server-probe.lock.json        本地集成测试材料的固定哈希（无测试材料）
```

`metadata/build-inputs.lock.json` 固定 422 个公共构建输入，共 245,094,257 字节；原生清单另固定 15 个正式载荷。DP2 目录含配套 `pvf-patch.zip`，由锁文件校验。依赖库保留上游名称，DP2、Frida 名称也保留各自项目身份。

## 镜像与实例运行路径

| 组件 | 镜像／运行位置 | 开关与用途 |
|---|---|---|
| DP2 | 镜像 `/opt/dnf/plugins/dp2/`；持久化 `/data/dp/`，通过 `/dp2` 加载 | `PLUGIN_MODE=dp2` 或 `dp2-frida` |
| 普通 Frida | 镜像 `/opt/dnf/plugins/frida/`；脚本 `/data/plugins/frida/`；Gadget `/home/neople/game/frida.so` | `PLUGIN_MODE=frida` 或 `dp2-frida` |
| 原生网关 | `/opt/dof-easy/bin/dof-gateway` | `LAUNCHER_MODE=native` |
| 原生插件与内联库 | `/opt/dof-easy/lib/libdof-plugin.so`、`libdof-inline.so` | 原生链及团本按运行配置加载 |
| 团本扩展 | 同一原生资源包，配置 `/data/dof-easy/config/anton-raid.ini`、`luke-raid.ini` | 默认 `RAID_EXTENSION=off`；按需改为 `on`，要求 DP2 |
| 网关代码与配置 | `/opt/dof-easy/runtime/gateway/`、`/data/dof-easy/config/` | `gateway.ini` 由运行时生成 |
| 密钥与日志 | `/data/dof-easy/keys/`、`/var/log/dof-easy/` | 每实例私有，不入库 |

`/dp2` 是上游 DP2 的加载约定，不是旧品牌兼容目录。`PLUGIN_MODE=none` 关闭普通玩法插件；原生登录所需的 Frida 凭据适配仍由原生链准备，不能随普通插件一起移除。修改插件组合时按实例配置管理，不手工删除库。

Dockerfile 只复制普通插件的两个明确子目录。原生资源不进入共享游戏镜像上下文；安装器仅复制锁定载荷、清单和配置到实例 `/data/dof-easy/`，启动时再次核验。原件、重复原始 SQL、历史来源清单及派生转储已撤出仓库。DP2 的 `pvf-patch.zip` 会随 DP2 安装到 `/data/dp/`，供配套 PVF 编辑使用；不会自动覆盖或合并你的 PVF。

## 安装、维护与校验

正常在仓库根目录运行 `bash dof.sh`，选择安装、等级（默认 86）、插件及内置／外置数据库。菜单不再询问正式原生资源目录。高级命令省略 `--dof-assets` 使用同一默认目录；指定目录时必须是相同运行锁校验通过的正式资源。

```bash
# 部署资源校验，不需要原件或联网
python3 scripts/container/resources.py
# 维护者显式重新派生，输出必须是新的本地目录
python3 scripts/dof/import_assets.py --source /local/private/native-inputs --output /tmp/dof-runtime-rebuilt
```

原件哈希不匹配直接失败，没有授权版回退。重新派生和正式打包见 [原生资源说明](dof/launcher.md)。当前初始化 SQL 是 `deploy/database/seed.sql`，不再保存另一份原始 SQL。普通插件修改应更新构建锁。第三方版权说明仍随项目保留，不包含原始程序。

**仍可能联网的部分**：首次获取源码、所选等级包（本仓库 Release 的 13 个等级之一）、Docker 镜像及源码构建时的系统/npm/Maven 依赖。原生、DP2、Frida 正式文件已在 Git，无需第三方下载站。PVF、客户端、密码与私钥由操作者保管。详见 [安装菜单](container-installer.md)、[宝塔部署](baota-deployment.md)。

整理资源不代表重新验证进游戏。历史证据保留原路径与日期；原先整理结果见 [资源整理验证](validation/unified-server-resources.md)；撤出原件及简化安装的结果见 [正式资源验证](validation/deployable-resources-only.md)。

新服务器使用 `git clone --depth 1 --branch main --single-branch https://github.com/linux-lixin/dof-easy.git`，只检出当前版本。此次移除不重写既有 Git 历史；旧提交仍保留当时记录。
