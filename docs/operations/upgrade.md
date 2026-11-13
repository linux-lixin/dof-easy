# 升级与分支

main 只维护晴空原版和外置 MySQL；旧 dof/native 或 internal 数据库实例会被拒绝，不会自动转换。需要旧产品时继续使用 [旧 main 备份](https://github.com/linux-lixin/dof-easy/tree/backup/main-before-qingkong-20261112) 或 [晴空接入前的混合模式分支](https://github.com/linux-lixin/dof-easy/tree/qingkong-baota-20261112)。

已运行的晴空实例先做[备份](backup-restore.md)，记录当前源码提交与镜像标签。更新源码本身不会重启服务器；只有显式执行安装/重启操作才影响实例。构建新标签时只生成 game 镜像，不创建新数据库。

```bash
sudo bash dof.sh build --tag qingkong-local
sudo bash dof.sh install --instance qingkong86 --offline --tag qingkong-local
sudo bash dof.sh status --instance qingkong86
```

已有实例沿用其等级、晴空输入、数据库身份和密钥。不要删除实例目录重跑首次初始化。镜像构建成功后再安排服务维护窗口；回退时使用原源码和保留的原镜像，并保持数据兼容。当前发布不承诺旧多模式实例的一键迁移。
