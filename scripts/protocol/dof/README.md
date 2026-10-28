# 原生协议探测与私有输入准备

用途与读者：原生配套开发者。这里存显式协议检查、客户端输入准备和受控探测，不属于正常安装器运行依赖。

[requirements.txt](requirements.txt)固定离线模板导入依赖 cryptography；在私有 Python 环境安装，不把私有原件放进源码目录。固定来源输入、命令参数和输出见[隔离后台指南](../../../docs/operations/isolated-admin.md)的 prepare_client_inputs 步骤与[客户端格式](../../../docs/reference/native/client-protocol.md)。查看工具参数可运行 `python scripts/protocol/dof/prepare_client_inputs.py --help`；不启动任何 EXE。

输入准备读取原始配套、公钥、UUID 和模板，输出私有网页输入归档；探测脚本可能连接目标或生成私有证据，逐个查阅源码与参数，仅对授权隔离目标运行，不能批量执行整个目录。成功仅覆盖该工具报告的协议/文件条件，不等于真实组件消费。失败保留私有输入并核对来源锁，不能绕过哈希接受未知版本。
