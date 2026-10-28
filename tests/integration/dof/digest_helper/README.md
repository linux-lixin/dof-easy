# 原生摘要库的独立验证

只验证本项目自有的 i386 共享库，不加载游戏、插件或其他原生样本，也不连接服务器。
先把本目录的 `probe.c`、`test.py` 与 `services/game/docker/ubuntu/admin-runtime-digest.c`
复制到新的私有输入目录，再通过 `scripts/dof/local_environment.py run` 运行
`python3 /inputs/test.py`。使用已经封存的 Linux 环境，并把输出放在 Linux 文件系统中。

验证真实的 `.so` 导出、版本接口、15 个 SHA-256 向量（包含分块边界、NUL，以及与锁定插件大小相同的 9193440 字节自造数据）
以及 6 个非法描述符状态/输入条件。结果记录源文件哈希、实际 ELF 哈希和编译器版本。
这不是原生插件、网页任务或游戏效果的验收；相应验证另由完整隔离栈完成。
