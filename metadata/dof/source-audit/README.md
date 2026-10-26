# 原始材料与来源审计

这里保存真实来源名称、版权、原始字节和历史证据，不是部署资源目录。正常安装只使用 [正式资源](../../../services/game/resources/README.md)。

| 目录 | 内容 |
|---|---|
| `server-inputs/files/` | 12 个固定免授权输入，保留原名；由 `replacement.lock.json` 锁定 |
| `server-derived/` | 已入库正式载荷的派生记录、第三方来源说明 |
| `plugins/` | 普通插件历史导入清单、未应用 PVF 补丁和原始 SQL |
| 其余已有文件 | 原生分析、探针、历史来源证据，保留原样 |

`resources.lock.json` 固定本次分离的 5 个证据文件。`python3 scripts/container/resources.py --audit` 除验证部署资源外，额外核验这 5 个文件及 12 个免授权原件。正常构建不依赖本目录。

原始文件名含旧品牌是来源事实；不改写版权，不重新哈希伪装为自研。正式资源的布局与实际运行路径见 [资源布局](../../../docs/resource-layout.md)。
