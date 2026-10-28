> 历史档案。来源 `docs/dof/client-readiness-update.md`，基线 `ecd4505d1ebdd28486b13a718cd991874efbe96f`；仅保留该批次或上游说明，不作为当前部署步骤。当前请读[统一入口](../../README.md)。

# 客户端材料状态修复：仅更新网页后台

本次修复在客户端材料尚未导入时返回 `enabled=false` 和明确原因，发布请求返回 409；拒绝链接形式的材料目录或绑定文件。页面使用 dof-easy 名称。

本文描述 PR #26（64085b0）的独立修复。后续 main 已加入客户端格式 2 与 PE 适配，更新到后续版本必须先阅读 [格式 2 更新说明](../../operations/native-client-publication.md)，不能只按本文更新后台。缺少材料的提示不会自动补齐材料，也不代表已通过进游戏验收。

## 已部署实例更新

在服务器仓库目录执行以下命令。示例实例为 `main`。先拉取 main，再只构建并替换 admin；无需运行安装或数据库初始化。工作区有本地修改时先处理冲突，不使用强制重置。

```bash
cd /opt/dof-easy
git switch main
git pull --ff-only origin main
git log -1 --oneline
python3 - <<'PYTHON'
import sys
sys.path.insert(0, 'scripts/container')
import manage
root = manage.instance_path('main')
manage.docker_check()
values = manage.read_env(root)
image = manage.image_config.instance_reference(values, 'admin')
manage.run(['docker', 'build', '-t', image, '-f',
            manage.ROOT / 'services/admin/docker/Dockerfile',
            manage.ROOT / 'services/admin'])
manage.compose(root, 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'admin')
manage.compose(root, 'ps', 'admin')
manage.compose(root, 'logs', '--tail', '80', 'admin')
PYTHON
```

构建成功后才替换网页容器；构建失败时停止，当前容器继续运行。此过程不重启 game/MySQL，不更换实例密钥、不覆盖玩家库。首次构建需要拉取基础镜像和构建依赖，预留磁盘与内存。暂时不需要该提示修复时，可先不更新。

更新后刷新网页“客户端发布 / PVF”页面。未导入材料时应显示“当前实例尚未导入客户端发布材料”，而不是普通服务器错误。已有完整材料仍沿用当前协议。后续新协议配套包须在真实客户端读取验证完成后另行发布，不能只换后台或只换文件名。

## 验证状态

- Java 主源码编译：通过。
- 材料缺失与符号链接拒绝测试：2 项通过。
- 前端类型检查与生产构建：通过。
- 服务器容器构建与替换：由操作者执行，未在本轮执行。
- 实际登录、选角、进城、地下城和重登：未验证。
