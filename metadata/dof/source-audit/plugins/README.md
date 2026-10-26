# 普通插件原始材料

- `import-manifest.json` 原样保留旧导入记录；其中相对路径是当时导入输出的布局，不是当前部署入口。
- `dp2-pvf-patch.zip` 对应旧记录的 `archive/dp2-pvf-patch.zip`，未自动应用到游戏 PVF。
- `original-seed.sql` 对应旧记录的 `seed/vipai.sql`，仅作原始 SQL 审计。当前初始化用 `deploy/vipai86/db/vipai.sql`，两者不能互换。

DP2、Frida 的部署文件在 `services/game/resources/plugins/`，当前哈希由 `metadata/build-inputs.lock.json` 固定。旧清单记录的源压缩包及作者信息保持真实；不表示那些原始压缩包已全部入库。不要把原始补丁、SQL 或整个审计目录复制进镜像或实例。
