# 隔离游戏部署旧正文

历史档案：来自 main@ecd4505 的 docs/dof/game-test-deployment.md。原结论限于 2026-10-26 批次；不作为当前结果。现行操作见[隔离游戏指南](../../operations/isolated-game.md)。

# 现有 86 级测试实例替换

部署脚本要求 Python 3.11+。此入口用于私有 profile 明确绑定的测试实例，不是通用服务器部署器。最新部署和验收结果见 [当前状态](../../status/README.md)；本文说明操作约束，不自动授权操作任意实例。

## 目标与构建

`scripts/dof/deploy_game_test.py` 与网页部署脚本分开。游戏目标限制为内部网络、无公开端口的频道 15 实例；profile 绑定容器完整 ID、旧镜像、数据库 UUID、数据卷、PVF、游戏程序和公钥哈希。实际 profile、凭据及恢复描述不得提交 Git。

先合入 main，再以该提交准备源码、固定免授权资源及镜像。`services/game/docker/ubuntu/Dockerfile.integration` 从已核验的本地原镜像构建，接收 `BASE_IMAGE`、`BASE_IMAGE_ID`、`MAIN_COMMIT`，保留依赖并替换项目模板和运行代码。部署校验镜像标签与基础层前缀，不能用服务器临时编辑代替提交。

```sh
# 先预检；profile 由操作者在私有目录准备并逐项核对
python3 scripts/dof/deploy_game_test.py --profile /private/game-profile.json \
  --runtime /private/dof-runtime --image sha256:<已核验的完整镜像ID>
# 预检通过后，实际替换同一目标
python3 scripts/dof/deploy_game_test.py --profile /private/game-profile.json \
  --runtime /private/dof-runtime --image sha256:<已核验的完整镜像ID> --replace-game
```

## 替换和数据保护

预检包含目标身份、挂载、镜像、ABI、资源锁和空间。容量预算覆盖持久化数据副本、实际 PVF 大小、1 GiB 运行文件及启动开销，再保留 2 GiB 基础余量；不足时不停止当前实例。资源限制沿用并检查目标配置，不修改其他实例。

替换前保存原容器私有描述，在新目录复制状态，保留密钥并检查受管理脚本没有用户修改。首次显式导入转换配置名；已使用 `native` 的来源从固定旧镜像的停止探针取得旧锁，校验后只替换新副本中的载荷。原 `/data` 保留。

新进程使用 `DOF_DATABASE_MODE=existing`，绕过游戏业务库初始化及账号授权重写。原生 `dof_native` 库表按当前定义补齐，不迁移旧库、不重置玩家数据。随机盒的三个表定义为空表初始化，不导入历史奖励或计数器，已有表和记录保留。

新容器必须达到真实进程健康检查；失败保留故障容器并恢复原容器，不还原整个数据库。成功后也保留原容器和数据，恢复名称与镜像写入私有记录。升级网页时另用 [run_isolated.py](../../operations/isolated-admin.md)，不会因此重启游戏或数据库。

## 验证与恢复

核对实际 `dof-gateway` 进程、两个新名模块、配置访问、新库表查询和初始化结果；镜像内原生程序还需保留可执行权限。停止及恢复只针对本次目标，不覆盖玩家进度。

2026-10-26 保存的证据确认游戏 main `175e0624` 已 healthy，实际加载新网关和两个库，随机盒三表存在。随后网页 main `1fd1b6c3` 完成绑定 PVF 导入与加密；游戏和数据库未因网页替换重启。两次提交的服务端源码和资源相同，但不能写成同一部署提交。

网页 client/script 状态接口的重定向已修复；签到记录仍返回结构不兼容的 HTTP 409。Windows 读取端、配套包实际安装和登录／城镇／地下城／重登尚未验收。健康、网页保存、PVF 生成分别记录，不能代替玩家流程。
