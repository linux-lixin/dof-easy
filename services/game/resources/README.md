# 服务端正式资源

- `plugins/dp2/`：DP2 库、Lua 和辅助脚本。
- `plugins/frida/`：Frida Gadget、JS 和无实例密码的模板。
- `native/`：固定免授权材料派生的 dof 网关、插件、内联库、依赖和默认配置。

普通插件由 Dockerfile 明确复制 DP2、Frida 两个子目录。原生载荷通过安装器逐文件校验并复制到实例数据，再由容器装入 `/opt/dof-easy/`；不直接烘入共享游戏镜像。

原始材料和来源记录在 [来源审计](../../../metadata/dof/source-audit/README.md)，不得从那里部署。详细的仓库、镜像和运行路径对应见 [资源布局](../../../docs/resource-layout.md)。
