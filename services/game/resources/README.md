# 晴空兼容资源

`plugins/frida/frida.so` 用于晴空插件连接项目数据库时的凭据适配。它属于运行兼容依赖，不是可选玩法；不加载旧 `frida.js`，也不包含普通 DP2/Frida 玩法资源。

原版晴空插件和团本配置由私有输入提供，按[输入清单](../runtime/ubuntu/qingkong/inputs.lock.json)校验。共享构建输入仍受[资源锁](../../../metadata/build-inputs.lock.json)约束。

[游戏服务源码](../README.md) · [第三方说明](../THIRD_PARTY_NOTICES.txt)
