# 重组前原文快照

历史档案：原文取自 `ecd4505d1ebdd28486b13a718cd991874efbe96f` 的 `docs/dof/protocol.txt`；其中“当前”、镜像和待部署结论仅适用于原段落批次。当前操作请从[文档首页](../../README.md)进入。

原生客户端配套生成：接口、格式与验收边界

范围
网页 admin 已实现固定免授权配套的配置生成和 PVF 编码。当前生成端仍使用原配套文件与字段；Windows 新名读取端尚未完成，不可将旧格式包当成新协议交付。
当前结果以 status.md、web-integration-validation.json 为准，页面入口为 /admin/dof。
仅支持不压缩的原生 PVF 格式和手动安装。压缩、原生自动更新及完整后台替代未交付。
网页编解码在 Ubuntu 执行，不调用原 Windows 后台工具。Windows 登录器和加载 DLL 的实际读取必须单独验证；仅允许已绑定测试实例。
生成和参数持久化均不表示客户端已经验证成功。

已锁定的原始配套输入（并非新协议验收结果）
ijl15.dll SHA-256：d2b207e32f1d366c29f43d080366b7880d87659aaeb93a9bd8e36f44bf54b678
启动器.exe SHA-256：5b0c5195c58e1583cb5456e2b7139acf02f6ebb34ff86e4445468c91184be733
网页适配器：dof-native-1；模式 LAUNCHER_MODE=native
实例身份、公钥及原始配置从已锁定的私有 inputs 目录取得；不会生成新身份密钥。

网页流程
1. 确认页面显示 私有配置中的隔离实例ID 隔离实例。
2. 进入客户端生成功能，上传原始 Script.pvf，或导入当前实例只读绑定的 PVF；两种入口共用结构、哈希和容量检查。
3. 对上传编号创建不压缩加密任务。完成后选择该任务作为配套来源。
4. 使用已导入的格式 2 来源生成配套包；生成操作不改变正在使用的服务器参数。完整游戏读取仍按 client-reader-validation.md 单独验收。
5. 选择该配套包，预览应用。后端检查原始 PVF 与隔离游戏只读挂载的来源 SHA-256 一致。
6. 单独应用参数，再下载配套包；保留任务编号和 manifest.json。
7. 彻底退出独立客户端和启动器，按清单备份将被替换的运行文件，再安装配套包。
8. 使用已适配且经过实际读取验证的配套登录器登录。完成城镇、战斗、返城、退出重登后，才能记录游戏验证结果。

前次绑定 PVF 的导入为 READY，加密为 READY_FOR_TEST，applicationState=NOT_APPLIED；未执行上述参数应用和玩家验收。

接口
根路径 /api/v1/admin/dof/，所有接口使用现有管理员认证和隔离实例检查。
GET script：配套版本、来源修订、容量上限、模式和应用是否已配置。
GET/POST script/uploads：列表/原始流上传；POST 参数 requestId、revision，Content-Type=application/octet-stream，必须提供长度。
POST script/uploads/paired：JSON requestId、revision，从部署绑定的 DOF_GAME_PVF 只读复制为普通上传任务；请求不能指定文件系统路径。它不改写游戏 PVF、不应用参数。
GET script/uploads/{id}：上传状态。重复上传编号不可覆盖，先查询已有记录。
GET/POST script/tasks：列表/创建任务；JSON 包含 requestId、uploadId、revision。
GET script/tasks/{id}：进度与结果。GET script/tasks/{id}/download：已校验的加密文件。
GET/POST client/tasks：保留原登录器任务；可选 scriptTaskId 生成六文件配套包。
GET client/tasks/{id}/download：验证归档 SHA-256 后下载。
GET client/releases/status：配置版本及手动模式兼容状态，不返回解密参数。
GET client/releases：参数应用、核对和恢复历史。
POST client/releases/preview：requestId、revision、bundleId；只读核对来源与配置版本。
POST client/releases：同一请求单独应用两处参数。
POST client/releases/{id}/reconcile：遇断连先核对实际结果，不重新发送原生写操作。
POST client/releases/{id}/restore：新的 requestId、当前 revision；只恢复本次两处参数。

配套包现状与目标
格式 2 的五文件生成器输出 dof-launcher.exe、ijl15.dll、dof-client.dll、manifest.json、使用与恢复.txt；绑定 Script 任务时增加 Script.pvf，共六个文件。
配置使用 token_dof 系列，保留原容器格式；不生成或强制要求 token_dof_version=2。历史版本字段可作为可选 TXT 元数据读取，发布时移除。dof-client.dll 是配置容器，不是可执行 DLL。
来源绑定包含 clientFormatVersion、readerRecipe、当前数据库 UUID、公钥及所有输入哈希；数据库适配器仍为 dof-native-1。旧格式任务必须重新生成，不能作为新格式下载或参数应用来源。
配方 2.1 的新文件名与字段读取有历史真实进程检查；2.2 移除版本拒绝后需重新验证；游戏 DLL 完整消费、资源引用和 dofRobotc 控制交互仍须单独通过。详见 client-reader-validation.md。
manifest 记录实例、适配器、来源修订、任务编号、原始 PVF 哈希及其余文件大小和 SHA-256。
manifest 自身由整个 ZIP 的 SHA-256 保护。
内容参数不另行下载，数据库凭据、服务端身份私钥、后台工具不进入配套包。

原生格式与证据边界
原始头部保留 56 字节；先流式核对 GUID、版本、索引条目、资源范围和实际长度。
原始索引字按 rotateRight(word XOR indexCRC XOR 0x81a79011, 6) 解码。
不压缩输出索引再与“数值参数 XOR indexCRC”异或，资源段保持原值。
原生参数明文结构为 0##十进制数值；仅当前不压缩分支确认首字段为 0，不能推论压缩分支是布尔值。
新任务使用安全随机源选取支持范围内的数值，不声称复现原工具的时间随机源。
参数封装使用已锁定客户端 DLL 的固定格式上下文；内容参数保存在私有任务目录及确认的原生两处字段。
尾部为 1024 个零字节加 256 个小写字母，原生摘要为最后 100 字节的 MD5。
整文件 SHA-256 用于传输和存储验证，与原生摘要分开。
三个离线原生处理函数样本验证了算术、资源保持和尾部结构；这些合成样本不是可玩 PVF，也不构成游戏消费证明。
完整原始 PVF 的结构检查及对照样本另有测试。新产物最终仍须实际客户端消费和游戏验收。
当前原生 Scriptverification 和 updatec 必须已经为 0；本阶段不改动它们，也不生成自动更新元数据。

存储与容量
DOF_CLIENT_WORKSPACE=/app/data/dof-client-publication，须为私有持久化目录。
inputs 保存锁定版本来源；jobs 保存配套包；script/uploads 保存原始上传；script/tasks 保存加密产物和私有参数。
生成状态 READY_FOR_TEST 仅表示测试产物就绪；FAILED/INTERRUPTED 不可下载。
服务器每步需留 2 GiB 基础余量，另计算上传、加密或 ZIP 的峰值，预留并行小任务空间。
上传最多 Integer.MAX_VALUE-1280 字节，索引最多 256 MiB；错误结构、超长或截断输入拒绝。
生成先写 .part、校验并落盘，再原子改名；容器重建把未结束任务标记为 INTERRUPTED，不自动重跑。

参数应用与恢复
仅改动 dof_native.billing.decrypt、dof_native.config.pvfencryption。
原来两处参数不同也分别保存，不归一化恢复前值。
审计表 dnf_service.DOF_SCRIPT_RELEASE 使用 InnoDB；原生 MyISAM 表使用显式 LOCK TABLES。
锁内检查版本，记录 PREPARED 前值，逐字段写入后回读；普通事务不能代替该流程。
PERSISTED 表示已经保存并核对数据库，不代表 dof-gateway 已刷新或游戏已经读到。
断连时保留请求编号，使用 reconcile 区分未写入、部分写入、已保存；禁止盲目重发。
恢复时只改动本次涉及且仍符合预期的字段；发现第三方修改即拒绝覆盖。
不修改服务器 PVF、不初始化玩家库、不恢复整库，不覆盖后续玩家进度。

部署与恢复详见 deployment.txt；真实任务、参数及账号留在私有目录。签到记录 signin-users 的当前 HTTP 409 另列于 status.md，不用客户端任务成功掩盖此未完成项。
