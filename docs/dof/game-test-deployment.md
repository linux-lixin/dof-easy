# 现有测试实例替换

`scripts/dof/deploy_game_test.py` 是独立的游戏部署入口，网页入口仍只允许替换网页服务。本入口限制为内部网络、无公开端口的频道 15 测试实例；私有 profile 绑定容器完整 ID、旧镜像、数据库 UUID、数据卷、PVF、游戏与公钥哈希。不得把实例凭据或 profile 提交 Git。

先将改动合入 main，再以该提交准备源码和固定哈希的正式资源。`Dockerfile.integration` 从经过核验的本地原镜像构建，传入 `BASE_IMAGE`、`BASE_IMAGE_ID` 和 `MAIN_COMMIT`，保留基础运行库、替换项目模板与运行时代码。部署检查镜像标签和基础层前缀；不能用临时服务器编辑替代提交。

不带 `--replace-game` 只做预检。替换前保留原容器完整私有描述，在新目录复制状态并显式转换配置名、保留密钥、核对受管理脚本没有自定义修改。原 `/data` 不变。新进程使用 `DOF_DATABASE_MODE=existing`，绕过游戏业务库初始化与账号授权重写。原生新库表单独增量创建；不迁移旧库，不重置玩家数据。

新容器未达到真实进程健康检查时，入口保留失败容器并恢复原容器。恢复不还原数据库。成功后也保留原容器和原数据，部署记录给出恢复容器名。运行健康不代表登录或玩法通过；网页、Windows 读取端和玩家流程必须另行验收。

## 86-level integration findings (2026-10-26)

Main `33238aa6b7e93e408d205f4e9be3e3740b7470ce` reached healthy status with
`Init GameWorld Success`, `Init Login State Success`, channel 15 ready, and both
renamed native libraries in the actual game process. The original game and web
containers remain available for recovery. This is not player login acceptance.

The actual browser logged in and saved the server display name through the web
UI; the audit returned PERSISTED and a fresh read matched. Browser checks also
found Nginx automatically redirecting the client/script status endpoints. Exact
locations now preserve the original route and forwarded address.

The gateway's embedded schema omits the three random-box feature tables. The
formal builder now appends their empty definitions, matching the verified test
instance column types, defaults and primary keys. It intentionally excludes
old rows and AUTO_INCREMENT counters. Existing tables and rewards are untouched.
This corrects fresh initialization; it does not validate reward delivery.

Windows native consumer adaptation, client package publication and actual
login/town/dungeon/relogin remain pending. Runtime file existence, a listening
channel and a successful browser save are separate evidence.
