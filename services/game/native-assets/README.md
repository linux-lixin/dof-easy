# 仓库内置原生服务端资源

本目录是默认部署资源，随 Git 检出提供，不需要另外上传免授权材料。安装菜单资源目录直接回车；高级命令使用 `--launcher native` 时可省略 `--dof-assets`。

`manifest.json` 与 [正式锁](../runtime/ubuntu/gateway/runtime.lock.json) 完全一致。网关、插件、内联库及依赖只从固定免授权原件派生；安装前继续检查哈希、大小和架构。

原件保存在 [来源审计目录](../../../metadata/dof/source-audit/server-inputs/README.md)，复现过程见那里。`source-audit` 保存派生记录和来源说明；不会被安装为游戏运行依赖。默认配置来自项目，不包含实例秘密。运行时密钥另行生成，PVF 仍由操作者提供。

提交这些文件不代表客户端实际进游戏通过，验收边界见 [当前状态](../../../docs/dof/status.md)。
