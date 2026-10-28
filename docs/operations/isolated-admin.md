# 隔离网页部署与恢复

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[deployment.txt](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/docs/dof/deployment.txt)。限制和当前验收边界见[当前状态](../status/README.md)。


所有命令先准备显式私有目标配置，不内置实际服务器、账号或测试实例。
将 scripts/dof/deployment-profile.example.json 复制到仓库外，保存为 deployment-profile.json，
权限设为 0600，逐项核对路径、容器、网络、数据库 UUID、公钥 SHA-256 及受保护容器列表。
示例全零 UUID/哈希刻意无法通过校验。账号名是占位示例；密码不填入这个文件。
密码仅由首次准备脚本随机生成并保存在本机 credentials.json/runtime.env，权限 0600。

## 首次准备

仅对已有独立游戏/数据库副本，已初始化新 dof_native 原生库。

```bash
python3 scripts/dof/prepare_isolated.py --profile /private/deployment-profile.json \
  --sources services/admin/database --peak-bytes <备份和新增文件预计峰值>
```

脚本先核对挂载、UUID、隔离标记和容量，保存私有备份，仅添加服务表和专用管理身份。
它只提取旧 init.sql 的 CREATE 定义，不执行玩家初始化、默认种子或 DROP。
已存在 runtime.env 时拒绝重新准备，防止升级时重置环境和覆盖发布设置。

## 客户端来源
安装器实例更新顺序与旧工作目录处理见[原生发布](native-client-publication.md)。
在本地私有 Python 环境安装 scripts/protocol/dof/requirements.txt 的导入依赖。
以下入口显式读取原始配套并确定性派生新读取端，只把改名后的文件交给网页。

```bash
python3 scripts/protocol/dof/prepare_client_inputs.py --client <配套客户端目录> \
  --public-key <现有服务器公钥DER> --public-sha256 <已核对哈希> \
  --workspace <全新私有来源目录> --instance dof-main \
  --database-uuid <当前实例数据库UUID> --endpoint <游戏地址>
```

将该目录里的 inputs 中五个普通文件打包到私有传输包（不包含目录条目）：
tar -cf /private/client-inputs.tar -C <全新私有来源目录> inputs/binding.json \
  inputs/template.dll inputs/game-public.der inputs/dof-launcher.exe inputs/ijl15.dll
在当前安装器管理的服务器仓库执行：

```bash
python3 scripts/dof/install_client_inputs.py --instance main --archive /private/client-inputs.tar
```

main 是安装器实例名，dof-main 是对应网页身份；其他实例按实际名称替换。
脚本只读核对实时数据库 UUID、管理表绑定和当前游戏公钥，不初始化数据库、不改密钥、
不重启服务；只安装到尚不存在的网页发布目录。已有输入／任务目录会拒绝覆盖。
安装主机不需要 Python 密码学依赖；内层 RSA 校验在本地导入和网页生成阶段执行。
服务端私钥、原管理工具和完整客户端不得加入传输包。新格式真实游戏读取仍须按
client-reader-validation.md 的状态和流程验收，不能把来源安装成功视为可以进游戏。

构建
在本地准备 JDK、Java 依赖和前端依赖，compile_admin.py 编译时保留 Java 参数名。

```bash
python3 scripts/dof/package_admin.py --base-jar <配套SpringBoot归档> --classes <新类目录> \
  --resources services/admin/backend/src/main/resources --frontend <dist> --output <新构建目录>
```

原 JAR 只提供加载器和依赖；业务类取自本次编译。不复制私有输入或测试文件。
按 build-manifest.json 校验产物。在服务器使用已核对的本地基础镜像标签构建：

```bash
docker build --network=none --pull=false --build-arg RUNTIME_IMAGE=<本地标签> <构建目录>
```

标签必须先核对为预期镜像 ID；不要把 sha256:镜像ID 当作 FROM 标签。
jar_delta.py 可传输 JAR 差量，但会严格核对基准及目标哈希，和原生游戏更新协议无关。

运行与升级

```bash
python3 scripts/dof/run_isolated.py --profile /private/deployment-profile.json \
  --image sha256:<本次完整镜像ID> --replace-web --peak-bytes <本次额外峰值>
```

仅允许替换 profile 中的网页容器。数据库和游戏以及其他 protectedContainers 不操作。
不发布公网端口；管理页面通过 SSH 隧道访问。不会初始化玩家库或生成身份密钥。
实际部署目录必须与私有 profile 一致；不要套用其他实例的配置。

配套包参数应用前，runtime.env 设置 DOF_GAME_PVF=/app/paired/Script.pvf。
运行脚本确认 host gamePvf 与隔离游戏 /data/Script.pvf 的实际挂载相同，随后只读挂载。
服务器共享 PVF 从不被网页改写；上传来源哈希不一致时拒绝应用。
数据卷保留 inputs、jobs、script/uploads、script/tasks 和私有参数；日志单独持久化。
新版本 dof-admin.sql 仅添加 DOF_ 管理表；项目未上线，不迁移旧库。已有测试网页配置需显式重新绑定 DOF_*、dof-native-1 和 LAUNCHER_MODE=native，保留旧配置恢复点，勿重跑首次准备。
PVF 应用需要专用数据库用户对 dof_native.billing.decrypt 和 dof_native.config.pvfencryption
拥有字段 UPDATE 权限，另需原生表读取/锁和服务库审计权限。不要授予全库玩家写权限。

容量
每个目标文件系统需至少 2 GiB 可用基础余量，加本步新增峰值。
PVF 需按原始上传、加密文件、ZIP 及临时文件实际规模计算，不能沿用小登录器包预算。
读取 statvfs.f_bavail，不使用保留块。空间不够即停止，不清理正式数据、备份或回退镜像。

## 恢复
更新前保留 runtime.env、原镜像 ID、发布来源与任务目录；不要把秘密写入 recovery 描述。
网页失败时用同一 profile 和旧镜像 ID 重建网页，保留持久卷。
参数保存断连先按操作编号 reconcile；MyISAM 不依赖事务回滚。
只恢复本次字段及文件，后续修改冲突则拒绝覆盖。不要回滚玩家整库。
游戏验收必须保持原 Windows 后台管理工具关闭，使用网页生成包完成进城、战斗、返城和重登。
正式服部署、切换和重新初始化不属于这些命令的用途。

历史集成记录（不代表本次格式 2 的部署状态）
2026-10-26 保存的记录中，游戏部署为 main 175e0624，网页为 main 1fd1b6c3；中间服务端源码与固定资源没有变化。网页替换未重启游戏或数据库。
网页登录、配置保存和回读通过，client/script 状态路由返回 200。绑定 PVF 导入及不压缩加密完成，任务仍为 READY_FOR_TEST / NOT_APPLIED，游戏 PVF 和客户端文件未替换。
网页可将 DOF_GAME_PVF 对应的只读文件导入普通上传任务，无需重复经浏览器传输；请求只传编号和修订，不接受服务器文件路径。
以下记录形成时 Windows 文件与字段消费端尚未改造验证；当前进度见 client-reader-validation.md。不能把现有旧配套包与新服务端直接部署并宣称联调通过。签到历史 HTTP 409 的原因未知，S50 所核对实例返回 200、取样为空；非空记录仍待验。完整版本和证据见 status.md、web-integration-validation.json。
历史一次性清理不构成自动保留策略。升级保留的恢复点和私有任务文件按实际清单管理；清理过的旧任务下载需要先恢复对应文件。

2026-10-28 main 测试部署与清理
最新后台源码基线为 a6ade43，具体镜像及游戏镜像引用恢复、重建结果见 status.md。
旧部署目录、回退镜像与重复备份已按用户要求清理，历史 Sxx 镜像回退命令不再可直接使用。
当前仅保留 .dof-easy/current-backup/ 一份 admin 数据及管理／原生配置／钱包 SQL 快照，
不包含完整玩家数据库。manifest.private.json 含当前镜像、文件哈希和备份范围。
恢复 admin 文件使用 scripts/dof/admin_restore_drill.py，在全新私有目录验证；不要向运行目录解压。
不得使用此管理快照重置游戏业务库。本次保留安排不是自动清理策略。
