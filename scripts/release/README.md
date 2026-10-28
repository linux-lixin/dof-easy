# 后台镜像发布工具

用途与读者：镜像发布维护者。适用后台源码镜像发布，不用于原生客户端包，也不部署现有实例。

[admin-image.sh](admin-image.sh)从仓库计算 `services/admin` 构建上下文，使用当前 Buildx 默认架构、标签 latest。输入为交互填写的镜像仓库；脚本确认后调用 `docker buildx build --push`，直接上传镜像，需 Docker、Buildx、依赖网络和目标仓库登录权限。没有无副作用的 `--help` 模式，不能为查看帮助直接运行。

示例（仅确需发布时，仓库根目录）：`bash scripts/release/admin-image.sh`。确认前核对目标仓库、提交、架构与标签；成功还须记录远端 digest，不能只看本地提示。失败不代表镜像完整发布，应查询目标仓库再重试。

只构建使用 `bash dof.sh build`，不调用发布脚本。完整来源与标签规则见[镜像指南](../../docs/operations/images.md)，类型检查/打包边界见[开发指南](../../docs/development/admin-packaging.md)。
