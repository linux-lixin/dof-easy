# 固定免授权原始输入

`files/` 收录 replacement.lock.json 锁定的 12 个文件：3 个免授权替换程序和 9 个 2.8 配套依赖库。原文件名仅用于来源审计和显式重新派生，不作为部署运行名称。未收录授权版回退程序。

默认部署使用 [正式资源目录](../../../../services/game/native-assets/README.md)。需要复现时，在仓库根目录执行（输出目录必须不存在）：

```sh
python3 scripts/dof/import_assets.py \
  --source metadata/dof/source-audit/server-inputs/files \
  --output /tmp/dof-runtime-rebuilt
python3 scripts/container/resources.py
```

派生输出应与 `services/game/runtime/ubuntu/gateway/runtime.lock.json` 逐文件一致；`tests/unit/test_bundled_native_assets.py` 验证这一关系。保留原件哈希、作者归属和 [来源说明](NOTICES.txt)，不因改名声称自研。
