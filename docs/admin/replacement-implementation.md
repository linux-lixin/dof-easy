# 免授权版 admin 替代实施记录

目标是使用网页完成晴空免授权 2.8 后台工具的全部管理操作。服务端、插件和客户端组件继续承担各自的运行职责；WPF 登录器改造单独实施。功能范围保持 71 类、239 项，并在原工具对照时继续补录。

## 当前交付范围

本批基于 `a9d1511`，实现提交为 `29cc3c7c87d48ac3387015b58eec8f3231e52dce`，在 `codex/admin-replacement` 分支实现，已在绑定的 86 级 `dof-main` 隔离实例部署新版 admin。完整替代状态仍为未完成。

| 阶段 | 本批实现 | 仍需完成 |
|---|---|---|
| P0 | 固定 12 个原始服务端载荷、免授权后台 EXE 和 libxl.dll、原始客户端 EXE 和 DLL，共 16 个输入；离线校验全部匹配 | 原工具全部子菜单、按钮、默认值和导入导出的等价审计 |
| P1 | 数据库身份绑定与 UI 表结构检查解耦；一个 UI 配置异常不再阻断插件和记录模块；`dof` 登录也可启用团本管理 | 将能力、任务及恢复机制覆盖到全部业务操作 |
| P2 | 固定记录目录复用既有投影，增加战令、收集箱、在线奖励、团本进度和举报聊天元数据；提供只读结构诊断；刷图记录按免授权原生 `map_history` 适配 | 当前实例签到结构与 HTTP 查询已核对；继续完成全部账号、角色及记录内容的原工具等价对照 |
| P3 | 基础插件 15 项、安徒恩 32 项、卢克 38 项，共 85 个配置字段的预览、保存、导入导出、历史、核对和恢复 | 网页运行时开关、原生刷新控制及完整团本流程验证 |
| P4 至 P8 | 保留已有邮件、经济、活动、UI、PVF 和格式 2 配套代码 | 按原工具逐项补齐实现、业务恢复、消费及游戏效果证据 |
| P9 | 管理页新增团本与插件入口；UI 模块读取失败不再隐藏其他管理页 | 全量页面导航、批量操作及浏览器验收 |
| P10 | 标准 Maven、MySQL 5.6 契约、前端构建、候选真实浏览器写入/恢复及隔离 admin 镜像部署 | Windows 原工具完整对照、全部业务、容器重建与实际游戏验收 |

## 免授权输入核对

固定清单为 `metadata/dof/admin-baseline.lock.json`。服务端条目复用既有确定性派生输入哈希，不重新指定另一套原件。原始客户端与派生格式 2 产物使用各自的锁，不能混为同一个文件。

```bash
python3 scripts/dof/verify_admin_baseline.py \
  --server /本地/免授权服务端目录 \
  --tool /本地/免授权覆盖文件目录 \
  --client-archive /本地/客户端文件.zip
```

命令只读取和计算哈希，不执行 EXE、ELF、脚本或覆盖材料。只要一个文件缺失或不匹配，就返回非零退出码。输出不包含绝对路径、密码和数据库连接信息。`sourceVerified` 仅表示材料匹配，不代表菜单审计或游戏验收完成。

## 插件配置管理

入口为 `/admin/dof` 的“团本与插件”。固定模块为 `plugin`、`anton`、`luke`，对应 `plugin.ini`、`anton-raid.ini`、`luke-raid.ini`。

| 接口 | 用途 |
|---|---|
| `GET /api/v1/admin/dof/plugins` | 模块能力、部署配置和不可用原因 |
| `GET /api/v1/admin/dof/plugins/{module}` | 当前字段、文本、编码和版本 |
| `POST /api/v1/admin/dof/plugins/{module}/preview` | 按版本预览字段变更或导入内容 |
| `PUT /api/v1/admin/dof/plugins/{module}` | 保存已经预览的变更 |
| `GET /api/v1/admin/dof/plugins/{module}/history` | 操作历史 |
| `POST /api/v1/admin/dof/plugins/{module}/history/{id}/reconcile` | 核对中断操作 |
| `POST /api/v1/admin/dof/plugins/{module}/history/{id}/restore` | 恢复该操作涉及的字段 |

预览和保存共用 `requestId`、`revision`，并提交 `values` 或导入的 `content`，二者不能混用。恢复使用新的操作编号。所有接口验证管理员权限及数据库实例身份。

变更保留未编辑字段、未知字段、注释及原文件编码。导入只接受当前文件已有的已知字段变更，不使用上传文件覆盖整个原文件。网页导出是 UTF-8 文本；实际保存仍沿用原文件的 UTF-8 或 GB18030 编码。

奖励表、时间、星期、元组和数值在后端检查格式。原模板必须能够完整读回；验证范围不等于原生执行逻辑已通过。自动补全未知配置或直接开放尚未验证的字段不在当前行为内。

每次文件写入先保存 `PREPARED` 恢复记录，再执行完整文件的原子替换，最后标记 `PERSISTED`。写前再次核对原文件哈希；符号链接、重复字段和未知模块拒绝访问。重复请求只返回原结果，不重复改写。恢复只处理原操作涉及的字段，并拒绝覆盖第三方对这些字段的修改。

文件历史保存在该 admin 实例的私有 `plugin-audit/` 目录中，快照不由 HTTP 返回。备份 admin 数据目录时应包含它。

## 部署接线

实例生成模型为启用原生管理的 admin 提供：

- `DOF_GAME_CONFIG_ROOT=/app/game-config`，只挂载该实例的 `runtime/game/data/dof-easy/config/`。
- `PLUGIN_MODE`、`RAID_EXTENSION` 和实际 `LAUNCHER_MODE`，用于显示部署配置；它们不表示已观察到运行效果。
- 既有数据库 UUID、实例绑定和私有管理数据目录。

安装器在配置目录写入 `.admin-instance.json`，绑定实例名和数据库 UUID。接口会同时检查数据库绑定与文件目录标记；挂错其他实例的目录时拒绝访问。既有管理实例再次启动时会核对并补充标记，不覆盖不匹配的标记。

原生登录或启用团本的实例使用绑定后的 admin 入口。启用管理后保存 `DOF_ADMIN_ENABLED=true`，关闭团本时仍可查看和编辑管理配置。为旧通用实例首次添加管理能力时，只执行既有追加式建表和绑定，不重新灌入游戏 SQL 或重置账号。

手动部署必须提供相同的固定挂载、实例绑定及环境变量。仅更新源码不会更新正在运行的镜像或已有部署模型。

保存配置不会开启团本，也不会自动重启游戏。团本当前需要有权限的 GM 使用实例配置中的原生刷新命令；刷新调用与游戏实际效果仍是待验项目。

## 测试与未完成项

机器摘要见 `docs/validation/admin-replacement-2026-10-26.json`。既有记录继续按来源日期和适用范围保留，不能用这次代码修改扩写为生产部署通过。

本批没有运行原 Windows 后台工具的界面对照。Windows 桌面自动化在初始化时拒绝 WSL 工作目录 URI；终端、编译、文件和数据库测试仍可运行。该限制需要恢复桌面工具环境后再完成对应审计。

WSL 无头 Chromium 已使用生产构建产物和模拟接口完成 10 项交互检查，包含结构诊断与生效指引；页面运行错误为 0。另通过 SSH 隧道连接独立候选容器，完成真实网页登录、配置副本预览/保存、重复请求、旧版本拒绝、导出、按原哈希恢复及签到结构/查询检查。候选写入只修改复制的配置目录，未被游戏消费；不得当作团本效果验收。

回归脚本为 `tests/integration/admin/plugin-page.cjs`，使用 Node 22、外部提供的 Playwright 及其 Chromium，不增加生产依赖：

```bash
node tests/integration/admin/plugin-page.cjs \
  --dist /临时目录/已构建前端/dist \
  --output /临时目录/浏览器检查结果
```

必要时通过 `PLAYWRIGHT_MODULE` 指定 Playwright 模块位置、`PLAYWRIGHT_BROWSERS_PATH` 指定浏览器缓存。脚本只允许访问临时回环地址，自动关闭浏览器和 HTTP 服务；截图与模拟导出文件写入指定输出目录。

已在服务器构建并部署新版隔离 admin，保留原镜像、部署配置、环境与管理数据恢复点，外部端口仍为 18080。游戏没有重启、配置原值及密钥保持不变，没有初始化数据库。当前实例签到表字段齐全，实际接口返回 200；历史 409 未复现，不能宣称已查明旧实例故障原因。实际客户端进游戏、奖励到账和完整团本仍未验收。

后续按 P2/P3 未完项及 P4 至 P10 继续执行。不能将本批的 85 项配置字段覆盖写成 239 项操作全部完成。

## 记录与真实部署回归

`GET /api/v1/admin/dof/records/{key}/schema` 仅检查固定来源的表字段、类型及数据库时间，不读取玩家行。表字段齐全不代表账号具有全部查询权限，仍需执行真实读取。查询保持固定投影、参数绑定、确定性排序及分页；举报聊天仅返回元数据，不代替全部聊天正文审计。

刷图记录使用免授权建表定义中的 `map_history`，不再查询旧 `dungeonCheck`；`reward_state` 按原定义显示为公告状态，不视作奖励到账证明。未知字段和玩家进度不改写。

真实回归入口为 `tests/integration/admin/plugin-live.cjs`。凭据 JSON 只放在私有临时目录，格式为 account/password，不提交仓库或报告；测试不记录令牌或密码。默认只读，仅在绑定配置副本的候选容器上使用 `--allow-config-edits`。

```bash
node tests/integration/admin/plugin-live.cjs \
  --origin http://127.0.0.1:18080 \
  --credentials /私有临时目录/credentials.json \
  --instance dof-main --output /私有临时目录/回归结果 \
  --require-all-records
```

页面截图和导出文件留在私有结果目录。真实游戏验收前不能据此关闭各项效果验证。服务器更新了部署模型与实例镜像引用；重新通过安装器生成模型前，需先同步包含本批挂载逻辑的源码，不应使用旧安装器覆盖已验证接线。

## 功能清单与完成门禁

从同一份现有清单生成 239 项工作对应表，不另设人工维护的进度来源：

```bash
python3 scripts/dof/admin_inventory.py --format csv > /私有临时目录/admin-feature-matrix.csv
python3 scripts/dof/admin_inventory.py --require-complete
```

检查唯一工作包归属、依赖环、重复或排除操作、计数一致性及完成证据。`verification` 的 nativeParity、implementation、persistence、consumption、effect、recovery 分别记录 state/evidence；不适用项另附 reason。未记录的阶段保持 pending，不从配置接入标签推断验收通过。当前完整替代门禁按预期返回非零，不能发布为全量替代完成。

## 本轮部署结果

- 最终镜像：`sha256:4d265d325bbc30be83123e72260c9432bbc8dc091243edec23a99534dbd26d40`。
- JAR SHA-256：`2b3043fa179f2778e1f123f2644b39c3cbe89d7dd32fd47cc5d7a635af97136c`。
- 标准 Maven：231 项，失败/错误 0，跳过 20；跳过的类别单列在机器摘要，不计通过。
- 当前隔离实例 30 类固定记录投影均实际查询成功；这只证明可查询，不证明已有业务数据与原工具逐行等价。
- 候选容器删除并重建后，配置审计文件哈希保持一致，2 条真实保存/恢复历史仍可读取；主 admin 和游戏没有随该检查重启。
- 新增配置写入/恢复只在候选副本上验证。部署后的主 admin 仅执行只读回归，未刷新游戏或修改游戏配置值。

admin 数据、原镜像和部署模型的恢复点保留在服务器私有交付目录中。回退应固定镜像 ID 对应的保留标签，仅重建 admin，并保留管理持久卷；不要运行首次建库流程。

最近一版的服务器恢复点是 `/opt/dof-easy/.dof-easy/admin-replacement/20261026-plan2/before-record-adapter/`，包含替换前的部署模型、实例环境和管理数据备份。首次更新前的完整恢复点另保存在同级 `before-promotion/`，其中可能含凭据，目录不得开放下载或提交 Git。

回退最近一版时，先备份当前 `deployment.json` 与 `.env`，确认保留标签 `dof-admin-replacement:20261026-plan2-final` 仍指向 `sha256:f13910f5395da565f57e397a554cd8dec71aa3ac446f4934e235636ae3c4f217`，再恢复 `before-record-adapter/` 中这两个文件，只执行：

```bash
sudo docker compose -p dof-main \
  -f /opt/dof-easy/.dof-easy/instances/main/deployment.json \
  up -d --no-deps --pull never --force-recreate admin
```

保留现有管理持久卷，不用旧备份覆盖新增审计；游戏和数据库不参加这个重建。初次更新前的旧镜像已另存固定回退标签，不能直接信任可能已变化的 `dof-easy-admin:local` 标签。数据库故障时先恢复连接，再按原操作编号核对；磁盘不足时停止生成任务并保留已有备份；未知执行结果不重复发奖或扣款。
