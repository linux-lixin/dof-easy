# Java 后端

此目录是 Maven 工程根目录。`src/main/java` 保存 API 和业务代码，`src/main/resources` 保存运行配置与模板。本轮整理不更改 Java 包名、依赖或业务逻辑。

完整后台镜像从仓库根目录构建，包含前端和后端：

```sh
docker build -f services/admin/docker/Dockerfile -t dnf-admin:latest services/admin
```

单独开发后端时，在本目录使用已安装的 Maven 执行 `mvn package -DskipTests`。详细环境说明见[后台开发与部署](../../../docs/admin/README.md)。不要将真实数据库口令或私钥写入资源文件。
