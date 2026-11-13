# 宝塔外置 MySQL

本分支只支持外置 **MySQL 5.6.x**。宝塔和 Docker 可以共存：安装器使用已有数据库服务，不安装数据库容器，也不替换宝塔 MySQL。请先在宝塔确认版本；MySQL 5.7/8.0、MariaDB 不会作为等价版本接受。

数据库地址填写宿主机可被 Docker 网段访问的私网 IPv4，不填容器中的 `127.0.0.1`。端口默认 3306；监听地址、防火墙和云安全组需允许指定 Docker 网段访问，玩家无需直连 3306。

交互菜单会探测本机 mysql 客户端及宝塔路径，并使用隐藏输入处理密码。命令行首次安装使用权限为 0600 的本机客户端配置，例如 `/private/mysql-admin.cnf`：

```ini
[client]
user=root
password=REPLACE_WITH_YOUR_PRIVATE_PASSWORD
socket=/tmp/mysql.sock
```

```bash
sudo chmod 600 /private/mysql-admin.cnf
```

对应参数为 `--mysql-config /private/mysql-admin.cnf --mysql-bin /www/server/mysql/bin/mysql`。不要把该文件放进仓库。管理员连接用于预检、建库和创建受限游戏账户；游戏容器不保存管理员凭据。安装器不会自动重置宝塔管理员密码。

初始化前检查版本、现有库和账号冲突，确认后创建游戏库与 `qk` 库，将权限限制到专用 Docker 子网。默认网段需以菜单确认和实例配置为准；可显式指定 `--db-allow-host 172.29.86.0/255.255.255.0`。已有业务库/账户冲突会退出，重启或升级不执行覆盖建库。

实例保存数据库 UUID，并在启动/备份前核对身份。地址、账号或 UUID 不一致时先核查连接，不删除标记绕过。完整命令见[晴空部署](qingkong-baota.md)。
