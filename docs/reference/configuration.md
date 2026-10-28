# 环境变量与实例配置

用途与读者：安装者与运维者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[manage.py](../../scripts/container/manage.py)；[.env.example](../../deploy/manual/all/.env.example)；[application.yml](../../services/admin/backend/src/main/resources/application.yml)。限制和当前验收边界见[当前状态](../status/README.md)。


安装器配置保存在私有 `.dof-easy/instances/<name>/.env` 与生成的 Compose/频道配置中；手动模板使用自己目录 `.env`，隔离 admin 使用独立配置。不能混拷实例 UUID、密钥和数据库配置。

| 配置范围 | 权威定义与使用方式 | 限制 |
|---|---|---|
| 安装器初始值 | [initial_values 与 CLI](../../scripts/container/manage.py) | omitted 值可能由实例旧值补齐；只在新建时套默认 |
| manual all | [环境模板](../../deploy/manual/all/.env.example)、[Compose](../../deploy/manual/all/compose.yaml) | 挂载相对本模板 runtime，先 config 渲染 |
| manual core/game/p2p/admin | [split](../../deploy/manual/split/README.md)各叶子 .env.example | 同版本、角色和启动顺序不可混用 |
| 容器公共环境 | [共享模板](../../deploy/shared)与[启动源码](../../services/game/runtime/ubuntu/bootstrap.sh) | 配置存在不等于组件消费 |
| 原生可选私有输入 | `DOF_LEGACY_UI_PROFILE`、`DNF_COMPANION_PREVIEW` | 分别指向只读旧 UI 精确样本与本机配套预览；缺失时不造示例能力 |

`IMAGE_REGISTRY` / 镜像标签属于实例镜像来源，修改全局默认不改变已建实例；`GAME_BIND_IP` 与 `ADMIN_BIND_IP` 的作用不同。数据库连接和 ADMIN_PASSWORD 为私有运行材料，不写入公共说明。查看配置时使用本机编辑器，不输出整份 `.env` 到日志。

成功信号：模板渲染无未解析变量、挂载符合目标实例、服务启动后值回读符合预期。失败先按[故障指南](../operations/troubleshooting.md)定位，不改业务源码配合错误路径。端口见[端口参考](ports.md)。
