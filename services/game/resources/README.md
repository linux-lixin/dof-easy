# 服务端正式资源

- `plugins/dp2/`：DP2 库、Lua 和辅助脚本。
- `plugins/frida/`：Frida Gadget、JS 和无实例密码的模板。
- `plugins/raid/config/`：安徒恩、卢克团本配置模板，默认关闭团本，开启需要 DP2。
- `native/`：固定免授权材料派生的 dof 网关、插件、内联库、依赖和默认配置。

Dockerfile 明确复制 DP2、Frida 和团本模板三个子目录，是否启用由实例开关控制。团本共用原生插件库，不重复存放二进制。安装时将团本模板写入原生读取位置 `/data/dof-easy/config/anton-raid.ini`、`luke-raid.ini`，已有实例配置保持不变。原生载荷通过安装器逐文件校验并复制到实例数据，再由容器装入 `/opt/dof-easy/`；不直接烘入共享游戏镜像。

安装自动使用本目录，无需原始材料或资源目录输入。DP2 的配套 PVF 补丁也包含在 `plugins/dp2/pvf-patch.zip`，仅选装 DP2 时复制到实例；应用到 PVF 仍需按实际版本处理。详细的仓库、镜像和运行路径对应见 [资源布局](../../../docs/resource-layout.md)。
