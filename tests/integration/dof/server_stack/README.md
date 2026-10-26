# 本地服务端原生验证

这是诊断用的完整进程组合，尚不是正式安装包。只能通过
`scripts/dof/local_environment.py run` 在本机独立网络／PID 命名空间中执行；
不要在宿主机直接运行 `stack-probe.sh`，也不要传入现有服务器材料或连接参数。

## 输入与执行

先按 `metadata/dof/server-probe.lock.json` 准备本地材料。
该清单锁定 542 个文件，包含真实游戏、PVF、文本资源、依赖库、SQL 种子、历史诊断输入及固定桥接材料。实际运行的网关／插件／内联库从 `--runtime` 正式清单取得，并通过生产 `assets.install`、`service.prepare/run` 和 `run-game` 加载。
每次派生改变都必须更新正式清单并重新执行相关验收，不能沿用旧报告。
执行入口不会下载材料，也不接受服务器地址。

```sh
python3 scripts/dof/stage_server_probe.py \
  --source /local/verified-inputs \
  --output /local/new-probe-inputs \
  --runtime /local/derived-server-runtime

python3 scripts/dof/local_environment.py run \
  --workspace /local/sealed-linux-environment \
  --inputs /local/new-probe-inputs \
  --output /local/new-run \
  --timeout 600 -- /bin/sh /inputs/stack-probe.sh
```

源目录和输出必须分离，输出必须不存在。准备工具先校验全部哈希，再复制允许的文件；
不复制输入目录中额外的私钥或未知文件。测试启动时生成自己的 RSA 密钥、数据库密码和配置。
Linux 环境须包含 amd64/i386 依赖以及已声明的私有 `/dp2` 挂载点。
环境工具在前后核验根文件系统和输入，任意变化会使本轮证据失效。

## 证据范围

- `native-observations.json` 记录真实登录状态初始化、组合模块、未解析符号、团本配置及退出结果。
- 团本检查调用真实配置初始化函数，核对文件访问和 UTF-8 内存字节；不是团本玩法验收。
- 数据库检查只证明本轮实际请求没有旧库／配置表名，不证明所有潜在分支已完成改名。
- 随机盒检查构造真实 `CUser`，在新测试数据库写入三条记录（两个不同物品编号），调用原生 `DB_randombox` 的第 5 分支。直接读取其专用连接的查询缓冲区和结果行数，并与 MySQL 日志交叉验证；必须使用 `dof_native.randombox`、返回两个编号，且源数据仍为三条。测试结束后清理记录并析构用户；这不是玩家抽奖、奖励发放或背包验收。
- 该随机盒分支通过保护虚拟机到达 `MySQL::set_query`，专用连接位于 `DBMgr+0x40`，普通配置连接位于 `+0x18`，不能通过省略库名依赖默认连接。另有 `native_random_box_oracle.py` 从完整函数入口验证第 3、4、5 分支及两个加载基址。
- 装备广播分别检查导出解析与真实派发。`server_equipment_probe.js` 用实际 PVF 中的史诗／普通装备、真实构造的 CUser 和临时角色资料，检查原生入口、重复／普通／无效物品拒绝，以及拾取完成钩子。为避开旧 Gadget 的嵌套观察抑制，使用临时原生入口观察器记录公告头、文本与频道发送参数，原指令继续执行；拾取完成调用在后续游戏线程的原生执行中进行。测试不更改掉落或背包，也不声称玩家登录、完整拾取或客户端显示已通过。观察器不进入正式资源包。
- 广播记录保留 `put_binary` 收到的 UTF-8 原始字节，JSON 将非 ASCII 字符转义，避免原生日志层把中文替换为问号。`equipment_probe_layout.cjs` 可用 Node 导出观察器指令；`native_equipment_probe_oracle.py --layout <导出 JSON> --report <结果 JSON>` 用 Unicorn 检查栈对齐、寄存器／标志位和线程限制，不运行原生样本。
- 正常停止要求登录状态初始化后的停止请求及被测游戏退出码均有证据。超时、强杀或外层诊断脚本成功均不算正常停止。
- 首次正常停止后，保留网关、桥接、安全服务、密钥和数据库再次启动游戏。`first-start.json`、`first-start/`、`native-observations.json`、`restart.json` 分别保存两次观察和重启结论；这不是整套服务重启或玩家重连验收。
- 两次游戏周期之后还会停止网关管理进程、复用数据库和密钥重启，并再次正常停止；结果保存在 `gateway-restart.json`。
- 配置读取、真实构造和模块加载不代表完整登录、业务功能、全栈重启、数据库迁移、镜像安装或版本矩阵通过。

报告保留 `releaseReady=false`。任何必须检查失败都会返回非零状态。
日志及数据库包含新生成的测试秘密，应留在本地验证目录，不能进入公开报告和安装包。
公开摘要仅保存哈希、检查结果、符号名及必要路径。

本轮用于验证的 `probe-source.json` 绑定测试脚本版本。修改探针后需准备新的输入目录；
不要在测试进行时改写已使用的输入或覆盖旧报告。
