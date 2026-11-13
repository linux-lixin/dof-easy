# 部署模板

[根 Compose](../compose.yaml)提供游戏与密钥初始化，安装器生成实例专属配置及外置数据库工具。不启动内置数据库或 Java/Vue 后台。

使用[安装教程](../docs/operations/qingkong-baota.md)，不要直接在根目录运行 compose up；实例路径、数据库身份、密钥与晴空原包由安装器准备。

- [密钥初始化](bootstrap/README.md)
- [数据库兼容种子](database/README.md)
