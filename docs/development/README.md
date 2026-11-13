# 开发维护

部署或管理现有实例请从[使用文档](../README.md)进入。本页说明源码、检查和维护边界。

## 源码导航

| 目录或文件 | 职责 |
|---|---|
| [dof.sh](../../dof.sh)、[实例管理](../../scripts/container/README.md) | 安装、菜单、配置、镜像与生命周期 |
| [部署模板](../../deploy/README.md) | Compose、密钥初始化、数据库种子 |
| [游戏服务](../../services/game/README.md) | Ubuntu 运行包装器、兼容层与镜像 |
| [资源来源与锁定](../../metadata/README.md) | 上游来源、共享资源、等级包与输入哈希 |
| [脚本目录](../../scripts/README.md) | 管理脚本和诊断工具入口 |
| [测试目录](../../tests/README.md) | 单元、结构与隔离数据库验证 |
| [贡献说明](../../CONTRIBUTING.md) | 提交内容、分支和私有材料规则 |

安装器只维护晴空＋外置 MySQL。旧产品与历史文档跟随原 main 备份，不重新放回当前分支。新旧模式的运行边界见[升级说明](../operations/upgrade.md)。

## 按改动选择检查

使用 Python 3.11+。在仓库根目录运行与改动相关的检查：

```bash
# 文档链接、目录、导航、CLI 默认值和私有材料检查
python3 scripts/diagnostics/check_docs.py
git diff --check

# 单元与结构检查
python3 -m unittest discover -s tests/unit -p 'test_*.py'
python3 -m unittest discover -s tests/structure -p 'test_*.py'

# 游戏镜像源码构建
sudo bash dof.sh build --registry '' --tag qingkong-check
```

完整结构与镜像检查需要完整仓库资源、Linux amd64 和 Docker/Compose；部分兼容测试需要 i386 编译工具链。缺少资源、工具链或 Docker 时，明确记录失败或跳过，不报告全通过。SQL 变更另按[隔离数据库验证](../../tests/integration/README.md)执行，不使用线上玩家库试验。

源码检查不能代替 Windows 后台和完整客户端流程，实测范围见[验证状态](../status/README.md)。

## 文档维护约定

- README 保留项目介绍与任务入口；完整使用顺序放在[文档目录](../README.md)。
- 安装、日常管理、数据库、Windows 工具、备份、升级各自只维护自己的操作内容，其他页面用链接引用。
- 代码目录 README 解释目录职责，不复制另一份安装命令或部署事实。
- 新增维护文档加入 `docs/catalog.json`，并从使用目录或本页可达。检查器默认只读取 Git 索引中的文件，新文件先暂存或用 `--include 路径` 检查。
- 示例使用占位 IP 和私有文件路径。设计草稿、真实服务器凭据、密钥、PVF 和玩家记录不发布。

## 资源与兼容层

共享游戏资源和兼容库使用固定哈希；不要修改锁文件来掩盖来源不一致。晴空原包由独立输入清单校验，仓库保留的 `frida.so` 只负责数据库凭据兼容。详见[兼容资源](../../services/game/resources/README.md)。

历史密钥目录、基础 `libhook` 路径和数据库身份检查可能看似冗余，删除前需核对既有实例的兼容要求。不要因为清理文档而改动运行文件或私有实例数据。
