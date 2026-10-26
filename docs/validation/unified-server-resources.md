# 服务端资源整理验证

> 后续按用户要求已撤出原件与来源归档，安装不再询问资源目录；当前结果见 [正式资源验证](deployable-resources-only.md)。本页保留历史结果。

日期：2026-10-26；基线：`3dd959fe393f283f178acd49a8dc6fad9f0be033`（PR #16 合并）。本轮仅本地处理，没有连接或修改现有服务器。

## 变更范围

- 正式 DP2、Frida 与原生资源归入 `services/game/resources/`；原生服务代码仍在此前规划的 `services/game/runtime/ubuntu/gateway/`。
- 普通插件 27 个文件、原生 15 个载荷以及原生默认配置均只移动位置，内容未变；原生运行锁未改变。
- 原始 PVF 补丁、原始 SQL、历史插件导入清单、原生派生证据与来源说明按原字节归档到 `metadata/dof/source-audit/`。12 个免授权原件及其锁未改动。
- 正常安装拒绝原始材料，取消菜单自动派生及旧 `quick-runtime` 复用。维护者仍可通过显式导入工具重新派生。
- 构建只要求 421 个公共输入、15 个正式原生载荷及匹配默认配置；`--audit` 单独核验 17 个原件／证据文件。
- 两个游戏 Dockerfile 只复制 DP2、Frida 子目录；默认和专用忽略规则均排除原生包和审计目录。原生载荷由实例安装器按清单复制，原件不进入运行目录。
- 正式打包与探针仍能读取已归档的派生证据；独立导入目录必须自带证据，不借用仓库记录。

## 本地结果

| 检查 | 结果与边界 |
|---|---|
| 通用单元测试 | 284 项：266 通过、18 跳过 |
| 原生工具单元测试 | 88 项：78 通过、10 跳过 |
| 固定资源与归档 | 421 公共输入、15 原生载荷、5 默认配置、17 归档文件校验通过 |
| 重新派生 | 原件产生的 15 个载荷和 5 个配置与入库资源逐字节一致，原件哈希未变 |
| 正式安装边界 | 缺少整个审计目录仍可校验安装；原件、损坏载荷被拒绝；既有 quick-runtime 不成为回退 |
| 插件配置 | 使用仓库真实文件验证 none、dp2、frida、dp2-frida 四种准备流程；未执行插件本机代码 |
| 正式资源包 | 校验新路径、来源证据与载荷，原始程序／私人文件不进入包；releaseReady 保持 false |
| Docker 资源边界 | 本地可丢弃 Debian 12 VM 中，两种 Dockerfile 的 COPY/忽略规则均通过；各核对 27 个插件文件 |
| 干净 Git 检出 | core.autocrlf=true 检出后，资源及审计哈希通过；21 项安装/派生/菜单测试通过 |
| 文档与脚本 | 修改的 Markdown 链接、部署文档链接测试、Bash 语法检查通过 |
| 完整游戏镜像构建、真实加载与客户端进游戏 | 本轮未执行；不得把资源整理结果当成新的游戏验收 |

跳过项需要独立原生/客户端材料或特定运行环境，仍记为跳过。历史真实加载、网页、游戏证据不改写为本轮运行结果。

## 复现与恢复

```bash
python3 scripts/container/resources.py --audit
python3 -m unittest discover -s tests/unit -p 'test_*.py'
python3 -m unittest discover -s tests/unit/dof -p 'test_*.py'
python3 -m unittest tests.structure.test_deploy_layout.DeploymentLayout.test_document_links_resolve
# 仅在自己可丢弃的本地 Docker 环境执行；需要预先存在 busybox:1.36
python3 tests/integration/resource_docker_probe.py --report /tmp/dof-resources-result.json
```

Docker 探针复制真实正式资源，并使用两个生产 Dockerfile 的插件 COPY 行及其有效忽略规则。以预载 BusyBox 为基础，检查构建上下文排除原生／审计材料，并逐一校验镜像内 27 个插件文件；不执行游戏程序、不下载依赖。它不是完整游戏镜像构建。

已有实例的数据目录、密钥、插件修改和实际加载路径未改变，无需数据库迁移。撤销此次源码提交可恢复旧仓库资源布局；没有需要恢复的服务器变更。新的正式目录与兼容路径见 [资源布局](../resource-layout.md)。

结构化结果及相关输入摘要见 [验证记录](unified-server-resources.json)。测试 VM 已关机，测试镜像已移除。
