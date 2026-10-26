# 部署目录与容器职责

新安装统一在仓库根目录运行 `bash install.sh`。项目不依赖宝塔；宝塔或普通 Nginx 均可作为可选管理／反向代理方式。

## 当前运行方式

**外置 MySQL：两个常驻容器 `game`、`admin`。内置 MySQL：再加一个 `db`。**

| 服务 | 作用 | 生命周期 |
|---|---|---|
| `game` | 核心、频道、组队、原生网关及所选插件；直接发布游戏 TCP/UDP | 常驻 |
| `admin` | 网页、Java API、容器内 Nginx；宿主机 18080（可配置）直接映射到容器 80 | 常驻 |
| `db` | MySQL 5.6、实例独立数据卷，不向宿主机发布 3306 | 仅内置模式，常驻 |
| `keys` | 生成／核验实例密钥，已有密钥保持不变 | 一次性任务，`Exited (0)` 正常 |
| `db-tools` | 限定项目数据库的查询和备份 | 仅外置模式按需 `run --rm`，平时不存在 |

不再创建 `netguard`、`ingress`、`db-check`，不再构建或拉取网络守卫镜像。访问链为“客户端 → game 发布端口”和“浏览器 → admin 发布端口”；宿主机 Nginx 可选。网页默认仅监听 `127.0.0.1:18080`，已有实例保持自己的网页绑定地址。游戏端口随频道及登录模式生成，原生网关为 TCP 8887。

玩家来源及出站访问限制交给云安全组／操作者的网络策略，项目不再在游戏网络空间安装防火墙。旧 `ALLOWED_CLIENT_CIDRS` 不再生效；菜单改为配置游戏绑定地址。游戏仍检查进程和监听端口，但不轮询外置 MySQL。初始化、启动配置及数据库维护操作仍核验版本、UUID 和权限，防止操作错库；不管理外置 MySQL 的启停。

## 目录

| 目录 | 用途 |
|---|---|
| `database/` | MySQL 镜像、种子 SQL、兼容表与配置 |
| `bootstrap/` | 实例密钥初始化 |
| `nginx/` | 可选宿主机 Nginx 反向代理示例 |
| `shared/` | 手动部署共享定义与日志模板 |
| `manual/` | 高级单机／拆分部署模板 |

等级由资源清单选择，回车默认 86。早期固定等级方案在 [历史归档](../docs/history/offline86/README.md)，旧网络辅助实现在 [网络历史归档](../docs/history/network-helpers/README.md)，均不参与当前安装构建。

## 已有一键端更新

在服务器自行执行（操作当前实例会短暂停服）：

```bash
cd /opt/dof-easy
git pull --ff-only origin main
bash dof.sh backup --instance main
bash dof.sh install --instance main --build
bash dof.sh status --instance main
```

已有实例的数据库模式、等级、端口、密码、PVF 和插件配置保持不变，无需重填数据库信息或重建库。若使用发布镜像，将 `--build` 换成对应新版的仓库／标签参数。仅 `git pull` 不会更新镜像里的健康检查代码。

首次切换会按 Compose 项目标签核对容器身份，将配置快照写入实例的 `config-backups/topology-*`，停止该实例的 game/admin，再移除它自己的 netguard/ingress/db-check，释放旧端口后启动新拓扑。不会执行全局 prune、删除卷或停止其他实例／宿主机 MySQL。再次运行时没有旧辅助容器便跳过清理。

若切换失败，先查看报错及 `bash dof.sh logs --instance main`，修复后重试安装／启动；已完成的库初始化不会重复执行。配置快照含秘密，勿提交。需要回到旧拓扑时，应使用更新前的代码和镜像，恢复快照中的实例配置后启动；不能只恢复旧 Compose 文件并混用新代码，也不需要回滚玩家数据库。

手动部署实例仍按 [manual 说明](manual/README.md) 管理独立 `.env` 和 `RUNTIME_ROOT`，不要混用一键端数据目录。新安装步骤见 [菜单说明](../docs/container-installer.md)及[同机 MySQL 指南](../docs/baota-deployment.md)。本轮验证及未执行项目见 [直接端口映射验证](../docs/validation/direct-topology.md)。
