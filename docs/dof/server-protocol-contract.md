# 当前服务端协议名称与客户端验证交接

本页记录固定免授权输入派生出的当前服务端名称，供下一阶段客户端对接。名称的原生指令引用已检查；本页不表示客户端读取端或实际登录已经验证。

| 类别 | 当前名称 |
|---|---|
| 原生控制帧 | `<dof>…</dof>`；开头 5 字节、结尾 6 字节 |
| 机器人命令 | `dofRobotc` |
| 配置及角色标识符 | `dof_config`、`dof_CID` |
| 其余原生注册／读取标识符 | `dofPassword`、`loginSysDof`、`pushOpenTextDof`、`titlesDof`、`listDollDof`、`msgCreateDof`、`msgRemoveDof`、`msgLoginDof`、`msgLogoutDof`、`msgMoveDof`、`msgPublicMsgDof`、`dofGlobal` |
| 原生数据库 | `dof_native`；配置表 `config`、`config_v2`；随机盒 `randombox` |
| 游戏加载库 | `libdof-plugin.so`、`libdof-inline.so` |
| 服务端 PVF 标签 | `[dof rarity color]`、`[dof randombox]` |

`dofPassword` 是标识符名称的修改，不代表改写实际密码或密钥。其余项目也不因改名而自动改变数据编码、加密算法或业务含义。具体引用、旧新对应关系及指令原文仅在来源审计和确定性派生记录中保存。不得把旧协议的成功结果当作新协议的通过证据。

下一阶段使用配套开发中的客户端，在本地完成：

1. 登录、选角、进入游戏和新协议交互。
2. 玩家实际拾取装备，检查入包、广播接收、内容和显示；对照普通装备、重复事件及限流条件。
3. 随机盒实际奖励入包与基础交互。
4. 安徒恩、卢克两个团本的实际流程。

这些项目统一为“下一阶段待验证”。服务端已完成的构造角色、原生组包／频道发送函数和随机盒查询检查，不能代替上述玩家流程。本轮未修改客户端和后台代码；测试 PVF 中的客户端旧资源路径也未纳入服务端名称完成结论。数据库迁移不适用，服务端启动采用用户确认的正常状态。
