# 登录模式与自有登录器兼容

晴空模式使用配套晴空启动器和 DLL；网页生成入口、PVF 加密及隔离部署见 [发布说明](../dof/publication.txt)。本分支不包含自有 Windows 登录器的改造，两个客户端协议不互通。

自有登录器管理接口保留，新增全量及增量资源 SHA-256、数字点分版本比较、默认关闭的增量开关。网页会显示当前登录模式，避免把自有登录器设置误认为晴空设置。

现有数据库升级前备份并停止目标隔离实例的 admin 服务，再运行 `python3 scripts/operations/migrate_launcher_checksum.py --instance 实例名`。脚本只为 `CLIENT_LAUNCHER_CONFIG`、`CLIENT_LAUNCHER_VERSION` 添加可空 `SHA256` 字段，先保存独立备份，重复运行不重复添加。不要把 `services/admin/database/init.sql` 当升级脚本，它包含重置语句。

更新元数据和下载地址应走可信加密通道；文件哈希用于完整性检查，不能单独证明发布者身份。晴空包的生成、参数应用和游戏验证分别记录，生成成功不等于已登录游戏。
