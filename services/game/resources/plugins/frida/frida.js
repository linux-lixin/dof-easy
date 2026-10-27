/**
 * 用于存放所有的和游戏相关的公共方法
 */
//获取系统时间
var CSystemTime_getCurSec = new NativeFunction(ptr(0x80CBC9E), 'int', ['pointer'], {"abi":"sysv"});
var GlobalData_s_systemTime_ = ptr(0x941F714);
//获取UTC时间(秒)
const getCurSec = new NativeFunction(Module.getExportByName(null, 'time'), 'int', ['pointer'], {"abi":"sysv"});
//从客户端封包中读取数据
var PacketBuf_get_byte = new NativeFunction(ptr(0x858CF22), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var PacketBuf_get_short = new NativeFunction(ptr(0x858CFC0), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var PacketBuf_get_int = new NativeFunction(ptr(0x858D27E), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var PacketBuf_get_binary = new NativeFunction(ptr(0x858D3B2), 'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});

//服务器组包
var PacketGuard_PacketGuard = new NativeFunction(ptr(0x858DD4C), 'int', ['pointer'], {"abi":"sysv"});
var InterfacePacketBuf_put_header = new NativeFunction(ptr(0x80CB8FC), 'int', ['pointer', 'int', 'int'], {"abi":"sysv"});
var InterfacePacketBuf_put_byte = new NativeFunction(ptr(0x80CB920), 'int', ['pointer', 'uint8'], {"abi":"sysv"});
var InterfacePacketBuf_put_short = new NativeFunction(ptr(0x80D9EA4), 'int', ['pointer', 'uint16'], {"abi":"sysv"});
var InterfacePacketBuf_put_int = new NativeFunction(ptr(0x80CB93C), 'int', ['pointer', 'int'], {"abi":"sysv"});
var InterfacePacketBuf_put_binary = new NativeFunction(ptr(0x811DF08), 'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});
var InterfacePacketBuf_finalize = new NativeFunction(ptr(0x80CB958), 'int', ['pointer', 'int'], {"abi":"sysv"});
var Destroy_PacketGuard_PacketGuard = new NativeFunction(ptr(0x858DE80), 'int', ['pointer'], {"abi":"sysv"});
var InterfacePacketBuf_clear =  new NativeFunction(ptr(0x080cb8e6), 'int', ['pointer'], {"abi":"sysv"});
var InterfacePacketBuf_put_packet = new NativeFunction(ptr(0x0815098e), 'int', ['pointer','pointer'], {"abi":"sysv"});
var PacketGuard_free_PacketGuard = new NativeFunction(ptr(0x0858de80), 'void', ['pointer'], {"abi":"sysv"});
var Packet_Monitor_Max_Level_BroadCast_Packet_Monitor_Max_Level_BroadCast = new NativeFunction(ptr(0x08694560), 'void', ['pointer'], {"abi":"sysv"});


//服务器环境
var G_CEnvironment = new NativeFunction(ptr(0x080CC181), 'pointer', [], {"abi":"sysv"});
//获取当前服务器配置文件名
var CEnvironment_get_file_name = new NativeFunction(ptr(0x80DA39A), 'pointer', ['pointer'], {"abi":"sysv"});
//获取GameWorld实例
var G_GameWorld = new NativeFunction(ptr(0x80DA3A7), 'pointer', [], {"abi":"sysv"});
//根据server_id查找user
var GameWorld_find_from_world = new NativeFunction(ptr(0x86C4B9C), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
//城镇瞬移
var GameWorld_move_area = new NativeFunction(ptr(0x86C5A84), 'pointer', ['pointer', 'pointer', 'int', 'int', 'int', 'int', 'int', 'int', 'int', 'int', 'int'], {"abi":"sysv"});
//根据账号查找已登录角色
var GameWorld_find_user_from_world_byaccid = new NativeFunction(ptr(0x86C4D40), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
//将协议发给所有在线玩家(慎用! 广播类接口必须限制调用频率, 防止CC攻击)
//除非必须使用, 否则改用对象更加明确的CParty::send_to_party/GameWorld::send_to_area
var GameWorld_send_all = new NativeFunction(ptr(0x86C8C14),  'int', ['pointer', 'pointer'], {"abi":"sysv"});
var GameWorld_send_all_with_state = new NativeFunction(ptr(0x86C9184),  'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});
//获取在线玩家数量
var GameWorld_get_UserCount_InWorld = new NativeFunction(ptr(0x86C4550), 'int', ['pointer'], {"abi":"sysv"});
//在线玩家列表(用于std::map遍历)
var gameworld_user_map_begin = new NativeFunction(ptr(0x80F78A6), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var gameworld_user_map_end = new NativeFunction(ptr(0x80F78CC), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var gameworld_user_map_not_equal = new NativeFunction(ptr(0x80F78F2), 'bool', ['pointer', 'pointer'], {"abi":"sysv"});
var gameworld_user_map_get = new NativeFunction(ptr(0x80F7944), 'pointer', ['pointer'], {"abi":"sysv"});
var gameworld_user_map_next = new NativeFunction(ptr(0x80F7906), 'pointer', ['pointer', 'pointer'], {"abi":"sysv"});
var GameWorld_IsEnchantRevisionChannel =new NativeFunction(ptr(0x082343fc), 'int', ['pointer'], {"abi":"sysv"});
var GameWorld_getDungeonMinimumRequiredLevel = new NativeFunction(ptr(0x086c9076), 'int', ['pointer','int'], {"abi":"sysv"});
var GameWorld_send_user_dungeon_inout_message = new NativeFunction(ptr(0x086c8fc8), 'void', ['pointer','pointer','int','int'], {"abi":"sysv"});
var GameWorld_IsPvPSkilTreeChannel = new NativeFunction(ptr(0x0823441e), 'int', ['pointer'], {"abi":"sysv"});

var G_CGameManager = new NativeFunction(ptr(0x080cc18e), 'pointer', [], {"abi":"sysv"});
var CGameManager_GetPremiumLetheManager = new NativeFunction(ptr(0x08298e88), 'int', ['pointer'], {"abi":"sysv"});
var CEventManager_GetRepeatEvent = new NativeFunction(ptr(0x08115998), 'pointer', ['pointer','int'], {"abi":"sysv"});
var EventClassify_CEventScriptMng_process_level_up_reward = new NativeFunction(ptr(0x0810bf56), 'pointer', ['pointer','pointer','int'], {"abi":"sysv"});
var GuildParameterScript_getGuildLevelUpParam = new NativeFunction(ptr(0x08979648), 'pointer', ['pointer','int'], {"abi":"sysv"});
var GuildParameterScript_getGuildExpBook = new NativeFunction(ptr(0x08979672), 'int', ['pointer','int'], {"abi":"sysv"});


//获取DataManager实例 用于处理pvf的
var G_CDataManager = new NativeFunction(ptr(0x80CC19B), 'pointer', [], {"abi":"sysv"});
//从pvf中获取任务数据
var CDataManager_find_quest = new NativeFunction(ptr(0x835FDC6), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
//获取装备pvf数据
var CDataManager_find_item = new NativeFunction(ptr(0x835FA32), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
var CDataManager_get_level_exp =  new NativeFunction(ptr(0x08360442), 'int', ['pointer','int'], {"abi":"sysv"});
var CDataManager_getDailyTrainingQuest = new NativeFunction(ptr(0x083640fe), 'pointer', ['pointer','int'], {"abi":"sysv"});
var CDataManager_GetSpAtLevelUp = new NativeFunction(ptr(0x08360cb8), 'int', ['pointer','int'], {"abi":"sysv"});
var CDataManager_get_event_script_mng = new NativeFunction(ptr(0x08110b62), 'pointer', ['pointer'], {"abi":"sysv"});
var CDataManager_GetExpertJobScript = new NativeFunction(ptr(0x0822b5f2), 'pointer', ['pointer','int'], {"abi":"sysv"});
var CDataManager_get_dimensionInout = new NativeFunction(ptr(0x0822b612), 'int', ['pointer','int'], {"abi":"sysv"});

//通知客户端QuestPiece更新
var GET_USER = new NativeFunction(ptr(0x084bb9cf),  'int', ['pointer'], {"abi":"sysv"});
//给角色发消息
var CUser_SendNotiPacketMessage = new NativeFunction(ptr(0x86886CE), 'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});
//角色增加经验
var CUser_gain_exp_sp = new NativeFunction(ptr(0x866A3FE), 'int', ['pointer', 'int', 'pointer', 'pointer', 'int', 'int', 'int'], {"abi":"sysv"});
//发送道具
var CUser_AddItem = new NativeFunction(ptr(0x867B6D4), 'int', ['pointer', 'int', 'int', 'int', 'pointer', 'int'], {"abi":"sysv"});
//通知客户端道具更新(客户端指针, 通知方式[仅客户端=1, 世界广播=0, 小队=2, war room=3], itemSpace[装备=0, 时装=1], 道具所在的背包槽)
var CUser_SendUpdateItemList = new NativeFunction(ptr(0x867C65A), 'int', ['pointer', 'int', 'int', 'int'], {"abi":"sysv"});
// 通知客户端更新背包栏
var CUser_send_itemspace = new NativeFunction(ptr(0x865DB6C),  'int', ['pointer', 'int'], {"abi":"sysv"});
//获取玩家任务信息
var CUser_getCurCharacQuestW = new NativeFunction(ptr(0x814AA5E),  'pointer', ['pointer'], {"abi":"sysv"});
//任务相关操作(第二个参数为协议编号: 33=接受任务, 34=放弃任务, 35=任务完成条件已满足, 36=提交任务领取奖励)
var CUser_quest_action = new NativeFunction(ptr(0x0866DA8A),  'int', ['pointer', 'int', 'int', 'int', 'int'], {"abi":"sysv"});
//发包给客户端
var CUser_Send = new NativeFunction(ptr(0x86485BA), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
//设置GM完成任务模式(无条件完成任务)
var CUser_setGmQuestFlag = new NativeFunction(ptr(0x822FC8E),  'int', ['pointer', 'int'], {"abi":"sysv"});
//是否GM任务模式
var CUser_getGmQuestFlag = new NativeFunction(ptr(0x822FC8E),  'int', ['pointer'], {"abi":"sysv"});
//通知客户端更新已完成任务列表
var CUser_send_clear_quest_list = new NativeFunction(ptr(0x868B044),  'int', ['pointer'], {"abi":"sysv"});
//计算任务基础奖励(不包含道具奖励)
var CUser_quest_basic_reward = new NativeFunction(ptr(0x866E7A8),  'int', ['pointer', 'pointer', 'pointer', 'pointer', 'pointer', 'pointer', 'int'], {"abi":"sysv"});
//通知客户端QP更新
var CUser_sendCharacQp = new NativeFunction(ptr(0x868AC24),  'int', ['pointer'], {"abi":"sysv"});
//通知客户端QuestPiece更新
var CUser_sendCharacQuestPiece = new NativeFunction(ptr(0x868AF2C),  'int', ['pointer'], {"abi":"sysv"});
//获取角色状态
var CUser_get_state = new NativeFunction(ptr(0x80DA38C), 'int', ['pointer'], {"abi":"sysv"});
//通知客户端角色属性更新
var CUser_SendNotiPacket = new NativeFunction(ptr(0x867BA5C), 'int', ['pointer', 'int', 'int', 'int'], {"abi":"sysv"});
// 获取账号金库
var CUser_GetAccountCargo = new NativeFunction(ptr(0x0822fc22), 'pointer', ['pointer'], {"abi":"sysv"});
//重置异界/极限祭坛次数
var CUser_DimensionInoutUpdate = new NativeFunction(ptr(0x8656C12),  'int', ['pointer', 'int', 'int'], {"abi":"sysv"});
//道具是否被锁
var CUser_CheckItemLock = new NativeFunction(ptr(0x8646942), 'int', ['pointer', 'int', 'int'], {"abi":"sysv"});
// 设置用户最大等级 int为等级
var CUser_SetUserMaxLevel  = new NativeFunction(ptr(0x0868fec8), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
var CUser_CalcurateUserMaxLevel  = new NativeFunction(ptr(0x0868ff04), 'pointer', ['pointer'], {"abi":"sysv"});
//获取角色所在队伍
const CUser_GetParty = new NativeFunction(ptr(0x0865514C), 'pointer', ['pointer'], {"abi":"sysv"});
//获取角色扩展数据
const CUser_GetCharacExpandData = new NativeFunction(ptr(0x080DD584), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
//获取角色点券余额
var CUser_GetCera = new NativeFunction(ptr(0x080FDF7A), 'int', ['pointer'], {"abi":"sysv"});
//获取角色账号id
var CUser_get_acc_id = new NativeFunction(ptr(0x80DA36E), 'int', ['pointer'], {"abi":"sysv"});
//返回选择角色界面
var CUser_ReturnToSelectCharacList = new NativeFunction(ptr(0x8686FEE), 'int', ['pointer', 'int'], {"abi":"sysv"});
var CUser_SendCmdErrorPacket =new NativeFunction(ptr(0x0867bf42), 'int', ['pointer', 'int', 'uint8'], {"abi":"sysv"});
var CUser_CheckMoney = new NativeFunction(ptr(0x0866af1c), 'int', ['pointer', 'int'], {"abi":"sysv"});
var CUser_GetCharacExpandDataR = new NativeFunction(ptr(0x0828b5de), 'int', ['int','int'], {"abi":"sysv"});
var CUser_isGMUser = new NativeFunction(ptr(0x0814589c), 'int', ['pointer'], {"abi":"sysv"});
var CUser_onLevelUp =  new NativeFunction(ptr(0x0866311a), 'void', ['pointer'], {"abi":"sysv"});
var CUser_getHades  = new NativeFunction(ptr(0x08230800), 'pointer', ['pointer'], {"abi":"sysv"});
var CUser_check_level_up  = new NativeFunction(ptr(0x08662aea), 'int', ['pointer','int','int','int','int'], {"abi":"sysv"});
var CUser_gain_sp = new NativeFunction(ptr(0x0866a9a0), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_gain_sfp = new NativeFunction(ptr(0x0866aad2), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_history_log_sp = new NativeFunction(ptr(0x0866ac0e), 'pointer', ['pointer','int','int','int'], {"abi":"sysv"});
var CUser_history_log_sfp = new NativeFunction(ptr(0x0866acd0), 'pointer', ['pointer','int','int','int'], {"abi":"sysv"});
var CUser_GetTutorialSkipable  = new NativeFunction(ptr(0x084ecad4), 'int', ['pointer'], {"abi":"sysv"});
var CUser_UpdateTutorialSkipable  = new NativeFunction(ptr(0x08697318), 'int', ['pointer'], {"abi":"sysv"});
var CUser_update_charac_stat = new NativeFunction(ptr(0x086646c8), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_GetServerGroup = new NativeFunction(ptr(0x080cbc90), 'int', ['pointer'], {"abi":"sysv"});
var CUser_makeGuildLevelUpMessage =  new NativeFunction(ptr(0x08679754), 'void', ['pointer','int'], {"abi":"sysv"});
var CUser_getCurCharacQuestR= new NativeFunction(ptr(0x0819a8a6),  'pointer', ['pointer'], {"abi":"sysv"});
var CUser_UpdateUserInfo4Guild = new NativeFunction(ptr(0x0867cd20), 'void', ['pointer'], {"abi":"sysv"});
var CUser_get_charac_no =  new NativeFunction(ptr(0x0815741c), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_VerifyPresentAvengerTitle = new NativeFunction(ptr(0x0868b552), 'int', ['pointer'], {"abi":"sysv"});
var CUser_AddCurCharacMercenaryInfo = new NativeFunction(ptr(0x0868e596), 'pointer', ['pointer'], {"abi":"sysv"});
var CUser_decide_growth_power_reward_system = new NativeFunction(ptr(0x0868d780), 'int', ['pointer'], {"abi":"sysv"});
var CUser_ReCalcChattingEmoticon = new NativeFunction(ptr(0x08689a22), 'void', ['pointer'], {"abi":"sysv"});
var CUser_SendChattingEmoticon = new NativeFunction(ptr(0x08689b90), 'void', ['pointer'], {"abi":"sysv"});
var CUser_isAffectedPremium = new NativeFunction(ptr(0x080e600e), 'int', ['int', 'int'], {"abi":"sysv"});
var CUser_processNPCGiftOnLevelUp = new NativeFunction(ptr(0x0866407a), 'void', ['pointer'], {"abi":"sysv"});
var CUser_processLevelUpEventReward = new NativeFunction(ptr(0x08663cc0), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_processLevelUpEvent  = new NativeFunction(ptr(0x0869115a), 'int', ['pointer'], {"abi":"sysv"});
var CUser_incPlayExpAdd = new NativeFunction(ptr(0x0869729a), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
var CUser_CheckInTrade = new NativeFunction(ptr(0x080da2fe), 'uint16', ['pointer'], {"abi":"sysv"});
var CUser_getCurCharacTotalFatigue = new NativeFunction(ptr(0x08657766), 'int', ['pointer'], {"abi":"sysv"});
var CUser_IsGuildMaster =  new NativeFunction(ptr(0x08230172), 'int', ['pointer'], {"abi":"sysv"});
var CUser_GetGuildDBInfo =  new NativeFunction(ptr(0x08230164), 'pointer', ['pointer'], {"abi":"sysv"});
var CUser_CalLevelUpItemCheck = new NativeFunction(ptr(0x08689d06), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_CalLevelUpItemState =  new NativeFunction(ptr(0x08689d74), 'int', ['pointer', 'pointer', 'pointer','int','int'], {"abi":"sysv"});
var CUser_GetCurExpertJobLevel = new NativeFunction(ptr(0x0868bc7c), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_send_skill_info = new NativeFunction(ptr(0x0866c46a), 'void', ['pointer'], {"abi":"sysv"});
var CUser_make_basic_info = new NativeFunction(ptr(0x0865a44e), 'int', ['pointer','pointer','int'], {"abi":"sysv"});
var CUser_GetWarRoom = new NativeFunction(ptr(0x086551de), 'pointer', ['pointer'], {"abi":"sysv"});
var CUser_adjust_charac_stat = new NativeFunction(ptr(0x08664766), 'int', ['pointer'], {"abi":"sysv"});
var CUser_increase_status = new NativeFunction(ptr(0x086657fc), 'void', ['pointer', 'int'], {"abi":"sysv"});
var CUser_SendTagCharacInfo = new NativeFunction(ptr(0x086903f8), 'void', ['pointer'], {"abi":"sysv"});
var CUser_giveCharacLinkBonusExp= new NativeFunction(ptr(0x08652564), 'void', ['pointer', 'int'], {"abi":"sysv"});
var CUser_get_charac_no  = new NativeFunction(ptr(0x0864dfa0), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_RecoverFatigue =new NativeFunction(ptr(0x08657ada), 'int', ['pointer','int'], {"abi":"sysv"});
var CUser_SendFatigue =new NativeFunction(ptr(0x08656540), 'void', ['pointer'], {"abi":"sysv"});
var CUser_processLevelReward  = new NativeFunction(ptr(0x0868745e), 'pointer', ['pointer','int','int','int'], {"abi":"sysv"});
var CUser_givePvPSkillTree = new NativeFunction(ptr(0x08665400), 'int', ['pointer','int','int','int'], {"abi":"sysv"});
var CUser_rewardExp = new NativeFunction(ptr(0x0868b20c), 'void', ['pointer', 'int', 'int', 'int', 'pointer', 'pointer','int','int'], {"abi":"sysv"});


var CUserCharacInfo_setDemensionInoutValue = new NativeFunction(ptr(0x0822f184), 'int', ['pointer','int','int'], {"abi":"sysv"});
//获取角色名字
var CUserCharacInfo_getCurCharacName = new NativeFunction(ptr(0x8101028), 'pointer', ['pointer'], {"abi":"sysv"});
//获取角色上次退出游戏时间
var CUserCharacInfo_getCurCharacLastPlayTick = new NativeFunction(ptr(0x82A66AA), 'int', ['pointer'], {"abi":"sysv"});
//获取角色等级
var CUserCharacInfo_get_charac_level = new NativeFunction(ptr(0x80DA2B8), 'int', ['pointer'], {"abi":"sysv"});
//获取角色当前等级升级所需经验
var CUserCharacInfo_get_level_up_exp = new NativeFunction(ptr(0x0864E3BA), 'int', ['pointer', 'int'], {"abi":"sysv"});
//获取角色背包
var CUserCharacInfo_getCurCharacInvenW = new NativeFunction(ptr(0x80DA28E), 'pointer', ['pointer'], {"abi":"sysv"});
// 价差分解机用户的状态 参数 用户  239 背包类型 位置
var CUserCharacInfo_GetCurCharacExpertJob = new NativeFunction(ptr(0x822f8d4), 'int', ['pointer'], {"abi":"sysv"});
//设置幸运点数
var CUserCharacInfo_SetCurCharacLuckPoint = new NativeFunction(ptr(0x0864670A), 'int', ['pointer', 'int'], {"abi":"sysv"});
//获取角色当前幸运点
var CUserCharacInfo_GetCurCharacLuckPoint = new NativeFunction(ptr(0x822F828), 'int', ['pointer'], {"abi":"sysv"});
//设置角色属性改变脏标记(角色上线时把所有属性从数据库缓存到内存中, 只有设置了脏标记, 角色下线时才能正确存档到数据库, 否则变动的属性下线后可能会回档)
var CUserCharacInfo_enableSaveCharacStat = new NativeFunction(ptr(0x819A870), 'int', ['pointer'], {"abi":"sysv"});
//本次登录时间
var CUserCharacInfo_GetLoginTick = new NativeFunction(ptr(0x822F692),  'int', ['pointer'], {"abi":"sysv"});
//获取当前角色id
var CUserCharacInfo_getCurCharacNo = new NativeFunction(ptr(0x80CBC4E), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacMoney=  new NativeFunction(ptr(0x0817a188), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_GetCurCharacMaxEquipLevel = new NativeFunction(ptr(0x086467a0), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_SetCurCharacMaxEquipLevel = new NativeFunction(ptr(0x086467c2), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacSkillR = new NativeFunction(ptr(0x0822f130), 'pointer', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacSkillW = new NativeFunction(ptr(0x0822f140), 'pointer', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacR =  new NativeFunction(ptr(0x0854f718), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_get_charac_exp = new NativeFunction(ptr(0x084ec05c), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_setCurCharacExp =  new NativeFunction(ptr(0x0819a87c), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_addCurCharacExp =  new NativeFunction(ptr(0x086967be), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_incCurCharacLevel = new NativeFunction(ptr(0x08696762), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_setCurCharacExp = new NativeFunction(ptr(0x0819a87c), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_resetCharacFatigueGrownUpBuff = new NativeFunction(ptr(0x08696386), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacGrowType = new NativeFunction(ptr(0x0815741c), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_set_charac_fatigue_buf_bonus_exp =  new NativeFunction(ptr(0x08469a02), 'int', ['pointer', 'int'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacCreateTime = new NativeFunction(ptr(0x0822f202), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_ResetCurCharacDungeonPlayCount = new NativeFunction(ptr(0x086969fe), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_GetCurCharacExpertJobExp = new NativeFunction(ptr(0x08375026), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_GetCurCharacDungeonPlayCount = new NativeFunction(ptr(0x085bfc78), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_get_charac_job = new NativeFunction(ptr(0x080fdf20), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_get_pvp_grade =  new NativeFunction(ptr(0x0819ee4a), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_get_charac_job = new NativeFunction(ptr(0x080fdf20), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharSecondGrowType = new NativeFunction(ptr(0x0822f23c), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharFirstGrowType = new NativeFunction(ptr(0x08110c94), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_GetCurCharacExpertJobType =  new NativeFunction(ptr(0x0822f894), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_GetCurCharacSkillTreeIndex =new NativeFunction(ptr(0x0822f33c), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_setCurCharacFatigue = new NativeFunction(ptr(0x0822f2ce), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_add_guild_exp = new NativeFunction(ptr(0x08645c76), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_setCurCharacStamina = new NativeFunction(ptr(0x082f0914), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_IncreasePowerWarPoint =  new NativeFunction(ptr(0x08687efc), 'int', ['pointer','int'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacR = new NativeFunction(ptr(0x08120432), 'pointer', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacInvenR = new NativeFunction(ptr(0x080da27e), 'pointer', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_get_charac_guildkey = new NativeFunction(ptr(0x0822f46c), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacFatigue = new NativeFunction(ptr(0x0822f2ae), 'int', ['pointer'], {"abi":"sysv"});
var CUserCharacInfo_getCurCharacAddInfoRefW = new NativeFunction(ptr(0x086960d8), 'int', ['pointer'], {"abi":"sysv"});



var CInventory_GetInvenRef = new NativeFunction(ptr(0x84FC1DE), 'pointer', ['pointer', 'int', 'int'], {"abi":"sysv"});
//减少金币
var CInventory_use_money = new NativeFunction(ptr(0x84FF54C), 'int', ['pointer', 'int', 'int', 'int'], {"abi":"sysv"});
//增加金币
var CInventory_gain_money = new NativeFunction(ptr(0x84FF29C), 'int', ['pointer', 'int', 'int', 'int', 'int'], {"abi":"sysv"});
//获取角色当前持有金币数量
var CInventory_get_money = new NativeFunction(ptr(0x81347D6), 'int', ['pointer'], {"abi":"sysv"});
//获取时装管理器
var CInventory_GetAvatarItemMgrR = new NativeFunction(ptr(0x80DD576), 'pointer', ['pointer'], {"abi":"sysv"});
//背包中删除道具(背包指针, 背包类型, 槽, 数量, 删除原因, 记录删除日志)
var CInventory_delete_item = new NativeFunction(ptr(0x850400C), 'int', ['pointer', 'int', 'int', 'int', 'int', 'int'], {"abi":"sysv"});
var CInventory_GetInvenData = new NativeFunction(ptr(0x084fbf2c), 'int', ['pointer','int','pointer'], {"abi":"sysv"});
var CInventory_GetInvenSlot  = new NativeFunction(ptr(0x084fb918), 'pointer', ['pointer', 'int', 'int', 'int'], {"abi":"sysv"});
var CInventory_update_item= new NativeFunction(ptr(0x085000ae), 'int', ['pointer','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int'], {"abi":"sysv"});
var CInventory_insertItemIntoInventory =  new NativeFunction(ptr(0x08502d86), 'int', ['pointer','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int','int'], {"abi":"sysv"});
var CInventory_GetEventCoin  = new NativeFunction(ptr(0x08110c7a), 'int', ['pointer'], {"abi":"sysv"});
var CInventory_SetEventCoin = new NativeFunction(ptr(0x08110c86), 'pointer', ['pointer','int'], {"abi":"sysv"});
var CInventory_GetCoin = new NativeFunction(ptr(0x0822d68a), 'int', ['pointer'], {"abi":"sysv"});
var CInventory_SetCoin = new NativeFunction(ptr(0x0822d67c), 'pointer', ['pointer','int'], {"abi":"sysv"});
var CInventory_get_inven_slot_no= new NativeFunction(ptr(0x0850cd62), 'int', ['pointer', 'int'], {"abi":"sysv"});




// 分解机 参数 角色 位置 背包类型  239  角色（谁的） 0xFFFF
var DisPatcher_DisJointItem_disjoint = new NativeFunction(ptr(0x81f92ca), 'int', ['pointer', 'int', 'int', 'int','pointer','int'], {"abi":"sysv"});

var AccountCargoScript_GetCurrUpgradeInfo = new NativeFunction(ptr(0x088c80ba), 'int', ['pointer','int'], {"abi":"sysv"});
var CAccountCargo_CheckValidSlot = new NativeFunction(ptr(0x0828A554), 'int', ['pointer','int'], {"abi":"sysv"});
var CAccountCargo_ResetSlot = new NativeFunction(ptr(0x082898c0), 'int', ['pointer','int'], {"abi":"sysv"});
var CAccountCargo_CheckMoneyLimit =new NativeFunction(ptr(0x0828a4ca), 'int', ['pointer','uint'], {"abi":"sysv"});
var CAccountCargo_AddMoney = new NativeFunction(ptr(0x0828a742), 'pointer', ['pointer', 'uint'], {"abi":"sysv"});
var CAccountCargo_SendNotifyMoney = new NativeFunction(ptr(0x0828a7dc), 'pointer', ['int', 'int'], {"abi":"sysv"});
var CAccountCargo_SubMoney = new NativeFunction(ptr(0x0828a764), 'pointer', ['pointer', 'uint'], {"abi":"sysv"});
var CAccountCargo_GetItemCount =new NativeFunction(ptr(0x0828a794), 'int', ['pointer'], {"abi":"sysv"});
var CAccountCargo_GetMoney = new NativeFunction(ptr(0x0822f020), 'int', ['pointer'], {"abi":"sysv"});
var CAccountCargo_SetStable = new NativeFunction(ptr(0x0844dc16), 'pointer', ['pointer'], {"abi":"sysv"});
// 获取账号金库一个空的格子
var CAccountCargo_GetEmptySlot= new NativeFunction(ptr(0x0828a580), 'int', ['pointer'], {"abi":"sysv"});
// 将已经物品移动到某个格子 第一个账号金库，第二个移入的物品，第三个格子位置
var CAccountCargo_InsertItem = new NativeFunction(ptr(0x08289c82), 'int', ['pointer','pointer','int'], {"abi":"sysv"});
// 向客户端发送账号金库列表
var CAccountCargo_SendItemList = new NativeFunction(ptr(0x0828a88a), 'int', ['pointer'], {"abi":"sysv"});


//删除背包槽中的道具
var Inven_Item_reset = new NativeFunction(ptr(0x080CB7D8),  'int', ['pointer'], {"abi":"sysv"});
//检查背包中道具是否为空
var Inven_Item_isEmpty = new NativeFunction(ptr(0x811ED66), 'int', ['pointer'], {"abi":"sysv"});
//获取背包中道具item_id
var Inven_Item_getKey = new NativeFunction(ptr(0x850D14E), 'int', ['pointer'], {"abi":"sysv"});
//道具是否是装备
var Inven_Item_isEquipableItemType = new NativeFunction(ptr(0x08150812), 'int', ['pointer'], {"abi":"sysv"});
//背包道具
var Inven_Item_Inven_Item = new NativeFunction(ptr(0x80CB854), 'pointer', ['pointer'], {"abi":"sysv"});
//获取道具附加信息
var Inven_Item_get_add_info = new NativeFunction(ptr(0x80F783A), 'int', ['pointer'], {"abi":"sysv"});


var CItem_GetIndex = new NativeFunction(ptr(0x8110c48), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetGrade = new NativeFunction(ptr(0x8110c54), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetItemName = new NativeFunction(ptr(0x811ed82), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetPrice = new NativeFunction(ptr(0x822c84a), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetGenRate = new NativeFunction(ptr(0x822c84a), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetNeedLevel = new NativeFunction(ptr(0x8545fda), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetUsableLevel = new NativeFunction(ptr(0x80f12ee), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetRarity = new NativeFunction(ptr(0x80f12d6), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetAttachType = new NativeFunction(ptr(0x80f12e2), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetItemGroupName = new NativeFunction(ptr(0x80f1312), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetUpSkillType = new NativeFunction(ptr(0x8545fcc), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetGetExpertJobCompoundMaterialVariation = new NativeFunction(ptr(0x850d292), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetExpertJobCompoundRateVariation = new NativeFunction(ptr(0x850d2aa), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetExpertJobCompoundResultVariation = new NativeFunction(ptr(0x850d2c2), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetExpertJobSelfDisjointBigWinRate = new NativeFunction(ptr(0x850d2de), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetExpertJobSelfDisjointResultVariation = new NativeFunction(ptr(0x850d2f6), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetExpertJobAdditionalExp = new NativeFunction(ptr(0x850d30e), 'int', ['pointer'], {"abi":"sysv"});
//道具是否为消耗品
var CItem_is_stackable = new NativeFunction(ptr(0x80F12FA), 'int', ['pointer'], {"abi":"sysv"});
var CItem_isPackagable = new NativeFunction(ptr(0x0828b5b4), 'int', ['pointer'], {"abi":"sysv"});
var CItem_getUsablePeriod = new NativeFunction(ptr(0x08110c60), 'int', ['pointer'], {"abi":"sysv"});
var CItem_getExpirationDate = new NativeFunction(ptr(0x080f1306), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetIncreaseStatusIntData = new NativeFunction(ptr(0x08694658), 'int', ['pointer','int','pointer'], {"abi":"sysv"});
var CItem_GetIncreaseStatusType =  new NativeFunction(ptr(0x086946b6), 'int', ['pointer'], {"abi":"sysv"});
var CItem_GetUsablePvPRank = new NativeFunction(ptr(0x086946c4), 'int', ['pointer'], {"abi":"sysv"});



var CEquipItem_GetUsableEquipmentType = new NativeFunction(ptr(0x0832e036), 'int', ['pointer'], {"abi":"sysv"});
var CEquipItem_GetSubType = new NativeFunction(ptr(0x833eecc), 'int', ['pointer'], {"abi":"sysv"});
//是否魔法封印装备
var CEquipItem_IsRandomOption = new NativeFunction(ptr(0x8514E5E), 'int', ['pointer'], {"abi":"sysv"});
//获取装备魔法封印等级?
var CEquipItem_GetRandomOptionGrade = new NativeFunction(ptr(0x8514E6E), 'int', ['pointer'], {"abi":"sysv"});


var UserQuest_finish_quest = new NativeFunction(ptr(0x86AC854), 'int', ['pointer', 'int'], {"abi":"sysv"});
//通知客户端更新角色任务列表
var UserQuest_get_quest_info = new NativeFunction(ptr(0x86ABBA8), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
//重置所有任务为未完成状态
var UserQuest_reset = new NativeFunction(ptr(0x86AB894), 'int', ['pointer'], {"abi":"sysv"});
var UserQuest_get_mail_quest_info = new NativeFunction(ptr(0x086abd7a), 'int', ['int','int','pointer'], {"abi":"sysv"});
var UserQuest_ResetUrgentQuestWaitingList = new NativeFunction(ptr(0x086ad178),  'pointer', ['pointer'], {"abi":"sysv"});


//设置任务为已完成状态
var WongWork_CQuestClear_setClearedQuest = new NativeFunction(ptr(0x808BA78), 'int', ['pointer', 'int'], {"abi":"sysv"});
//重置任务为未完成状态
var WongWork_CQuestClear_resetClearedQuests = new NativeFunction(ptr(0x808BAAC), 'int', ['pointer', 'int'], {"abi":"sysv"});
//任务是否已完成
var WongWork_CQuestClear_isClearedQuest = new NativeFunction(ptr(0x808BAE0), 'int', ['pointer', 'int'], {"abi":"sysv"});
//点券充值
var WongWork_IPG_CIPGHelper_IPGInput = new NativeFunction(ptr(0x80FFCA4),  'int', ['pointer', 'pointer', 'int', 'int', 'pointer', 'pointer', 'pointer', 'pointer', 'pointer', 'pointer'], {"abi":"sysv"});
//代币充值
var WongWork_IPG_CIPGHelper_IPGInputPoint = new NativeFunction(ptr(0x80FFFC0),  'int', ['pointer', 'pointer','int', 'int', 'pointer', 'pointer'], {"abi":"sysv"});
//同步点券数据库
var WongWork_IPG_CIPGHelper_IPGQuery = new NativeFunction(ptr(0x8100790),  'int', ['pointer', 'pointer'], {"abi":"sysv"});
//获取时装插槽数据
var WongWork_CAvatarItemMgr_getJewelSocketData = new NativeFunction(ptr(0x82F98F8), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
//发系统邮件(多道具)
var WongWork_CMailBoxHelper_ReqDBSendNewSystemMultiMail = new NativeFunction(ptr(0x8556B68), 'int', ['pointer', 'pointer', 'int', 'int', 'int', 'pointer', 'int', 'int', 'int', 'int'], {"abi":"sysv"});
var WongWork_CMailBoxHelper_MakeSystemMultiMailPostal = new NativeFunction(ptr(0x8556A14), 'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});
//发系统邮件(时装)(仅支持在线角色发信)
var WongWork_CMailBoxHelper_ReqDBSendNewAvatarMail = new NativeFunction(ptr(0x85561B0), 'pointer', ['pointer', 'int', 'int', 'int', 'int', 'int', 'int', 'pointer', 'int'], {"abi":"sysv"});
var WongWork_CUserPremium_GetGoldBonus = new NativeFunction(ptr(0x08694a64), 'int', ['pointer','int'], {"abi":"sysv"});
var WongWork_CUserPremium_RecalcAdditionalInfo = new NativeFunction(ptr(0x086ae8c6), 'pointer', ['pointer','pointer'], {"abi":"sysv"});
var WongWork_CGMAccounts_isGM = new NativeFunction(ptr(0x08109346), 'int', ['pointer','int'], {"abi":"sysv"});
var WongWork_CSkillChanger_CheckCondition = new NativeFunction(ptr(0x08609d10), 'int', ['pointer'], {"abi":"sysv"});
var WongWork_CSkillChanger_d_CSkillChanger = new NativeFunction(ptr(0x08234fc4), 'void', ['pointer'], {"abi":"sysv"});
var WongWork_CSkillChanger_CSkillChanger = new NativeFunction(ptr(0x08234fbe), 'void', ['pointer'], {"abi":"sysv"});
var WongWork_CSkillChanger_SkillInitialize =new NativeFunction(ptr(0x08609e90), 'pointer', ['pointer','pointer','int','int'], {"abi":"sysv"});
var WongWork_CMailBoxHelper_ReqDBSendNewSystemMail = new NativeFunction(ptr(0x085555e8), 'int', ['pointer'], {"abi":"sysv"});


//检测当前角色是否可接该任务
var stSelectQuestParam_stSelectQuestParam = new NativeFunction(ptr(0x83480B4), 'pointer', ['pointer', 'pointer'], {"abi":"sysv"});
var Quest_check_possible = new NativeFunction(ptr(0x8352D86), 'int', ['pointer', 'pointer'], {"abi":"sysv"});

//获取副本id
var CDungeon_get_index = new NativeFunction(ptr(0x080FDCF0),  'int', ['pointer'], {"abi":"sysv"});




//解封魔法封印
var random_option_CRandomOptionItemHandle_give_option = new NativeFunction(ptr(0x85F2CC6),  'int', ['pointer', 'int', 'int', 'int', 'int', 'int', 'pointer'], {"abi":"sysv"});





var AvatarCoin_Add = new NativeFunction(ptr(0x0817fefa), 'int', ['pointer','int'], {"abi":"sysv"});
var AvatarCoin_SaveToDB = new NativeFunction(ptr(0x081800d6), 'int', ['pointer'], {"abi":"sysv"});
var AvatarCoin_SendSyncPacket = new NativeFunction(ptr(0x0817ffe4), 'int', ['pointer'], {"abi":"sysv"});
var AvatarCoin_HistoryLog_AddLog = new NativeFunction(ptr(0x0817ff9c), 'void', ['pointer','pointer'], {"abi":"sysv"});


var CPremiumLetheManager_InitLetheSkill = new NativeFunction(ptr(0x085c4008), 'int', ['int','pointer','int'], {"abi":"sysv"});
var CPremiumLetheManager_UpdateBackupSkillFlag = new NativeFunction(ptr(0x085c3f30), 'int', ['int','pointer','int'], {"abi":"sysv"});
var CPremiumLetheManager_ConfirmSkillReq =new NativeFunction(ptr(0x085c3d70), 'int', ['pointer','pointer'], {"abi":"sysv"});

var SkillSlot_get_remain_sp_at_index = new NativeFunction(ptr(0x08603528), 'int', ['pointer','pointer'], {"abi":"sysv"});
var SkillSlot_get_remain_sfp_at_index = new NativeFunction(ptr(0x086035f2), 'int', ['pointer','pointer'], {"abi":"sysv"});
var SkillSlot_growtype_skill = new NativeFunction(ptr(0x086040bc), 'int', ['pointer','pointer','int','int','int'], {"abi":"sysv"});
var SkillSlot_set_remain_sp_at_index = new NativeFunction(ptr(0x086034f8), 'int', ['int','int','int'], {"abi":"sysv"});
var SkillSlot_clear_sfp_skills = new NativeFunction(ptr(0x08604e78), 'int', ['int','int','pointer'], {"abi":"sysv"});
var SkillSlot_clear_all_skills = new NativeFunction(ptr(0x08604d90), 'int', ['pointer','int'], {"abi":"sysv"});
var SkillSlot_clear_all_skills_both =  new NativeFunction(ptr(0x08604e08), 'int', ['pointer'], {"abi":"sysv"});
var SkillSlot_set_parent = new NativeFunction(ptr(0x0822ee2e), 'pointer', ['pointer','pointer'], {"abi":"sysv"});
var addSkillOnCreateCharacter = new NativeFunction(ptr(0x08604fe2), 'void', ['int','int'], {"abi":"sysv"});
var CCharacter_get_give_skill = new NativeFunction(ptr(0x08348798), 'int', ['int','int','int','int','int'], {"abi":"sysv"});

var CQuestShop_clearQP = new NativeFunction(ptr(0x085ef54c), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var CQuestShop_sendCharacQp = new NativeFunction(ptr(0x085ef6fc), 'void', ['pointer', 'pointer','int'], {"abi":"sysv"});


//获取消耗品类型
var CStackableItem_GetItemType = new NativeFunction(ptr(0x8514A84),  'int', ['pointer'], {"abi":"sysv"});
//获取徽章支持的镶嵌槽类型
var CStackableItem_getJewelTargetSocket = new NativeFunction(ptr(0x0822CA28),  'int', ['pointer'], {"abi":"sysv"});


var CMonitorServerProxy_SendCharLevelGrowType = new NativeFunction(ptr(0x08470c04), 'int', ['pointer','int','int','int','int'], {"abi":"sysv"});
var CMonitorServerProxy_SendPacket = new NativeFunction(ptr(0x08470df4), 'int', ['pointer','pointer','int'], {"abi":"sysv"});
var CServerProxyMgr_CMonitorServerProxy_GetServerProxy = new NativeFunction(ptr(0x0811208a), 'pointer', ['pointer','int'], {"abi":"sysv"});
var CServerProxyMgr_CGuildServerProxy_GetServerProxy = new NativeFunction(ptr(0x0811d3b8), 'pointer', ['pointer','int'], {"abi":"sysv"});
var CGuildServerProxy_SendCharLevelGrowType = new NativeFunction(ptr(0x0846da9a), 'int', ['pointer','int','int','int','int'], {"abi":"sysv"});


//时装镶嵌数据存盘
var DB_UpdateAvatarJewelSlot_makeRequest = new NativeFunction(ptr(0x843081C), 'pointer', ['int', 'int', 'pointer'], {"abi":"sysv"});



//获取字符串长度
var strlen = new NativeFunction(Module.getExportByName(null, 'strlen'), 'int', ['pointer'], {"abi":"sysv"});

//线程安全锁
var Guard_Mutex_Guard = new NativeFunction(ptr(0x810544C), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var Destroy_Guard_Mutex_Guard = new NativeFunction(ptr(0x8105468), 'int', ['pointer'], {"abi":"sysv"});

//服务器内置定时器队列
var G_TimerQueue = new NativeFunction(ptr(0x80F647C), 'pointer', [], {"abi":"sysv"});



//执行debug命令
var DoUserDefineCommand = new NativeFunction(ptr(0x0820BA90), 'int', ['pointer', 'int', 'pointer'], {"abi":"sysv"});

//设置角色等级(最高70级)
var DisPatcher_DebugCommand__debugCommandSetLevel = new NativeFunction(ptr(0x0858EFDE), 'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});//需要临时开GM权限



//vector相关操作
var std_vector_std_pair_int_int_vector = new NativeFunction(ptr(0x81349D6), 'pointer', ['pointer'], {"abi":"sysv"});
var std_vector_std_pair_int_int_clear = new NativeFunction(ptr(0x817A342), 'pointer', ['pointer'], {"abi":"sysv"});
var std_make_pair_int_int = new NativeFunction(ptr(0x81B8D41), 'pointer', ['pointer', 'pointer', 'pointer'], {"abi":"sysv"});
var std_vector_std_pair_int_int_push_back = new NativeFunction(ptr(0x80DD606), 'pointer', ['pointer', 'pointer'], {"abi":"sysv"});
var vector_unsigned_int_operator=  new NativeFunction(ptr(0x0808e1dc), 'pointer', ['pointer','int'], {"abi":"sysv"});
var std_vector_std_pair_int_int_size = new NativeFunction(ptr(0x080dd814), 'int', ['pointer'], {"abi":"sysv"});
var std_vector_std_pair_int_int_operator = new NativeFunction(ptr(0x080ea8a4), 'pointer', ['pointer','int'], {"abi":"sysv"});
var std_vector_std_pair_int_int_d_vector = new NativeFunction(ptr(0x081349ea), 'void', ['pointer'], {"abi":"sysv"});
var std_vector_charac_info_size = new NativeFunction(ptr(0x081a0b9a), 'int', ['pointer'], {"abi":"sysv"});
var std_vector_Charac_info_operatorArr = new NativeFunction(ptr(0x081a0bb8), 'int', ['pointer','int'], {"abi":"sysv"});


var LogManager_logFormat = new NativeFunction(ptr(0x08ad3c0a), 'int', ['pointer','int','pointer','pointer','pointer','pointer','...','pointer'], {"abi":"sysv"});
var cUserHistoryLog_EventCoinAdd = new NativeFunction(ptr(0x08683c58), 'pointer', ['pointer','int','int','int'], {"abi":"sysv"});
var cUserHistoryLog_CoinAdd = new NativeFunction(ptr(0x08683b90), 'int', ['pointer','int','int','int'], {"abi":"sysv"});
var HistoryLog_WriteLevelUp = new NativeFunction(ptr(0x084b9e5e), 'int', ['pointer','pointer'], {"abi":"sysv"});
var cUserHistoryLog_LevelUp  = new NativeFunction(ptr(0x086845b2), 'int', ['pointer', 'int', 'int'], {"abi":"sysv"});
var cUserHistoryLog_ItemAdd = new NativeFunction(ptr(0x08682e84), 'int', ['int','int','int','int','pointer','int'], {"abi":"sysv"});



//开启怪物攻城
const Inter_VillageAttackedStart_dispatch_sig = new NativeFunction(ptr(0x84DF47A), 'pointer', ['pointer', 'pointer', 'pointer'], {"abi":"sysv"});
//结束怪物攻城
const village_attacked_CVillageMonsterMgr_OnDestroyVillageMonster = new NativeFunction(ptr(0x086B43D4), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
const GlobalData_s_villageMonsterMgr = ptr(0x941F77C);

//获取队伍中玩家
const CParty_get_user = new NativeFunction(ptr(0x08145764), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
var CParty_send_to_party = new NativeFunction(ptr(0x0859d14e), 'int', ['pointer','pointer'], {"abi":"sysv"});

//绝望之塔层数
const TOD_Layer_TOD_Layer = new NativeFunction(ptr(0x085FE7B4), 'pointer', ['pointer', 'int'], {"abi":"sysv"});
//设置绝望之塔层数
const TOD_UserState_setEnterLayer = new NativeFunction(ptr(0x086438FC), 'pointer', ['pointer', 'pointer'], {"abi":"sysv"});

var cMyTrace_cMyTrace = new NativeFunction(ptr(0x0854f718), 'void', ['pointer','pointer','int','int'], {"abi":"sysv"});
var cMyTrace_operator = new NativeFunction(ptr(0x0854f788), 'void', ['int','pointer','pointer'], {"abi":"sysv"});


var MsgQueueMgr_put =  new NativeFunction(ptr(0x08570fde), 'int', ['int','int','pointer'], {"abi":"sysv"});
var NumberToString= new NativeFunction(ptr(0x0810904b), 'uint', ['uint','int'], {"abi":"sysv"});

var GetIntegratedPvPItemAttr =new NativeFunction(ptr(0x084fc5ff), 'int', ['pointer'], {"abi":"sysv"});
var ARAD_Singleton_ServiceRestrictManager_Get = new NativeFunction(ptr(0x081625e6), 'pointer', [], {"abi":"sysv"});
var ServiceRestrictManager_isRestricted = new NativeFunction(ptr(0x0816e6b8), 'uint8', ['int','pointer','int','int'], {"abi":"sysv"});
var CSecu_ProtectionField_Check =  new NativeFunction(ptr(0x08288a02), 'int', ['pointer','pointer','int'], {"abi":"sysv"});

var stAmplifyOption_t_getAbilityType = new NativeFunction(ptr(0x08150732), 'uint8', ['pointer'], {"abi":"sysv"});
var stAmplifyOption_t_getAbilityValue = new NativeFunction(ptr(0x08150772), 'uint16', ['pointer'], {"abi":"sysv"});
var stAmplifyOption_t_GetLock =  new NativeFunction(ptr(0x0828b5a8), 'int', ['pointer'], {"abi":"sysv"});

var StreamPool_Acquire = new NativeFunction(ptr(0x0828fa86), 'pointer', ['pointer','pointer','int'], {"abi":"sysv"});
var CStreamGuard_CStreamGuard = new NativeFunction(ptr(0x080c8c26), 'void', ['pointer','pointer','int'], {"abi":"sysv"});
var CStreamGuard_operator =  new NativeFunction(ptr(0x080c8c46), 'int', ['int'], {"abi":"sysv"});
var CStreamGuard_operator_int = new NativeFunction(ptr(0x080c8c56), 'int', ['pointer','int'], {"abi":"sysv"});
var CStreamGuard_operator_p = new NativeFunction(ptr(0x080c8c4e), 'int', ['int'], {"abi":"sysv"});
var CStreamGuard_GetInBuffer_SIG_ACCOUNT_CARGO_DATA = new NativeFunction(ptr(0x08453a10), 'pointer', ['pointer'], {"abi":"sysv"});
var Destroy_CStreamGuard_CStreamGuard = new NativeFunction(ptr(0x0861c8d2), 'void', ['pointer'], {"abi":"sysv"});

var CStackableItem_getStackableLimit = new NativeFunction(ptr(0x0822c9fc), 'int', ['pointer'], {"abi":"sysv"});
var item_lock_CItemLock_CheckItemLock = new NativeFunction(ptr(0x08541a96), 'int', ['int','int'], {"abi":"sysv"});
var UpgradeSeparateInfo_IsTradeRestriction = new NativeFunction(ptr(0x08110b0a), 'int', ['pointer'], {"abi":"sysv"});
var Stream_operator_p = new NativeFunction(ptr(0x0861c796), 'int', ['pointer','int'], {"abi":"sysv"});
var Stream_GetOutBuffer_SIG_ACCOUNT_CARGO_DATA = new NativeFunction(ptr(0x08453a26), 'int', ['pointer'], {"abi":"sysv"});

var CGuildServerProxy_SendIncreaseGuildExp = new NativeFunction(ptr(0x0846ece2), 'int', ['pointer','int','int','int','int'], {"abi":"sysv"});
var PvPSkillTreeParameterScript_getPvPSkillPoint = new NativeFunction(ptr(0x08a5dd62), 'int', ['pointer','int','int','int','int','int'], {"abi":"sysv"});
var ServerParameterScript_isDungeonOpen  = new NativeFunction(ptr(0x082687fc), 'int', [], {"abi":"sysv"});
var XNuclear_CHades_ExpUp = new NativeFunction(ptr(0x084b953e), 'int', ['pointer','int'], {"abi":"sysv"});
var WarRoom_SendToRoom = new NativeFunction(ptr(0x086be0cc), 'int', ['pointer','pointer'], {"abi":"sysv"});

var CCharacterView_enableSaveCharacView =  new NativeFunction(ptr(0x0822fbda), 'pointer', ['pointer'], {"abi":"sysv"});
var CLevelDungeonPlayStatistic_IncreaseLevelDungeonPlay = new NativeFunction(ptr(0x0860ecc6), 'pointer', ['pointer','int','int'], {"abi":"sysv"});
var expert_job_CExpertJob_IncreaseExpertJobExp =  new NativeFunction(ptr(0x08375026), 'void', ['pointer','pointer'], {"abi":"sysv"});
var APSystem_CUserProc_ClearActionAndSendtoUser = new NativeFunction(ptr(0x08122390), 'void', ['pointer','int','int','int'], {"abi":"sysv"});
var DB_InsertUnlimitSupportLog_makeRequest = new NativeFunction(ptr(0x080cbc9e), 'void', ['pointer','int','pointer'], {"abi":"sysv"});
var DB_InsertArchieveEventLog_makeRequest = new NativeFunction(ptr(0x08115998), 'void', ['int','int','int'], {"abi":"sysv"});
var RDARScriptStringManager_findString = new NativeFunction(ptr(0x08aa57fe), 'pointer', ['pointer','int','pointer','int'], {"abi":"sysv"});
var ImportSpPerLevelReferenceTable =  new NativeFunction(ptr(0x08910505), 'int', ['pointer','pointer'], {"abi":"sysv"});
var stSpPerLevelTable =  new NativeFunction(ptr(0x0837f544), 'void', ['pointer'], {"abi":"sysv"});
//获取背包槽中的道具
var INVENTORY_TYPE_BODY = 0;            //身上穿的装备(0-26)
var INVENTORY_TYPE_ITEM = 1;            //物品栏(0-311)
var INVENTORY_TYPE_AVARTAR = 2;         //时装栏(0-104)
var INVENTORY_TYPE_CREATURE = 3;        //宠物装备(0-241)


//通知客户端更新背包栏
var ENUM_ITEMSPACE_INVENTORY = 0;       //物品栏
var ENUM_ITEMSPACE_AVATAR = 1;          //时装栏
var ENUM_ITEMSPACE_CARGO = 2;           //仓库
var ENUM_ITEMSPACE_CREATURE = 7;        //宠物栏
var ENUM_ITEMSPACE_ACCOUNT_CARGO = 12;  //账号仓库
/**
 * 用于存放公共的方法
 */

//本地时间戳
function get_timestamp()
{
    var date = new Date();
    date = new Date(date.setHours(date.getHours() + 10));     //转换到本地时间
    var year = date.getFullYear().toString();
    var month = (date.getMonth() + 1).toString();
    var day = date.getDate().toString();
    var hour = date.getHours().toString();
    var minute = date.getMinutes().toString();
    var second = date.getSeconds().toString();
    var ms = date.getMilliseconds().toString();


    return year + '-' + month + '-' + day + ' ' + hour + ':' + minute + ':' + second;
}

//生成随机整数(不包含max)
function get_random_int(min, max)
{
    return Math.floor(Math.random() * (max - min)) + min;
}


//内存十六进制打印
function bin2hex(p, len)
{
    var hex = '';
    for(var i = 0; i < len; i++)
    {
        var s = p.add(i).readU8().toString(16);
        if(s.length == 1)
            s = '0' + s;
        hex += s;
        if (i != len - 1)
            hex += ' ';
    }
    return hex;
}

//字符串压缩(返回压缩后的指针与长度)
function api_compress_zip(s)
{
    var input = Memory.allocUtf8String(s);
    var alloc_buf_size = 1000 + strlen(s)*2;
    var output = Memory.alloc(alloc_buf_size);
    var output_len = Memory.alloc(4);
    output_len.writeInt(alloc_buf_size);
    compress_zip(output, output_len, input, strlen(s));

    return [output, output_len.readInt()];
}

//二进制数据解压缩
function api_uncompress_zip(p, len)
{
    var alloc_buf_size = 1000 + (len*10);
    var output = Memory.alloc(alloc_buf_size);
    var output_len = Memory.alloc(4);
    output_len.writeInt(alloc_buf_size);
    uncompress_zip(output, output_len, p, len);

    return output.readUtf8String(output_len.readInt());
}


//从客户端封包中读取数据(失败会抛异常, 调用方必须做异常处理)
function api_PacketBuf_get_byte(packet_buf)
{
    var data = Memory.alloc(1);

    if(PacketBuf_get_byte(packet_buf, data))
    {
        return data.readU8();
    }

    throw  new Error('PacketBuf_get_byte Fail!');
}
function api_PacketBuf_get_short(packet_buf)
{
    var data = Memory.alloc(2);

    if(PacketBuf_get_short(packet_buf, data))
    {
        return data.readShort();
    }

    throw  new Error('PacketBuf_get_short Fail!');
}
function api_PacketBuf_get_int(packet_buf)
{
    var data = Memory.alloc(4);

    if(PacketBuf_get_int(packet_buf, data))
    {
        return data.readInt();
    }


    throw  new Error('PacketBuf_get_int Fail!');
}
function api_PacketBuf_get_binary(packet_buf, len)
{
    var data = Memory.alloc(len);

    if(PacketBuf_get_binary(packet_buf, data, len))
    {
        return data.readByteArray(len);
    }

    throw  new Error('PacketBuf_get_binary Fail!');
}

//获取原始封包数据
function api_PacketBuf_get_buf(packet_buf)
{
    return packet_buf.add(20).readPointer().add(13);
}

//linux创建文件夹
function api_mkdir(path)
{
    var opendir = new NativeFunction(Module.getExportByName(null, 'opendir'), 'int', ['pointer'], {"abi":"sysv"});
    var mkdir = new NativeFunction(Module.getExportByName(null, 'mkdir'), 'int', ['pointer', 'int'], {"abi":"sysv"});
    var path_ptr = Memory.allocUtf8String(path);
    if(opendir(path_ptr))
        return true;
    return mkdir(path_ptr, 0x1FF);
}

//linux读本地文件
var fopen = new NativeFunction(Module.getExportByName(null, 'fopen'), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var fread = new NativeFunction(Module.getExportByName(null, 'fread'), 'int', ['pointer', 'int', 'int', 'int'], {"abi":"sysv"});
var fclose = new NativeFunction(Module.getExportByName(null, 'fclose'), 'int', ['int'], {"abi":"sysv"});
function api_read_file(path, mode, len)
{
    var path_ptr = Memory.allocUtf8String(path);
    var mode_ptr = Memory.allocUtf8String(mode);
    var f = fopen(path_ptr, mode_ptr);

    if(f == 0)
        return null;

    var data = Memory.alloc(len);
    var fread_ret = fread(data, 1, len, f);

    fclose(f);

    //返回字符串
    if(mode == 'r')
        return data.readUtf8String(fread_ret);

    //返回二进制buff指针
    return data;
}

//加载本地配置文件(json格式)
var global_config = {};
function load_config(path)
{
    var data = api_read_file(path, 'r', 10*1024*1024);
    global_config = JSON.parse(data);
}
/**
 * 打印日志相关的方法
 */



//文件记录日志
var frida_log_dir_path = './frida_log/'
var f_log = null;
var log_day = null;
function log(msg)
{
    var date = new Date();
    date = new Date(date.setHours(date.getHours() + 10));     //转换到本地时间
    var year = date.getFullYear().toString();
    var month = (date.getMonth() + 1).toString();
    var day = date.getDate().toString();
    var hour = date.getHours().toString();
    var minute = date.getMinutes().toString();
    var second = date.getSeconds().toString();
    var ms = date.getMilliseconds().toString();

    //日志按日期记录
    if((f_log == null) || (log_day != day))
    {
        api_mkdir(frida_log_dir_path);
        f_log = new File(frida_log_dir_path + 'frida_' + api_CEnvironment_get_file_name() + '_' + year + '_' + month + '_' + day + '.log', 'a+');
        log_day = day;
    }

    //时间戳
    var timestamp = year + '-' + month + '-' + day + ' ' + hour + ':' + minute + ':' + second + '.' + ms;

    //控制台日志
    console.log('[' + timestamp + ']' + msg + '\n');

    //文件日志
    f_log.write('[' + timestamp + ']' + msg + '\n');
    //立即写日志到文件中
    f_log.flush();
}
//MYSQL操作
//游戏中已打开的数据库索引(游戏数据库非线程安全 谨慎操作)
var TAIWAN_CAIN = 2;
var DBMgr_GetDBHandle = new NativeFunction(ptr(0x83F523E), 'pointer', ['pointer', 'int', 'int'], {"abi":"sysv"});
var MySQL_MySQL = new NativeFunction(ptr(0x83F3AC8), 'pointer', ['pointer'], {"abi":"sysv"});
var MySQL_init = new NativeFunction(ptr(0x83F3CE4), 'int', ['pointer'], {"abi":"sysv"});
var MySQL_open = new NativeFunction(ptr(0x83F4024), 'int', ['pointer', 'pointer', 'int', 'pointer', 'pointer', 'pointer'], {"abi":"sysv"});
var MySQL_close = new NativeFunction(ptr(0x83F3E74), 'int', ['pointer'], {"abi":"sysv"});
var MySQL_set_query_2 = new NativeFunction(ptr(0x83F41C0), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
var MySQL_set_query_3 = new NativeFunction(ptr(0x83F41C0), 'int', ['pointer', 'pointer', 'int'], {"abi":"sysv"});
var MySQL_set_query_4 = new NativeFunction(ptr(0x83F41C0), 'int', ['pointer', 'pointer', 'int', 'int'], {"abi":"sysv"});
var MySQL_set_query_5 = new NativeFunction(ptr(0x83F41C0), 'int', ['pointer', 'pointer', 'int', 'int', 'int'], {"abi":"sysv"});
var MySQL_set_query_6 = new NativeFunction(ptr(0x83F41C0), 'int', ['pointer', 'pointer', 'int', 'int', 'int', 'int'], {"abi":"sysv"});
var MySQL_exec = new NativeFunction(ptr(0x83F4326), 'int', ['pointer', 'int'], {"abi":"sysv"});
var MySQL_exec_query= new NativeFunction(ptr(0x083F5348), 'int', ['pointer'], {"abi":"sysv"});
var MySQL_get_n_rows = new NativeFunction(ptr(0x80E236C), 'int', ['pointer'], {"abi":"sysv"});
var MySQL_fetch = new NativeFunction(ptr(0x83F44BC), 'int', ['pointer'], {"abi":"sysv"});
var MySQL_get_int = new NativeFunction(ptr(0x811692C), 'int', ['pointer', 'int', 'pointer'], {"abi":"sysv"});
var MySQL_get_uint = new NativeFunction(ptr(0x80E22F2), 'int', ['pointer', 'int', 'pointer'], {"abi":"sysv"});
var MySQL_get_ulonglong = new NativeFunction(ptr(0x81754C8), 'int', ['pointer', 'int', 'pointer'], {"abi":"sysv"});
var MySQL_get_ushort = new NativeFunction(ptr(0x8116990), 'int', ['pointer'], {"abi":"sysv"});
var MySQL_get_float = new NativeFunction(ptr(0x844D6D0), 'int', ['pointer', 'int', 'pointer'], {"abi":"sysv"});
var MySQL_get_binary = new NativeFunction(ptr(0x812531A), 'int', ['pointer', 'int', 'pointer', 'int'], {"abi":"sysv"});
var MySQL_get_binary_length = new NativeFunction(ptr(0x81253DE), 'int', ['pointer', 'int'], {"abi":"sysv"});
var MySQL_get_str = new NativeFunction(ptr(0x80ECDEA), 'int', ['pointer', 'int', 'pointer', 'int'], {"abi":"sysv"});
var MySQL_blob_to_str = new NativeFunction(ptr(0x83F452A), 'pointer', ['pointer', 'int', 'pointer', 'int'], {"abi":"sysv"});
var compress_zip = new NativeFunction(ptr(0x86B201F), 'int', ['pointer', 'pointer', 'pointer', 'int'], {"abi":"sysv"});
var uncompress_zip = new NativeFunction(ptr(0x86B2102), 'int', ['pointer', 'pointer', 'pointer', 'int'], {"abi":"sysv"});

var MySQL_set_query_3_ptr = new NativeFunction(ptr(0x83F41C0), 'int', ['pointer', 'pointer', 'pointer'], {"abi":"sysv"});
//已打开的数据库句柄
var mysql_taiwan_cain = null;
var mysql_taiwan_cain_2nd = null;
var mysql_taiwan_billing = null;
var mysql_frida = null;
var mysql_frida_ready = false;

//打开数据库
function api_MYSQL_open(db_name, db_ip, db_port, db_account, db_password)
{
    //mysql初始化
    var mysql = Memory.alloc(0x80000);
    MySQL_MySQL(mysql);
    MySQL_init(mysql);

    //连接数据库
    var db_ip_ptr = Memory.allocUtf8String(db_ip);
    var db_port = db_port;
    var db_name_ptr = Memory.allocUtf8String(db_name);
    var db_account_ptr = Memory.allocUtf8String(db_account);
    var db_password_ptr = Memory.allocUtf8String(db_password);
    var ret = MySQL_open(mysql, db_ip_ptr, db_port, db_name_ptr, db_account_ptr, db_password_ptr);
    if(ret)
    {
        //log('Connect MYSQL DB <' + db_name + '> SUCCESS!');
        return mysql;
    }

    return null;
}

//mysql查询(返回mysql句柄)(注意线程安全)
function api_MySQL_exec(mysql, sql)
{
    if(mysql == null || mysql.isNull())
    {
        log('[Frida DB] connection unavailable; query was not executed');
        return 0;
    }
    var sql_ptr = Memory.allocUtf8String(sql);

    MySQL_set_query_2(mysql, sql_ptr);

    return MySQL_exec(mysql, 1);
}

//查询sql结果
//使用前务必保证api_MySQL_exec返回0
//并且MySQL_get_n_rows与预期一致
function api_MySQL_get_int(mysql, field_index)
{
    var v = Memory.alloc(4);
    if(1 == MySQL_get_int(mysql, field_index, v))
        return v.readInt();
    //log('api_MySQL_get_int Fail!!!');
    return null;
}
function api_MySQL_get_uint(mysql, field_index)
{
    var v = Memory.alloc(4);
    if(1 == MySQL_get_uint(mysql, field_index, v))
        return v.readUInt();
    //log('api_MySQL_get_uint Fail!!!');
    return null;
}
function api_MySQL_get_short(mysql, field_index)
{
    var v = Memory.alloc(4);
    if(1 == MySQL_get_short(mysql, field_index, v))
        return v.readShort();
    //log('MySQL_get_short Fail!!!');
    return null;
}
function api_MySQL_get_float(mysql, field_index)
{
    var v = Memory.alloc(4);
    if(1 == MySQL_get_float(mysql, field_index, v))
        return v.readFloat();
    //log('MySQL_get_float Fail!!!');
    return null;
}
function api_MySQL_get_str(mysql, field_index)
{
    var binary_length = MySQL_get_binary_length(mysql, field_index);
    if(binary_length > 0)
    {
        var v = Memory.alloc(binary_length);
        if(1 == MySQL_get_binary(mysql, field_index, v, binary_length))
            return v.readUtf8String(binary_length);
    }

    //log('MySQL_get_str Fail!!!');
    return null;
}
function api_MySQL_get_binary(mysql, field_index)
{
    var binary_length = MySQL_get_binary_length(mysql, field_index);
    if(binary_length > 0)
    {
        var v = Memory.alloc(binary_length);
        if(1 == MySQL_get_binary(mysql, field_index, v, binary_length))
            return v.readByteArray(binary_length);
    }

    //log('api_MySQL_get_binary Fail!!!');
    return null;
}

//初始化数据库(打开数据库/建库建表/数据库字段扩展)
function init_db()
{
    mysql_frida_ready = false;
    //配置文件
    var config = global_config['db_config'];

    //打开数据库连接
    if(mysql_taiwan_cain == null)
    {
        mysql_taiwan_cain = api_MYSQL_open('taiwan_cain', '127.0.0.1', 3306, config['account'], config['password']);
    }

    if(mysql_taiwan_cain_2nd == null)
    {
        mysql_taiwan_cain_2nd = api_MYSQL_open('taiwan_cain_2nd', '127.0.0.1', 3306, config['account'], config['password']);
    }
    if(mysql_taiwan_billing == null)
    {
        mysql_taiwan_billing = api_MYSQL_open('taiwan_billing', '127.0.0.1', 3306, config['account'], config['password']);
    }
    if(mysql_taiwan_cain == null || mysql_taiwan_cain_2nd == null || mysql_taiwan_billing == null)
    {
        log('[Frida DB] game database connection failed; activity initialization stopped');
        return false;
    }

    //建库frida
    api_MySQL_exec(mysql_taiwan_cain, 'create database if not exists frida default charset utf8;');
    if(mysql_frida == null)
    {
        mysql_frida = api_MYSQL_open('frida', '127.0.0.1', 3306, config['account'], config['password']);
    }
    if(mysql_frida == null)
    {
        log('[Frida DB] frida database unavailable; check instance plugin-db provisioning and scoped grants');
        return false;
    }

    //建表frida.game_event
    if(!api_MySQL_exec(mysql_frida, 'CREATE TABLE IF NOT EXISTS game_event (\
        event_id varchar(30) NOT NULL, event_info mediumtext NULL,\
        PRIMARY KEY  (event_id)\
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;'))
    {
        log('[Frida DB] game_event schema could not be prepared; activity initialization stopped');
        return false;
    }

    //载入活动数据
    if(!event_villageattack_load_from_db())
    {
        log('[Frida DB] activity data could not be loaded; existing progress retained');
        return false;
    }
    mysql_frida_ready = true;
    log('[Frida DB] activity storage ready');
    return true;
}

//关闭数据库（卸载插件前调用）
function uninit_db()
{
    //活动数据存档
    event_villageattack_save_to_db();

    //关闭数据库连接
    if(mysql_taiwan_cain)
    {
        MySQL_close(mysql_taiwan_cain);
        mysql_taiwan_cain = null;
    }

    if(mysql_taiwan_cain_2nd)
    {
        MySQL_close(mysql_taiwan_cain_2nd);
        mysql_taiwan_cain_2nd = null;
    }

    if(mysql_taiwan_billing)
    {
        MySQL_close(mysql_taiwan_billing);
        mysql_taiwan_billing = null;
    }

    if(mysql_frida)
    {
        MySQL_close(mysql_frida);
        mysql_frida = null;
    }
}
//获取当前频道名
function api_CEnvironment_get_file_name()
{
    var filename = CEnvironment_get_file_name(G_CEnvironment());
    return filename.readUtf8String(-1);
}


//获取系统UTC时间(秒)
function api_CSystemTime_getCurSec()
{
    return getCurSec(ptr(0));
}
//申请锁(申请后务必手动释放!!!)
function api_Guard_Mutex_Guard()
{
    var a1 = Memory.alloc(100);
    Guard_Mutex_Guard(a1, G_TimerQueue().add(16));

    return a1;
}

//需要在dispatcher线程执行的任务队列(热加载后会被清空)
var timer_dispatcher_list = [];

//在dispatcher线程执行(args为函数f的参数组成的数组, 若f无参数args可为null)
function api_scheduleOnMainThread(f, args)
{
    //线程安全
    var guard = api_Guard_Mutex_Guard();

    timer_dispatcher_list.push([f, args]);

    Destroy_Guard_Mutex_Guard(guard);

    return;
}

//设置定时器 到期后在dispatcher线程执行
function api_scheduleOnMainThread_delay(f, args, delay)
{
    setTimeout(api_scheduleOnMainThread, delay, f, args);
}


//处理到期的自定义定时器
function do_timer_dispatch()
{
    //当前待处理的定时器任务列表
    var task_list = [];

    //线程安全
    var guard = api_Guard_Mutex_Guard();

    //依次取出队列中的任务
    while(timer_dispatcher_list.length > 0)
    {
        //先入先出
        var task = timer_dispatcher_list.shift();
        task_list.push(task);
    }

    Destroy_Guard_Mutex_Guard(guard);

    //执行任务
    for(var i=0; i<task_list.length; ++i)
    {
        var task = task_list[i];

        var f = task[0];
        var args = task[1];

        f.apply(null, args);
    }
}


//挂接消息分发线程 确保代码线程安全
function hook_TimerDispatcher_dispatch()
{
    //hook TimerDispatcher::dispatch
    //服务器内置定时器 每秒至少执行一次
    Interceptor.attach(ptr(0x8632A18), {

        onEnter: function (args) {
        },
        onLeave: function (retval) {

            //清空等待执行的任务队列
            do_timer_dispatch();
        }
    });
}

//设置角色当前绝望之塔层数
function api_TOD_UserState_setEnterLayer(user, layer)
{
    var tod_layer = Memory.alloc(100);
    TOD_Layer_TOD_Layer(tod_layer, layer);
    var expand_data = CUser_GetCharacExpandData(user, 13);
    TOD_UserState_setEnterLayer(expand_data, tod_layer);
}

//根据角色id查询角色名
function api_get_charac_name_by_charac_no(charac_no)
{
    //从数据库中查询角色名
    if(api_MySQL_exec(mysql_taiwan_cain, "select charac_name from charac_info where charac_no=" + charac_no + ";"))
    {
        if(MySQL_get_n_rows(mysql_taiwan_cain) == 1)
        {
            if(MySQL_fetch(mysql_taiwan_cain))
            {
                var charac_name = api_MySQL_get_str(mysql_taiwan_cain, 0);
                return charac_name;
            }
        }
    }

    return charac_no.toString();
}

//给角色发经验
function api_CUser_gain_exp_sp(user, exp)
{
    var a2 = Memory.alloc(4);
    var a3 = Memory.alloc(4);
    CUser_gain_exp_sp(user, exp, a2, a3, 0, 0, 0);
}

//给角色发道具
function api_CUser_AddItem(user, item_id, item_cnt)
{
    var item_space = Memory.alloc(4);
    var slot = CUser_AddItem(user, item_id, item_cnt, 6, item_space, 0);

    if(slot >= 0)
    {
        //通知客户端有游戏道具更新
        CUser_SendUpdateItemList(user, 1, item_space.readInt(), slot);
    }

    return;
}

//获取角色名字
function api_CUserCharacInfo_getCurCharacName(user)
{
    var p = CUserCharacInfo_getCurCharacName(user);
    if(p.isNull())
    {
        return '';
    }

    return p.readUtf8String(-1);
}

//给角色发消息
function api_CUser_SendNotiPacketMessage(user, msg, msg_type)
{
    var p = Memory.allocUtf8String(msg);
    CUser_SendNotiPacketMessage(user, p, msg_type);

    return;
}

//服务器组包
function api_PacketGuard_PacketGuard()
{
    var packet_guard = Memory.alloc(0x20000);
    PacketGuard_PacketGuard(packet_guard);

    return packet_guard;
}

//发送字符串给客户端
function api_InterfacePacketBuf_put_string(packet_guard, s)
{
    var p = Memory.allocUtf8String(s);
    var len = strlen(p);
    InterfacePacketBuf_put_int(packet_guard, len);
    InterfacePacketBuf_put_binary(packet_guard, p, len);

    return;
}

//获取在线玩家列表表头
function api_gameworld_user_map_begin()
{
    var begin = Memory.alloc(4);
    gameworld_user_map_begin(begin, G_GameWorld().add(308));
    return begin;
}

//获取在线玩家列表表尾
function api_gameworld_user_map_end()
{
    var end = Memory.alloc(4);
    gameworld_user_map_end(end, G_GameWorld().add(308));
    return end;
}

//获取当前正在遍历的玩家
function api_gameworld_user_map_get(it)
{
    return gameworld_user_map_get(it).add(4).readPointer();
}


//遍历在线玩家列表
function api_gameworld_user_map_next(it)
{
    var next = Memory.alloc(4);
    gameworld_user_map_next(next, it);
    return next;
}

//发系统邮件(多道具)(角色charac_no, 邮件标题, 邮件正文, 金币数量, 道具列表)
function api_WongWork_CMailBoxHelper_ReqDBSendNewSystemMultiMail(target_charac_no, title, text, gold, item_list)
{
    //添加道具附件
    var vector = Memory.alloc(100);
    std_vector_std_pair_int_int_vector(vector);
    std_vector_std_pair_int_int_clear(vector);

    for(var i=0; i<item_list.length; ++i)
    {
        var item_id = Memory.alloc(4);          //道具id
        var item_cnt = Memory.alloc(4);         //道具数量

        item_id.writeInt(item_list[i][0]);
        item_cnt.writeInt(item_list[i][1]);

        var pair = Memory.alloc(100);
        std_make_pair_int_int(pair, item_id, item_cnt);

        std_vector_std_pair_int_int_push_back(vector, pair);
    }

    //邮件支持10个道具附件格子
    var addition_slots = Memory.alloc(1000);
    for(var i=0; i<10; ++i)
    {
        Inven_Item_Inven_Item(addition_slots.add(i*61));
    }
    WongWork_CMailBoxHelper_MakeSystemMultiMailPostal(vector, addition_slots, 10);


    var title_ptr = Memory.allocUtf8String(title);      //邮件标题
    var text_ptr = Memory.allocUtf8String(text);        //邮件正文
    var text_len = strlen(text_ptr);                    //邮件正文长度

    //发邮件给角色
    WongWork_CMailBoxHelper_ReqDBSendNewSystemMultiMail(title_ptr, addition_slots, item_list.length, gold, target_charac_no, text_ptr, text_len, 0, 99, 1);
}

//全服在线玩家发信
function api_gameworld_send_mail(title, text, gold, item_list)
{
    //遍历在线玩家列表
    var it = api_gameworld_user_map_begin();
    var end = api_gameworld_user_map_end();

    //判断在线玩家列表遍历是否已结束
    while(gameworld_user_map_not_equal(it, end))
    {
        //当前被遍历到的玩家
        var user = api_gameworld_user_map_get(it);

        //只处理已登录角色
        if(CUser_get_state(user) >= 3)
        {
            //角色uid
            var charac_no = CUserCharacInfo_getCurCharacNo(user);

            //给角色发信
            api_WongWork_CMailBoxHelper_ReqDBSendNewSystemMultiMail(charac_no, title, text, gold, item_list);
        }

        //继续遍历下一个玩家
        api_gameworld_user_map_next(it);
    }
}

//对全服在线玩家执行回调函数
function api_gameworld_foreach(f, args)
{
    //遍历在线玩家列表
    var it = api_gameworld_user_map_begin();
    var end = api_gameworld_user_map_end();

    //判断在线玩家列表遍历是否已结束
    while(gameworld_user_map_not_equal(it, end))
    {
        //当前被遍历到的玩家
        var user = api_gameworld_user_map_get(it);

        //只处理已登录角色
        if(CUser_get_state(user) >= 3)
        {
            //执行回调函数
            f(user, args);
        }

        //继续遍历下一个玩家
        api_gameworld_user_map_next(it);
    }
}


//临时开GM权限
function api_CUser_SetGameMasterMode_temp(user, enable)
{
    var old_gm_mode = user.add(463320).readU8();

    user.add(463320).writeU8(enable);

    //返回旧权限
    return old_gm_mode;
}

//设置角色等级(最高70级)
function api_DisPatcher_DebugCommand__debugCommandSetLevel(user, new_level)
{
    //为该角色临时开通GM权限
    var old_gm_mode = api_CUser_SetGameMasterMode_temp(user, 1);

    DisPatcher_DebugCommand__debugCommandSetLevel(ptr(0), user, new_level);

    //恢复原始GM权限
    api_CUser_SetGameMasterMode_temp(user, old_gm_mode);
}

//返回选择角色界面
function api_CUser_ReturnToSelectCharacList(user)
{
    scheduleOnMainThread(CUser_ReturnToSelectCharacList, [user, 1]);
}

//所有副本开王图
function unlock_all_dungeon_difficulty(user)
{
    var a3 = Memory.allocUtf8String('3');   //副本解锁难度: 0-3
    DoUserDefineCommand(user, 120, a3);
}


//清空角色邮箱(重新选择角色生效)
function clear_letter(user)
{
    //从数据库中删除该用户所有邮件
    var charac_no = CUserCharacInfo_getCurCharacNo(user);
    api_MySQL_exec(mysql_taiwan_cain_2nd, 'delete from letter where charac_no=' + charac_no + ';');
    api_MySQL_exec(mysql_taiwan_cain_2nd, 'delete from postal where receive_charac_no=' + charac_no + ';');
}

//清空时装栏(重新选择角色生效)
function clear_avartar_inven(user)
{
    //从数据库中删除该用户所有时装
    var charac_no = CUserCharacInfo_getCurCharacNo(user);
    api_MySQL_exec(mysql_taiwan_cain_2nd, 'delete from user_items where charac_no=' + charac_no + ';');
}

//清理宠物栏(重新选择角色生效)
function clear_creature_inven(user)
{
    //从数据库中删除该用户宠物
    var charac_no = CUserCharacInfo_getCurCharacNo(user);
    api_MySQL_exec(mysql_taiwan_cain_2nd, 'delete from creature_items where charac_no=' + charac_no + ';');

    //角色背包
    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    //遍历宠物装备背包
    for(var slot=0; slot<=241; slot++)
    {
        //根据格子获取道具
        var inven_item = CInventory_GetInvenRef(inven, INVENTORY_TYPE_CREATURE, slot);
        //删除该道具
        Inven_Item_reset(inven_item);
    }

    //通知客户端更新背包
    CUser_send_itemspace(user, ENUM_ITEMSPACE_CREATURE);
}

//修改角色职业
function change_job(user, new_job, new_growtype, new_level)
{
    //设置角色等级
    api_DisPatcher_DebugCommand__debugCommandSetLevel(user, new_level);

    //在数据库中修改角色职业和转职
    var charac_no = CUserCharacInfo_getCurCharacNo(user);
    api_MySQL_exec(mysql_taiwan_cain, 'update charac_info set job=' + new_job + ', grow_type=' + new_growtype + ' where charac_no=' + charac_no + ';');

    //返回选择角色界面
    api_CUser_ReturnToSelectCharacList(user);
}

//获取道具名字
function api_CItem_GetItemName(item_id)
{
    var citem = CDataManager_find_item(G_CDataManager(), item_id);
    if(!citem.isNull())
    {
        return ptr(CItem_GetItemName(citem)).readUtf8String(-1);
    }

    return item_id.toString();
}

//世界广播(频道内公告)
function api_GameWorld_SendNotiPacketMessage(msg, msg_type)
{
    var packet_guard = api_PacketGuard_PacketGuard();
    InterfacePacketBuf_put_header(packet_guard, 0, 12);
    InterfacePacketBuf_put_byte(packet_guard, msg_type);
    InterfacePacketBuf_put_short(packet_guard, 0);
    InterfacePacketBuf_put_byte(packet_guard, 0);
    api_InterfacePacketBuf_put_string(packet_guard, msg);
    InterfacePacketBuf_finalize(packet_guard, 1);
    GameWorld_send_all_with_state(G_GameWorld(), packet_guard, 3);  //只给state >= 3 的玩家发公告
    Destroy_PacketGuard_PacketGuard(packet_guard);
}


//点券充值 (禁止直接修改billing库所有表字段, 点券相关操作务必调用数据库存储过程!)
function api_recharge_cash_cera(user, amount)
{
    //充值
    WongWork_IPG_CIPGHelper_IPGInput(ptr(0x941F734).readPointer(), user, 5, amount, ptr(0x8C7FA20), ptr(0x8C7FA20),
        Memory.allocUtf8String('GM'), ptr(0), ptr(0), ptr(0));

    //通知客户端充值结果
    WongWork_IPG_CIPGHelper_IPGQuery(ptr(0x941F734).readPointer(), user);
}

//代币充值 (禁止直接修改billing库所有表字段, 点券相关操作务必调用数据库存储过程!)
function api_recharge_cash_cera_point(user, amount)
{
    //充值
    WongWork_IPG_CIPGHelper_IPGInputPoint(ptr(0x941F734).readPointer(), user, amount, 4, ptr(0), ptr(0));

    //通知客户端充值结果
    WongWork_IPG_CIPGHelper_IPGQuery(ptr(0x941F734).readPointer(), user);
}

/**
 * 整备分解
 * @param user
 */
function decompose(user){
    // 分解券
    var index = 0;
    // 检查副职业是否开启
    var checkTag = CUserCharacInfo_GetCurCharacExpertJob(user);
    if(checkTag == 0){
        api_CUser_SendNotiPacketMessage(user,"注意： 副职业没有开启！",1);
        return;
    }
    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    for (var i = 9; i <= 24; i++) {
        var equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, i);
        if(Inven_Item_getKey(equ)){
            // 分解装备
            DisPatcher_DisJointItem_disjoint(user,i,ENUM_ITEMSPACE_INVENTORY,239,user,0xFFFF);
            // 检查装备是否存在
            equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, i);
            if(Inven_Item_getKey(equ)){
                // 失败
            }else{
                // 成功
                index++;
                CUser_SendUpdateItemList(user, 1, 0, i);
            }
        }
    }
    if(index>0){
        api_CUser_SendNotiPacketMessage(user,"恭喜： "+index+"件装备分解 成功！",0);
    }else{
        api_CUser_SendNotiPacketMessage(user,"注意： 装备分解 失败！",0);
    }
}

/**
 * 跨界石： 将装备栏的第一个格子的装备移入到账号金库，自动找空的格子，所以可以同时移入多件
 * @param user
 */
function crossover(user){
    // 跨界  将装备移入到账号金库
    var accountCargo = CUser_GetAccountCargo(user);
    CAccountCargo_GetEmptySlot_NEW = new NativeFunction(tempFunc, 'int', ['pointer'], {"abi":"sysv"});
    console.log('accountCargo：'+accountCargo);
    var emptyIndex = CAccountCargo_GetEmptySlot_NEW(accountCargo);
    console.log('accountCargo emptyIndex:'+emptyIndex)
    if(emptyIndex==-1){
        api_CUser_SendNotiPacketMessage(user,"跨界失败：账号金库没有空的格子！！！",0);
    }
    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    var equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9);
    var itemId = Inven_Item_getKey(equ);

    if(itemId){
        var tag = CAccountCargo_InsertItem_NEW(accountCargo,equ,emptyIndex);
        if(tag==-1){
            console.log('fail!!!');
            api_CUser_SendNotiPacketMessage(user,"跨界失败：移入装备error",0);
        }else{
            Inven_Item_reset(equ);
            CUser_SendUpdateItemList(user, 1, 0, 9);
            CAccountCargo_SendItemList_NEW(accountCargo);
            console.log('success!!!');
            api_CUser_SendNotiPacketMessage(user,"跨界成功：已存入第 "+(emptyIndex+1)+" 个格子！",0);
        }
    }
}




/**
 * 重置异界次数
 * @param user
 * @param index
 */
function resetResetDimensionInout(user, index){
    var dimensionInout = CDataManager_get_dimensionInout(G_CDataManager(), index);
    CUserCharacInfo_setDemensionInoutValue(user, index, dimensionInout);
}

/**
 * 装备继承 将粉色以上等级大于等于55的装备继承 强化 增幅 锻造 附魔， 原装备会归0
 * 武器需要同源  即 太刀-》太刀
 * 防具需要同类型   即皮甲上衣 -》板甲上衣
 * @param user
 */
function equInherit(user){
    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    var equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9);
    var itemId = Inven_Item_getKey(equ)
    if(Inven_Item_getKey(equ)){
        //读取装备强化等级
        var upgrade_level = equ.add(6).readU8();
        var itemData = CDataManager_find_item(G_CDataManager(),itemId);
        var equ_type = itemData.add(141*4).readU32(); // 装备类型
        var sub_type = CEquipItem_GetSubType(itemData);
        var equRarity = CItem_GetRarity(itemData); // 稀有度  >=3  粉色以上
        var needLevel = CItem_GetUsableLevel(itemData);  //等级
        console.log("equ_type :"+equ_type)
        console.log("sub_type :"+sub_type)

        var useJob = "";
        for (var i = 60; i <=70; i++) {
            useJob += itemData.add(i).readU8();
        }
        console.log(equ_type + "  "+useJob);

        if(equRarity < 3){
            // 装备品级必须要求粉色以上，继承装备不满足要求
            api_CUser_SendNotiPacketMessage(user,"继承失败：装备品级必须要求粉色以上，继承装备不满足要求",0);
            return;
        }
        if(needLevel < 55){
            // 装备等级要大于50级以上，继承装备不满足要求
            api_CUser_SendNotiPacketMessage(user,"继承失败：装备等级要大于等于55级以上，继承装备不满足要求("+needLevel+")",0);
            return;
        }
        var successTag = false;
        for (var i = 10; i <=21; i++) {
            var equIn = CInventory_GetInvenRef(inven, INVENTORY_TYPE_BODY, i);
            if(Inven_Item_getKey(equIn)){
                var inItemId = Inven_Item_getKey(equIn)
                var inItemData = CDataManager_find_item(G_CDataManager(),inItemId);
                var inEqu_type = inItemData.add(141*4).readU32(); // 装备类型
                var inEquRarity = CItem_GetRarity(inItemData); // 稀有度  >=3  粉色以上
                var inNeedLevel = CItem_GetUsableLevel(inItemData);  //等级
                console.log('equ_type a：'+equ_type+','+inEqu_type+','+inItemData.add(148).readU8())
                if(inEqu_type==equ_type){
                    if(inEqu_type==10){
                        // 武器需要同职业
                        var useJob = "";
                        var inUseJob = "";
                        for (var i = 60; i <=70; i++) {
                            useJob += itemData.add(i).readU8();
                            inUseJob += inItemData.add(i).readU8();
                        }
                        if(useJob != inUseJob){
                            api_CUser_SendNotiPacketMessage(user,"继承失败：武器装备需要当前职业且同类型，穿戴装备不满足要求",0);
                            return;
                        }
                        var inSubType = CEquipItem_GetSubType(inItemData);
                        if(sub_type!=inSubType){
                            api_CUser_SendNotiPacketMessage(user,"继承失败：武器装备需要当前职业且同类型，穿戴装备不满足要求",0);
                            return;
                        }
                    }
                    // 类型一直 才能继承
                    if(inEquRarity<3){
                        // 继承失败：装备品级必须要求粉色以上，穿戴装备不满足要求
                        api_CUser_SendNotiPacketMessage(user,"继承失败：装备品级必须要求粉色以上，穿戴装备不满足要求",0);
                        return;
                    }
                    if(inNeedLevel < 55){
                        // 装备等级要大于50级以上，穿戴装备不满足要求
                        api_CUser_SendNotiPacketMessage(user,"继承失败：装备等级要大于等于55级以上，穿戴装备不满足要求",0);
                        return;
                    }
                    // 强化
                    var inUpgrade_level = equIn.add(6).readU8();
                    // 增幅
                    var zengfu = equ.add(17).readU16();
                    // 锻造
                    var duanzao = equ.add(51).readU8();
                    // 宝珠
                    var baozhu = equ.add(13).readU32();
                    //魔法封印
				var seal1_lv = equ.add(37).readU8(); 
				var seal2_lv = equ.add(38).readU8();
				var seal3_lv = equ.add(39).readU8();
				var seal4_lv = equ.add(40).readU8();
				var seal5_lv = equ.add(41).readU8();
				var seal6_lv = equ.add(42).readU8();
				var seal7_lv = equ.add(43).readU8();
				var seal8_lv = equ.add(44).readU8();
				var seal9_lv = equ.add(45).readU8();
				var seal10_lv = equ.add(46).readU8();
				var seal11_lv = equ.add(47).readU8();
				var seal12_lv = equ.add(48).readU8();
				var seal13_lv = equ.add(49).readU8();
				var seal14_lv = equ.add(50).readU8();
				
                    if(inUpgrade_level <= upgrade_level){
                        //提升强化/增幅等级
                        equIn.add(6).writeU8(upgrade_level);
                        equIn.add(17).writeU16(zengfu);
                        equIn.add(51).writeU8(duanzao);
                        equIn.add(13).writeU32(baozhu);
                        equIn.add(37).writeU8(seal1_lv);
                        equIn.add(38).writeU8(seal2_lv);
                        equIn.add(39).writeU8(seal3_lv);
                        equIn.add(40).writeU8(seal4_lv);
                        equIn.add(41).writeU8(seal5_lv);
                        equIn.add(42).writeU8(seal6_lv);
                        equIn.add(43).writeU8(seal7_lv);
                        equIn.add(44).writeU8(seal8_lv);
                        equIn.add(45).writeU8(seal9_lv);
                        equIn.add(46).writeU8(seal10_lv);
                        equIn.add(47).writeU8(seal11_lv);
                        equIn.add(48).writeU8(seal12_lv);
                        equIn.add(49).writeU8(seal13_lv);
                        equIn.add(50).writeU8(seal14_lv);
                        
                        // 将原装备清除
                        equ.add(6).writeU8(0);
                        equ.add(17).writeU16(0);
                        equ.add(51).writeU8(0);
                        equ.add(13).writeU32(0);
                        equ.add(37).writeU8(0);
                        equ.add(38).writeU8(0);
                        equ.add(39).writeU8(0);
                        equ.add(40).writeU8(0);
                        equ.add(41).writeU8(0);
                        equ.add(42).writeU8(0);
                        equ.add(43).writeU8(0);
                        equ.add(44).writeU8(0);
                        equ.add(45).writeU8(0);
                        equ.add(46).writeU8(0);
                        equ.add(47).writeU8(0);
                        equ.add(48).writeU8(0);
                        equ.add(49).writeU8(0);
                        equ.add(50).writeU8(0);
                        //通知客户端更新装备
                        CUser_SendUpdateItemList(user, 1, 0, 9);
                        CUser_SendUpdateItemList(user, 1, 3, i);
                        CUser_SendUpdateItemList(user, 1, 3, 10);
                        successTag = true;
                        console.log("success！！！")
                        api_CUser_SendNotiPacketMessage(user,"继承成功！！若属性不显示请选择角色或整理背包！",6);
                    }
                    break;
                }
            }
        }
        if(!successTag){
            // 失败 没有合适的装备，不符合装备
            api_CUser_SendNotiPacketMessage(user,"继承失败：没有合适的装备",8);
        }
    }
}

function startHellParty(){
    Interceptor.attach(ptr(0x085a0954), {
        onEnter: function (args) {
            if(heffPartyTag){
                args[3] = ptr(1);
            }
        }
    });
}

function test(){
    Interceptor.attach(ptr(0x084be970), {
        onEnter: function (args) {
            // a1 InterDispatcher对象  a2 dest消息 a3 消息 a4 获取buffer a5 Stream::length
            console.log("InterDispatcher::dispatch arg =:"+args[0]+' '+args[1]+' '+args[2]+' '+args[3]+' '+args[4]+' ')
        }
    });

    Interceptor.attach(ptr(0x08594e52), {
        onEnter: function (args) {
            // a1 PacketDispatcher对象  a2 消息  a3 获取buffer  a4 Stream::length
            console.log("PacketDispatcher::dispatch:=:"+args[0]+' '+args[1]+' '+args[2]+' '+args[3])
        }
    });
    Interceptor.attach(ptr(0x08594922), {
        onEnter: function (args) {
            // a1 PacketDispatcher对象 a2 user a3 buffer的int值 a4 buffer+1的双字节值(类型)  a5 获取buffer a6 Stream::length a7 a8
            console.log("PacketDispatcher::doDispatch:=:"+args[0]+' '+args[1]+' '+args[2]+' '+args[3]+' '+args[4]+' '+args[5]+' '+args[6]+' '+args[7])
        }
    });


}
var QUEST_GRADE_COMMON_UNIQUE = 5;                  //任务脚本中[grade]字段对应的常量定义 可以在importQuestScript函数中找到
var QUEST_GRADE_NORMALY_REPEAT = 4;                 //可重复提交的重复任务
var QUEST_GRADE_DAILY = 3;                          //每日任务
var QUEST_GRADE_EPIC = 0;                           //史诗任务
var QUEST_GRADE_achievement = 2;                           //成就任务
//无条件完成指定任务并领取奖励
function api_force_clear_quest(user, quest_id)
{
    //设置GM完成任务模式(无条件完成任务)
    CUser_setGmQuestFlag(user, 1);

    //接受任务
    CUser_quest_action(user, 33, quest_id, 0, 0);

    //完成任务
    CUser_quest_action(user, 35, quest_id, 0, 0);

    //领取任务奖励(倒数第二个参数表示领取奖励的编号, -1=领取不需要选择的奖励; 0=领取可选奖励中的第1个奖励; 1=领取可选奖励中的第二个奖励)
    CUser_quest_action(user, 36, quest_id, -1, 1);

    //服务端有反作弊机制: 任务完成时间间隔不能小于1秒.  这里将上次任务完成时间清零 可以连续提交任务
    user.add(0x79644).writeInt(0);

    //关闭GM完成任务模式(不需要材料直接完成)
    CUser_setGmQuestFlag(user, 0);

    return;
}

var quest_list = [4920,4921,2413,2201, 18620]; // 需要完成的任务列表

function api_force_clear_quest_list(user, quest_list)
{

    // 循环遍历任务列表
    for (var i = 0; i < quest_list.length; i++)
    {
        var quest_id = quest_list[i];
        api_force_clear_quest(user, quest_id); // 完成对应的任务
    }
}

//重置所有任务(需要小退重新选择角色刷新)
function api_reset_all_quest(user)
{
    var user_quest = CUser_getCurCharacQuestW(user);
    //清空已接任务列表
    for(var i=0; i<20; i++)
    {
        user_quest.add(4 * (i + 7500 + 2)).writeInt(0);
    }
    //所有任务设置未完成状态
    for(var i=0; i<29999; i++)
    {
        WongWork_CQuestClear_resetClearedQuests(user_quest.add(4), i);
    }

    api_CUser_SendNotiPacketMessage(user, '所有任务已重置, 请重新选择角色刷新任务列表!', 14);
}

//完成当前已接任务并领取奖励
function clear_doing_quest(user)
{
    //玩家任务信息
    var user_quest = CUser_getCurCharacQuestW(user);

    //遍历20个已接任务
    //任务列表(保存任务id): user_quest.add(4 * (i + 7500 + 2))
    //任务完成状态(0=已满足任务条件): user_quest.add(4 * (i + 7520 + 2))
    for(var i=0; i<20; i++)
    {
        //任务id
        var quest_id = user_quest.add(4 * (i + 7500 + 2)).readInt();

        if(quest_id > 0)
        {
            //无条件完成任务并领取奖励
            api_force_clear_quest(user, quest_id);
        }
    }

    //通知客户端更新已完成任务列表
    CUser_send_clear_quest_list(user);

    //通知客户端更新任务列表
    var packet_guard = api_PacketGuard_PacketGuard();
    UserQuest_get_quest_info(user_quest, packet_guard);
    CUser_Send(user, packet_guard);
    Destroy_PacketGuard_PacketGuard(packet_guard);
}

//完成角色当前可接的所有任务(仅发送金币/经验/QP等基础奖励 无道具奖励)
function clear_all_quest_by_character_level(user)
{
    //log('clear_all_quest_by_character_level start!');

    //玩家任务信息
    var user_quest = CUser_getCurCharacQuestW(user);
    //玩家已完成任务信息
    var WongWork_CQuestClear = user_quest.add(4);
    //玩家当前等级
    var charac_lv = CUserCharacInfo_get_charac_level(user);

    //本次完成任务数量
    var clear_quest_cnt = 0;

    //pvf数据
    var data_manager = G_CDataManager();

    //首先完成当前已接任务
    clear_doing_quest(user);

    //完成当前等级所有任务总经验奖励
    var total_exp_bonus = 0;
    //完成当前等级所有任务总金币奖励
    var total_gold_bonus = 0;
    //任务点奖励
    var total_quest_point_bonus = 0;
    var total_quest_piece_bonus = 0;

    //任务最大编号: 29999
    for(var quest_id=1; quest_id<30000; quest_id++)
    {
        //跳过已完成的任务
        if(WongWork_CQuestClear_isClearedQuest(WongWork_CQuestClear, quest_id))
            continue;

        //获取任务数据
        var quest = CDataManager_find_quest(data_manager, quest_id);
        if(!quest.isNull())
        {
            //任务类型
            var quest_grade = quest.add(8).readInt();

            //跳过可重复提交的任务
            //跳过每日任务
            //跳过成就任务       
            if( (quest_grade != QUEST_GRADE_NORMALY_REPEAT) && (quest_grade != QUEST_GRADE_DAILY) && (quest_grade != QUEST_GRADE_achievement))
            {
                //判断任务当前是否可接
                //var stSelectQuestParam = Memory.alloc(100);
                //stSelectQuestParam_stSelectQuestParam(stSelectQuestParam, user);
                //if(Quest_check_possible(quest, stSelectQuestParam))

                //只判断任务最低等级要求 忽略 职业/前置 等任务要求 可一次性完成当前等级所有任务
                var quest_min_lv = quest.add(0x20).readInt();
                if(quest_min_lv <= charac_lv)
                {
                    //获取该任务的基础奖励
                    var exp_bonus = Memory.alloc(4);
                    var gold_bonus = Memory.alloc(4);
                    var quest_point_bonus = Memory.alloc(4);
                    var quest_piece_bonus = Memory.alloc(4);
                    //QP奖励已直接发送到角色 经验/金币只返回结果  需要手动发送
                    CUser_quest_basic_reward(user,quest, exp_bonus, gold_bonus, quest_point_bonus, quest_piece_bonus, 1);

                    //统计本次自动完成任务的基础奖励
                    var exp = exp_bonus.readInt();
                    var gold = gold_bonus.readInt();
                    var quest_point = quest_point_bonus.readInt();
                    var quest_piece = quest_piece_bonus.readInt();
                    if(exp > 0)
                        total_exp_bonus += exp;
                    if(gold > 0)
                        total_gold_bonus += gold;
                    if(quest_point > 0)
                        total_quest_point_bonus += quest_point;     //没有[quest point]字段的任务quest_point=10000
                    if(quest_piece > 0)
                        total_quest_piece_bonus += quest_piece;

                    //将该任务设置为已完成状态
                    WongWork_CQuestClear_setClearedQuest(user_quest.add(4), quest_id);

                    //本次自动完成任务计数
                    clear_quest_cnt++;
                }
            }
        }
    }

    //通知客户端更新
    if(clear_quest_cnt > 0)
    {
        //发送任务经验奖励
        if(total_exp_bonus > 0)
            api_CUser_gain_exp_sp(user, total_exp_bonus);
        //发送任务金币奖励
        if(total_gold_bonus > 0)
            CInventory_gain_money(CUserCharacInfo_getCurCharacInvenW(user), total_gold_bonus, 0, 0, 0);

        //通知客户端更新奖励数据
        if ( CUser_get_state(user) == 3 )
        {
            CUser_SendNotiPacket(user, 0, 2, 0);
            CUser_SendNotiPacket(user, 1, 2, 1);

            CUser_SendUpdateItemList(user, 1, 0, 0);
            CUser_sendCharacQp(user);
            CUser_sendCharacQuestPiece(user);
        }

        //通知客户端更新已完成任务列表
        CUser_send_clear_quest_list(user);

        //通知客户端更新任务列表
        var packet_guard = api_PacketGuard_PacketGuard();
        UserQuest_get_quest_info(user_quest, packet_guard);
        CUser_Send(user, packet_guard);
        Destroy_PacketGuard_PacketGuard(packet_guard);

        //公告通知客户端本次自动完成任务数据
        api_CUser_SendNotiPacketMessage(user, '已自动完成当前等级任务数量: ' + clear_quest_cnt, 14);
        api_CUser_SendNotiPacketMessage(user, '任务经验奖励: ' + total_exp_bonus, 14);
        api_CUser_SendNotiPacketMessage(user, '任务金币奖励: ' + total_gold_bonus, 14);
        api_CUser_SendNotiPacketMessage(user, '任务QuestPoint奖励: ' + total_quest_point_bonus, 14);
        api_CUser_SendNotiPacketMessage(user, '任务QuestPiece奖励: ' + total_quest_piece_bonus, 14);
    }


    //log('clear_all_quest_by_character_level end!');

    return;
}
//发送每日首次登陆奖励
function send_first_login_reward(user)
{
    //奖励道具列表(道具id, 每级奖励数量)
    var REWARD_LIST = [[8, 0.1], [3037, 10]];

    //获取玩家登录
    var cur_level = CUserCharacInfo_get_charac_level(user);
    for(var i=0; i<REWARD_LIST.length; i++)
    {
        //道具id
        var reward_item_id = REWARD_LIST[i][0];
        //道具数量
        var reward_item_cnt = 1 + Math.floor((cur_level * REWARD_LIST[i][1]));

        //发送道具到玩家背包
        api_CUser_AddItem(user, reward_item_id, reward_item_cnt);
    }
}
//角色每日首次登录奖励
function hook_user_first_login()
{
    //角色每日重置处理函数 Hook CUser::AddDailyItem
    Interceptor.attach(ptr(0x8656CAA), {

        onEnter: function (args) {
            //保存函数参数
            var user = args[0];

            console.log('[CUser::AddDailyItem] user=' + user);

            //发送每日首次登陆奖励
            send_first_login_reward(user);
        },
        onLeave: function (retval) {
        }
    });
}

//发送离线奖励
function send_offline_reward(user)
{
    //当前系统时间
    var cur_time = api_CSystemTime_getCurSec();

    //用户上次退出游戏时间
    var user_last_play_time = CUserCharacInfo_getCurCharacLastPlayTick(user);

    //新创建的角色首次登陆user_last_play_time为0
    if(user_last_play_time > 0)
    {
        //离线时长(分钟)
        var diff_time = (cur_time - user_last_play_time) / 60;

        //离线10min后开始计算
        if(diff_time < 0)
            return;

        //离线奖励最多发送3天
        if(diff_time > 3*24*60)
            diff_time = 3*24*60;

        //经验奖励: 每分钟当前等级经验的0.02%
        var REWARD_EXP_PER_MIN = 0.002;
        //金币奖励: 每分钟当前等级*100
        var REWARD_GOLD_PER_MIN = 10;

        //计算奖励
        var cur_level = CUserCharacInfo_get_charac_level(user);
        var reward_exp = Math.floor(CUserCharacInfo_get_level_up_exp(user, cur_level) * REWARD_EXP_PER_MIN * diff_time);
        var reward_gold = Math.floor(cur_level * REWARD_GOLD_PER_MIN * diff_time);

        //发经验
        api_CUser_gain_exp_sp(user, reward_exp);
        //发金币
        CInventory_gain_money(CUserCharacInfo_getCurCharacInvenW(user), reward_gold, 0, 0, 0);
        //通知客户端有游戏道具更新
        CUser_SendUpdateItemList(user, 1, 0, 0);

        //发消息通知客户端奖励已发送
        api_CUser_SendNotiPacketMessage(user,'离线：'+ parseInt(diff_time) +'分钟，已发送(经验奖励:' + reward_exp + ', 金币奖励:' + reward_gold + ')', 6);
    }
}

//角色登入登出处理
function hook_user_inout_game_world()
{
    //选择角色处理函数 Hook GameWorld::reach_game_world
    Interceptor.attach(ptr(0x86C4E50), {
        //函数入口, 拿到函数参数args
        onEnter: function (args) {
            //保存函数参数
            this.user = args[1];

            console.log('[GameWorld::reach_game_world] this.user=' + this.user);
        },
        //原函数执行完毕, 这里可以得到并修改返回值retval
        onLeave: function (retval) {
            //给角色发消息问候
             api_CUser_SendNotiPacketMessage(this.user, 'Hello ' + api_CUserCharacInfo_getCurCharacName(this.user), 1);
            // use_ftcoin_change_luck_point(this.user); //开启幸运点
            //离线奖励处理
           // send_offline_reward(this.user);

            //怪物攻城活动更新进度
/*             if(villageAttackEventInfo.state != VILLAGEATTACK_STATE_END)
            {
                //通知客户端打开活动UI
                notify_villageattack_score(this.user);

                //公告通知客户端活动进度
                event_villageattack_broadcast_diffcult();
            } */
        }
    });

    //角色退出时处理函数 Hook GameWorld::leave_game_world
    Interceptor.attach(ptr(0x86C5288), {
        onEnter: function (args) {

            var user = args[1];
            this.user = user;
            console.log('[GameWorld::leave_game_world] user=' + user);
        }
    });
    //角色退出时处理函数 Hook CGameManager::user_exit
    Interceptor.attach(ptr(0x082985a8), {

        onEnter: function (args) {

            var user = args[1];
            this.user = user;
            console.log('[CGameManager::user_exit] user=' + user);
        },
        onLeave: function (retval) {
            var accId = CUser_get_acc_id(this.user);
            // 清除账号仓库 释放空间
            if(accountCargfo[accId]){
                delete accountCargfo[accId];
                // console.log('clean accountCargfo accId:'+accId)
            }
        }
    });
}


function set_user_max_level(){

}

var heffPartyTag = false;
//处理GM信息
function hook_gm_command()
{
    //HOOK Dispatcher_New_Gmdebug_Command::dispatch_sig
    Interceptor.attach(ptr(0x820BBDE), {

        onEnter: function (args) {

            //获取原始封包数据
            var raw_packet_buf = api_PacketBuf_get_buf(args[2]);

            //解析GM DEBUG命令
            var msg_len = raw_packet_buf.readInt();
            var msg = raw_packet_buf.add(4).readUtf8String(msg_len);

            var user = args[1];


            console.log('收到GM_DEBUG消息: ['+ api_CUserCharacInfo_getCurCharacName(user) + '] ' + msg);

            //去除命令开头的 '//'
            msg = msg.slice(2);

            if(msg == 'test')
            {
                //向客户端发送消息
                api_CUser_SendNotiPacketMessage(user, '这是一条测试命令', 1);

                //执行一些测试代码

                return;
            }
            else if(msg.indexOf('move ') == 0)
            {
                //城镇瞬移
                var msg_group = msg.split(' ');
                if(msg_group.length == 5)
                {
                    var village = parseInt(msg_group[1]);
                    var area = parseInt(msg_group[2]);
                    var pos_x = parseInt(msg_group[3]);
                    var pos_y = parseInt(msg_group[4]);
                    GameWorld_move_area(G_GameWorld(), user, village, area, pos_x, pos_y, 0, 0, 0, 0, 0);
                }
                else
                {
                    api_CUser_SendNotiPacketMessage(user, '格式错误. 使用示例: //move 2 1 100 100', 2);
                }
            }else if(msg.indexOf('item ') == 0)
            {
                //获得物品
                var msg_group = msg.split(' ');
                if(msg_group.length == 3)
                {
                    var item_id = parseInt(msg_group[1]);
                    var item_cnt = parseInt(msg_group[2]);
                    //发送道具到玩家背包
                    api_CUser_AddItem(user, item_id, item_cnt);
                    api_CUser_SendNotiPacketMessage(user, 'GM命令完成', 1);
                }
                else
                {
                    api_CUser_SendNotiPacketMessage(user, '格式错误. item: //item 1 1', 2);
                }
            }else if(msg == 'attackstart')
            {	//GM模式开启怪物攻城
                on_start_event_villageattack()
            }
            else if(msg == 'attackend')
            {	//GM模式关闭怪物攻城
                on_end_event_villageattack();
            }else if(msg == 'onhell'){
                heffPartyTag = true;
                api_CUser_SendNotiPacketMessage(user, '开启深渊模式', 1);
            }else if(msg == 'offhell'){
                heffPartyTag = false;
                api_CUser_SendNotiPacketMessage(user, '关闭深渊模式', 1);
            }
        },
        onLeave: function (retval) {
        }
    });
}


//所有账号角色开启GM权限
function hook_check_gm()
{
    //GM账户
    //WongWork::CGMAccounts::isGM
    Interceptor.attach(ptr(0x8109346), {

        onEnter: function (args) {
        },
        onLeave: function (retval) {
            //强制返回true
            retval.replace(1);
        }
    });

    //GM角色
    //CUser::isGMUser
    Interceptor.attach(ptr(0x814589C), {

        onEnter: function (args) {
        },
        onLeave: function (retval) {
            //强制返回true
            retval.replace(1);
        }
    });
}
//解除每日创建角色数量限制
function disable_check_create_character_limit()
{
    //DB_CreateCharac::CheckLimitCreateNewCharac
    Interceptor.attach(ptr(0x8401922), {
        onEnter: function (args) {
        },
        onLeave: function (retval) {
            //强制返回允许创建
            retval.replace(1);
        }
    });
}


//修复绝望之塔
//skip_user_apc: 为true时, 跳过每10层的UserAPC
function fix_TOD(skip_user_apc)
{
    //每日进入次数限制
    //TOD_UserState::getEnterCount
    Interceptor.attach(ptr(0x08643872), {

        onEnter: function (args) {
            //今日已进入次数强制清零
            args[0].add(0x10).writeInt(0);
        },
        onLeave: function (retval) {
        }
    });

    //每10层挑战玩家APC 服务器内角色不足10个无法进入
    if(skip_user_apc)
    {
        //跳过10/20/.../90层
        //TOD_UserState::getTodayEnterLayer
        Interceptor.attach(ptr(0x0864383E), {

            onEnter: function (args) {
                //绝望之塔当前层数
                var today_enter_layer = args[1].add(0x14).readShort();

                if(((today_enter_layer%10) == 9) && (today_enter_layer > 0) && (today_enter_layer < 100))
                {
                    //当前层数为10的倍数时  直接进入下一层
                    args[1].add(0x14).writeShort(today_enter_layer + 1);
                }
            },
            onLeave: function (retval) {
            }
        });
    }



    //修复金币异常
    //CParty::UseAncientDungeonItems
    var CParty_UseAncientDungeonItems_ptr = ptr(0x859EAC2);
    var CParty_UseAncientDungeonItems = new NativeFunction(CParty_UseAncientDungeonItems_ptr,  'int', ['pointer', 'pointer', 'pointer', 'pointer'], {"abi":"sysv"});
    Interceptor.replace(CParty_UseAncientDungeonItems_ptr, new NativeCallback(function (party, dungeon, inven_item, a4) {

        //当前进入的地下城id
        var dungeon_index = CDungeon_get_index(dungeon);

        //根据地下城id判断是否为绝望之塔
        if((dungeon_index >= 11008) && (dungeon_index <= 11107))
        {
            //绝望之塔 不再扣除金币
            return 1;
        }

        //其他副本执行原始扣除道具逻辑
        return CParty_UseAncientDungeonItems(party, dungeon, inven_item, a4);
    }, 'int', ['pointer', 'pointer', 'pointer', 'pointer']));

}

//允许赛利亚房间的人互相可见
function share_seria_room()
{
    //Hook Area::insert_user
    Interceptor.attach(ptr(0x86C25A6), {

        onEnter: function (args) {
            //修改标志位, 让服务器广播赛利亚旅馆消息
            args[0].add(0x68).writeInt(0);
        },
        onLeave: function (retval) {
        }
    });
}

// 史诗免确认
function cancel_epic_ok()
{
    Memory.patchCode(ptr(0x085A56CE).add(2), 1, function (code) {
        var cw = new X86Writer(code, { pc: ptr(0x085A56CE).add(2) });
        cw.putU8(9);
        cw.flush();
    });
    Interceptor.attach(ptr(0x08150f18), {
        onLeave: function (retval) {
            retval.replace(0);
        }
    });
}

/**
 * 开启创建缔造
 */
function enable_createCreator(){
    Memory.patchCode(ptr(0x081C029E).add(1), 1, function (code) {
        var cw = new X86Writer(code, { pc: ptr(0x081C029E).add(1) });
        cw.putU8(11);
        cw.flush();
    });
}
// DOF_DP2_ITEM_OWNERSHIP_V1: DP2 owns these items only in the combined mode.
function dp2_owns_item(item_id) {
    if (String(item_id) !== '2021458801' && String(item_id) !== '2021458802') return false;
    var getenv = new NativeFunction(Module.getExportByName(null, 'getenv'),
        'pointer', ['pointer'], { abi: 'sysv' });
    var mode = getenv(Memory.allocUtf8String('PLUGIN_MODE'));
    return !mode.isNull() && mode.readUtf8String() === 'dp2-frida';
}
function use_item_handler(user, item_id){
    if (dp2_owns_item(item_id)) return;
    // 任务清除券，  任务完成券 成就任务完成券 主线等


    if('10000114' == item_id)
    {
        //重置任务
        //api_reset_all_quest(user);

    }
    if('2021458802' == item_id)
    {
        //所有的任务完成券
        clear_all_quest_by_character_level(user);
        
    }

    if('690000119' === String(item_id))
    {
        //以接任务完成券
        clear_doing_quest(user);
    }
    if('7577' == item_id){
        // 装备继承  完美继承强化，增幅 ，宝珠 ，锻造（还可以实现 +n的锻造券）
        equInherit(user);
    }

    if('2021458801' == item_id){
        // 装备跨界
        crossover(user);
    }
    if('20220912' == item_id){
        // 分解
        decompose(user)
    }
     if('1230' == item_id){
        // 魔法封印重置
        qc(user)
    }
    if('2749101' == item_id){
        // 称号合成
        chhc(user)
    }


    // DOF_SINGLE_RECYCLE_V1: consume at most one matching object per use.
    // Preserve the shared item ID; try equipment, then avatar, then creature.
    if ('202145880' == item_id) {
        if (!zbhs(user) && !szhs(user)) cwhs(user);
    }

    if ('7576' == item_id) {

        // 随机强化 ok
        randomIncrease(user)
        }

     if('61000001' == item_id){
        // 每日任务完成
        api_force_clear_quest_list(user, quest_list)
    }
    if('8068' == item_id){
        // 初阶异界入场重置
        resetResetDimensionInout(user,0)
        resetResetDimensionInout(user,1)
        resetResetDimensionInout(user,2)
    }
    if('8069' == item_id){
        // 高阶异界入场重置
        resetResetDimensionInout(user,3)
        resetResetDimensionInout(user,4)
        resetResetDimensionInout(user,5)
    }
}
//增加魔法封印装备的魔法封印等级
function _boost_random_option_equ(inven_item)
{
    //空装备
    if(Inven_Item_isEmpty(inven_item))
        return false;

    //获取装备当前魔法封印属性
    var random_option = inven_item.add(37);

    //随机选取一个词条槽
    var random_option_slot = get_random_int(0, 3);

    //若词条槽已有魔法封印
    if(random_option.add(3*random_option_slot).readU8())
    {
        //每个词条有2个属性值
        var value_slot = get_random_int(1, 3);

        //当前词条等级
        var random_option_level = random_option.add(3*random_option_slot + value_slot).readU8();
        if(random_option_level < 0xFF)
        {
            //1%概率词条等级+1
            if(get_random_int(random_option_level, 100000) < 1000)
            {
                random_option.add(3*random_option_slot + value_slot).writeU8(random_option_level + 1);
                return true;
            }
        }
    }

    return false;
}

//穿戴中的魔法封印装备词条升级
function boost_random_option_equ(user)
{
    //遍历身上的装备 为拥有魔法封印属性的装备提升魔法封印等级
    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    for(var slot=10; slot<=21; slot++)
    {
        var inven_item = CInventory_GetInvenRef(inven, INVENTORY_TYPE_BODY, slot);
        if(_boost_random_option_equ(inven_item))
        {
            api_CUser_SendNotiPacketMessage(user, '通知：魔法封印装备升级', 6);
            //通知客户端更新
            CUser_SendUpdateItemList(user, 1, 3, slot);
        }
    }
}


//魔法封印属性转换时可以继承
function change_random_option_inherit()
{
    //random_option::CRandomOptionItemHandle::change_option
    Interceptor.attach(ptr(0x85F3340), {
        onEnter: function (args) {
            //保存原始魔法封印属性
            this.random_option = args[7];
            //本次变换的属性编号
            this.change_random_option_index = args[6].toInt32();

            //记录原始属性
            this.random_optio_type = this.random_option.add(3*this.change_random_option_index).readU8();
            this.random_optio_value_1 = this.random_option.add(3*this.change_random_option_index + 1).readU8();
            this.random_optio_value_2 = this.random_option.add(3*this.change_random_option_index + 2).readU8();
        },
        onLeave: function (retval) {
            //魔法封印转换成功
            if(retval == 1)
            {
                //获取未被附魔的魔法封印槽
                var index = -1;
                if(this.random_option.add(0).readU8() == 0)
                    index = 0;
                else if(this.random_option.add(3).readU8() == 0)
                    index = 1;
                else if(this.random_option.add(6).readU8() == 0)
                    index = 2;

                //当魔法封印词条不足3个时, 若变换出等级极低的属性, 可直接附魔到装备空的魔法封印槽内
                if(index >= 0)
                {
                    if((this.random_option.add(11).readU8() <= 5) && (this.random_option.add(12).readU8() <= 5))
                    {
                        //魔法封印附魔
                        this.random_option.add(3*index).writeU8(this.random_option.add(10).readU8());
                        this.random_option.add(3*index + 1).writeU8(this.random_option.add(11).readU8());
                        this.random_option.add(3*index + 2).writeU8(this.random_option.add(12).readU8());

                        //清空本次变换的属性(可以继续选择其他词条变换)
                        this.random_option.add(10).writeInt(0);

                        return;
                    }
                }

                //用变换后的词条覆盖原始魔法封印词条
                this.random_option.add(3*this.change_random_option_index).writeU8(this.random_option.add(10).readU8());

                //若变换后的属性低于原来的值 则继承原有属性值 否则使用变换后的属性
                if(this.random_option.add(11).readU8() > this.random_optio_value_1)
                    this.random_option.add(3*this.change_random_option_index + 1).writeU8(this.random_option.add(11).readU8());

                if(this.random_option.add(12).readU8() > this.random_optio_value_2)
                    this.random_option.add(3*this.change_random_option_index + 2).writeU8(this.random_option.add(12).readU8());

                //清空本次变换的属性(可以继续选择其他词条变换)
                this.random_option.add(10).writeInt(0);
            }
        }
    });
}

//魔法封印自动解封
function auto_unseal_random_option_equipment()
{
    //CInventory::insertItemIntoInventory
    Interceptor.attach(ptr(0x8502D86), {
        onEnter: function (args) {
            this.user = args[0].readPointer();
        },
        onLeave: function (retval) {

            //物品栏新增物品的位置
            var slot = retval.toInt32();
            if(slot > 0 )
            {
                //获取道具的角色
                var user = this.user;

                //角色背包
                var inven = CUserCharacInfo_getCurCharacInvenW(user);

                //背包中新增的道具
                var inven_item = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, slot);

                //过滤道具类型
                if(!Inven_Item_isEquipableItemType(inven_item))
                    return;

                //装备id
                var item_id = Inven_Item_getKey(inven_item);

                //pvf中获取装备数据
                var citem = CDataManager_find_item(G_CDataManager(), item_id);

                //检查装备是否为魔法封印类型
                if(!CEquipItem_IsRandomOption(citem))
                    return;

                //是否已被解除魔法封印（魔法封印前10个字节是否为0）
                var random_option = inven_item.add(37);
                if(random_option.readU32() || random_option.add(4).readU32() || random_option.add(8).readShort())
                {
                    return;
                }

                //尝试解除魔法封印
                var ret = random_option_CRandomOptionItemHandle_give_option(ptr(0x941F820).readPointer(), item_id, CItem_GetRarity(citem), CItem_GetUsableLevel(citem)
                    , CItem_GetItemGroupName(citem), CEquipItem_GetRandomOptionGrade(citem), inven_item.add(37));
                if(ret)
                {
                    //通知客户端有装备更新
                    CUser_SendUpdateItemList(user, 1, 0, slot);
                }
            }
        }
    });
}

 //史诗魔法封印变换券
function qc(user)
{    
    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    //遍历装备
    for(var i = 9; i <=16; i++)
    {
        //获取物品栏第一排的装备
        var equIn = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, i); //遍历类型为物品栏
        var inItemId = Inven_Item_getKey(equIn) //道具id
        var inItemData = CDataManager_find_item(G_CDataManager(),inItemId); //获取pvf数据
		var equRarity = CItem_GetRarity(inItemData); // 稀有度  >=3  粉色以上
		if(equRarity == 4){
		    var inEqu_type = inItemData.add(141*4).readU32(); // 装备类型10武器 11称号	       
		    //清空所有魔法封印字节
		    if(inEqu_type!=11){
                equIn.add(37).writeU8(0);
                equIn.add(38).writeU8(0);    			
                equIn.add(39).writeU8(0);    			
                equIn.add(40).writeU8(0);   			
                equIn.add(41).writeU8(0);    			
                equIn.add(42).writeU8(0);    			
                equIn.add(43).writeU8(0);    
                equIn.add(44).writeU8(0);    
                equIn.add(45).writeU8(0);    
                equIn.add(46).writeU8(0); 			
                equIn.add(47).writeU8(0);    
                equIn.add(48).writeU8(0);    
                equIn.add(49).writeU8(0);    
                equIn.add(50).writeU8(0); 
                //尝试解除魔法封印
                var ret = random_option_CRandomOptionItemHandle_give_option(ptr(0x941F820).readPointer(), inItemId, CItem_GetRarity(inItemData), CItem_GetUsableLevel(inItemData)
                    , CItem_GetItemGroupName(inItemData), CEquipItem_GetRandomOptionGrade(inItemData), equIn.add(37));
                if(ret)
                {
                    //通知客户端有装备更新
                    CUser_SendUpdateItemList(user, 1, 0, i);
                }
			}			
		}
	}	
}



function disintegrate_item_handler(item_id){
    // 分解成就
    //
}

//捕获玩家游戏事件
function hook_history_log()
{
    //cHistoryTrace::operator()
    Interceptor.attach(ptr(0x854F990), {

        onEnter: function (args) {

            //解析日志内容: "18000008",18000008,D,145636,"nickname",1,72,8,0,192.168.200.1,192.168.200.1,50963,11, DungeonLeave,"龍人之塔",0,0,"aabb","aabb","N/A","N/A","N/A"
            var history_log = args[1].readUtf8String(-1);
            var group = history_log.split(',');

            //角色信息
            var account_id = parseInt(group[1]);
            var time_hh_mm_ss = group[3];
            var charac_name = group[4];
            var charac_no = group[5];
            var charac_level = group[6];
            var charac_job = group[7];
            var charac_growtype = group[8];
            var user_web_address = group[9];
            var user_peer_ip2 = group[10];
            var user_port = group[11];
            var channel_index = group[12];              //当前频道id

            //玩家游戏事件
            var game_event = group[13].slice(1);        //删除多余空格

            //触发游戏事件的角色
            var user = GameWorld_find_user_from_world_byaccid(G_GameWorld(), account_id);

            if(user.isNull())
                return;

            //道具减少:  Item-,1,10000113,63,1,3,63,0,0,0,0,0,0000000000000000000000000000,0,0,00000000000000000000
            if(game_event == 'Item-')
            {
                var item_id = parseInt(group[15]);                //本次操作道具id
                var item_cnt = parseInt(group[17]);               //本次操作道具数量
                var reason = parseInt(group[18]);                 //本次操作原因


                //log('玩家[' + charac_name + ']道具减少, 原因:' + reason + '(道具id=' + item_id + ', 使用数量=' + item_cnt);

                if(5 == reason)
                {
                    //丢弃道具
                }
                else if(3 == reason)
                {
                    //使用道具
                    //这里并未改变道具原始效果 原始效果成功执行后触发下面的代码
                    use_item_handler(user,item_id)
                }
                else if(9 == reason)
                {
                    //分解道具
                    disintegrate_item_handler(item_id)
                }
                else if(10 == reason)
                {
                    //使用属性石头
                }
            }
            else if(game_event == 'KillMob')
            {
                //杀死怪物
                //魔法封印装备词条升级
                boost_random_option_equ(user);
            }
            else if(game_event == 'Money+')
            {
                var cur_money = parseInt(group[14]);                //当前持有的金币数量
                var add_money = parseInt(group[15]);                //本次获得金币数量
                var reason = parseInt(group[16]);                   //本次获得金币原因

                //log('玩家[' + charac_name + ']获取金币, 原因:' + reason + '(当前持有金币=' + cur_money + ', 本次获得金币数量=' + add_money);

                if(4 == reason)
                {
                    //副本拾取
                }
                else if(5 == reason)
                {
                    //副本通关翻牌获取金币
                }
            }
            else if(game_event == 'DungeonLeave')
            {
                //离开副本
                //刷完副本后, 重置异界+极限祭坛次数
                // CUser_DimensionInoutUpdate(user, 1, 1);
            }
            else if (game_event == 'Item+') {
                var item_id = parseInt(group[15]);
                var group_18 = parseInt(group[18]);
                if (group_18 == 4) {
                
                if (item_id == 1904040077)
                processing_data(1904040077, user, 3171, 20, 0);

                }
                }
        },
        onLeave: function (retval) {

        }
    });
}
//在线奖励
function enable_online_reward()
{
    //在线每5min发一次奖, 在线时间越长, 奖励越高
    //CUser::WorkPerFiveMin
    Interceptor.attach(ptr(0x8652F0C), {
        onEnter: function (args) {
            var user = args[0];

            //当前系统时间
            var cur_time = api_CSystemTime_getCurSec();

            //本次登录时间
            var login_tick = CUserCharacInfo_GetLoginTick(user);


            if(login_tick > 0)
            {
                //在线时长(分钟)
                var diff_time = Math.floor((cur_time - login_tick) / 60);

                //在线10min后开始计算
                if(diff_time < 10)
                    return;

                //在线奖励最多发送1天
                if(diff_time > 1*24*60)
                    return;

                //奖励: 每分钟0.1点券
                var REWARD_CASH_CERA_PER_MIN = 0.1;

                //计算奖励
                var reward_cash_cera = Math.floor(diff_time*REWARD_CASH_CERA_PER_MIN);

                //发点券
                api_recharge_cash_cera(user, reward_cash_cera);

                //发消息通知客户端奖励已发送
                api_CUser_SendNotiPacketMessage(user, '[' + get_timestamp() + '] 在线奖励已发送(当前阶段点券奖励:' + reward_cash_cera + ')', 6);
            }
        },
        onLeave: function (retval) {
        }
    });
}


//称号合成
function chhc(user){
var inven = CUserCharacInfo_getCurCharacInvenW(user); // 获取当前角色的背包对象
var item1 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9); // 获取第9个装备物品的对象
var itemId1 = Inven_Item_getKey(item1); // 获取该物品的ID
var item2 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 10); // 获取第10个装备物品的对象
var itemId2 = Inven_Item_getKey(item2); // 获取该物品的ID

if (itemId1 == 100331738 && itemId2 == 202311175) {
		Inven_Item_getKey(item1)
		var baozhu = item1.add(13).readU32();	
    Inven_Item_reset(item1); // 将第9个装备物品从背包中删除
        CUser_SendUpdateItemList(user, 1, 0, 9); 
    Inven_Item_reset(item2); // 将第10个装备物品从背包中删除
        CUser_SendUpdateItemList(user, 1, 0, 10); 
	api_CUser_AddItem(user, 100331747, 1); 
	var item3 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9);
	item3.add(13).writeU32(baozhu);
	CUser_SendUpdateItemList(user, 1, 0, 9);
}
	if (itemId1 == 202311175 && itemId2 == 100331738) {
		Inven_Item_getKey(item2)
		var baozhu = item2.add(13).readU32();
    Inven_Item_reset(item1); // 将第9个装备物品从背包中删除
        CUser_SendUpdateItemList(user, 1, 0, 9); 
    Inven_Item_reset(item2); // 将第10个装备物品从背包中删除
        CUser_SendUpdateItemList(user, 1, 0, 10); 
	api_CUser_AddItem(user, 100331747, 1); 
	var item3 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9);
	item3.add(13).writeU32(baozhu);
	CUser_SendUpdateItemList(user, 1, 0, 9);
}
}


//装备回收
function zbhs(user){
    var inven = CUserCharacInfo_getCurCharacInvenW(user); // 获取当前角色的背包对象
    var item1 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9); // 获取第9个装备物品的对象
    var itemId1 = Inven_Item_getKey(item1); // 获取该物品的ID

    var arr =[102040046,102020050,102010114,102000057,102030046,104010041,104020043,104040045,104030050,104000047,106040048,106020042,106010044,106000045,106030044,108030041,108000045,108010043,108040042,108020042,101040046,101030042,101010045,101020047,101000028,109000043,109010042,109020040]
for (var i=0 ;i<arr.length ;i++)

    if (itemId1 == arr[i] ) {
		Inven_Item_getKey(item1)
		var baozhu = item1.add(13).readU32();	
    Inven_Item_reset(item1); // 将第9个装备物品从背包中删除
        CUser_SendUpdateItemList(user, 1, 0, 9); 
	api_CUser_AddItem(user, 3285, 100); 
    api_GameWorld_SendNotiPacketMessage('装备回收成功！', 14);
    return true;
}

    return false;
}


//时装回收
function szhs(user){
    var inven = CUserCharacInfo_getCurCharacInvenW(user); // 获取当前角色的背包对象
    var item1 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_AVARTAR, 0); // 获取第1个时装栏的对象
    var itemId1 = Inven_Item_getKey(item1); // 获取该物品的ID

    var arr =[101550609,101520590,101500787,101530541,101540702]
for (var i=0 ;i<arr.length ;i++)

    if (itemId1 == arr[i] ) {
		Inven_Item_getKey(item1)
    Inven_Item_reset(item1); // 将第1个时装栏部位的时装从背包中删除
    CUser_send_itemspace(user,ENUM_ITEMSPACE_AVATAR);//更新时装栏
	api_CUser_AddItem(user, 3285, 100); 
    api_GameWorld_SendNotiPacketMessage('时装回收成功！', 14);
    return true;
}

    return false;
}


//宠物回收
function cwhs(user){
    var inven = CUserCharacInfo_getCurCharacInvenW(user); // 获取当前角色的背包对象
    var item1 = CInventory_GetInvenRef(inven, INVENTORY_TYPE_CREATURE, 0); // 获取第1个宠物栏的对象
    var itemId1 = Inven_Item_getKey(item1); // 获取该物品的ID

    var arr =[63040,101520590,101500787,101530541,101540702]
for (var i=0 ;i<arr.length ;i++)

    if (itemId1 == arr[i] ) {
		Inven_Item_getKey(item1)
    Inven_Item_reset(item1); // 将第1个宠物栏部位的宠物从背包中删除
    CUser_send_itemspace(user,ENUM_ITEMSPACE_CREATURE);//更新宠物栏
	api_CUser_AddItem(user, 3285, 100); 
    api_GameWorld_SendNotiPacketMessage('宠物回收成功！', 14);
    return true;
}

    return false;
}

function randomNumber() {
    var nums = [11,12,13,14,15,16, 17, 18];
    var weights = [1, 0.9,0.6, 0.3, 0.1,0.06,0.05,0.01];
    
    var totalWeight = weights.reduce(function (a, b) {
    
    return a + b;
    
    }, 0);
    
    var rnd = Math.random() * totalWeight;
    
    var sum = 0;
    
    var result = null;
    
    for (var i = 0; i < nums.length; i++) {
    sum += weights[i];
    if (rnd < sum) {
    result = nums[i];
    break;
    }
    }
    return result;
    }
/**
* 随机增幅
*
* @param user 用户
*/

function randomIncrease(user) {

    var inven = CUserCharacInfo_getCurCharacInvenW(user);
    var equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, 9);
    var itemId = Inven_Item_getKey(equ);
    var inUpgrade_level = equ.add(6).readU8();
    var append = equ.add(17).readU16();
    var characName = api_CUserCharacInfo_getCurCharacName(user);
    var equipmentName = api_CItem_GetItemName(itemId);


    var inItemData = CDataManager_find_item(G_CDataManager(),itemId);
    var inEqu_type = inItemData.add(141*4).readU32();

    if (itemId=0) {
        api_CUser_SendNotiPacketMessage(user, "装备栏无装备！", 1);
        api_CUser_AddItem(user, 7576, 1); 
    } 
else if(inEqu_type !=11 ){
            if (equ != null) {
                if (append != null && append != 0) {
                    api_CUser_SendNotiPacketMessage(user, "使用失败：此装备有异界气息！", 1);
                    api_CUser_AddItem(user, 7576, 1); 
                } else {
           
                
                if (inUpgrade_level < 31) {
                    var now = randomNumber();
                    equ.add(6).writeU8(now);
                    CUser_SendUpdateItemList(user, 1, 0, 9);
                    api_CUser_SendNotiPacketMessage(user, "[" + [characName] + "] 强化 +" + now + " " + [equipmentName] + "成功", 0);
                    } else {
                    api_CUser_SendNotiPacketMessage(user, "使用失败：当前装备强化等级，已到最大限制！", 1);
                    api_CUser_AddItem(user, 7576, 1); 
                    }
                }
                }
               
            }
            
            else {
                api_CUser_SendNotiPacketMessage(user, "称号无法强化！", 1);
                api_CUser_AddItem(user, 7576, 1); 
                }
        
      

        }


/*
    if (itemId1 != 0 ) {
        if(inEqu_type !=11 ){
            if (equ != null) {
                if (append != null && append != 0) {
                    api_CUser_SendNotiPacketMessage(user, "使用失败：此装备有异界气息！", 1);
                } else {
           
                
                if (inUpgrade_level < 31) {
                    var now = randomNumber();
                    equ.add(6).writeU8(now);
                    CUser_SendUpdateItemList(user, 1, 0, 9);
                    api_CUser_SendNotiPacketMessage(user, "[" + [characName] + "] 强化 +" + now + " " + [equipmentName] + "成功", 0);
                    } else {
                    api_CUser_SendNotiPacketMessage(user, "使用失败：当前装备强化等级，已到最大限制！", 1);
                    }
                }
                }
               
            }
            
            else {
                api_CUser_SendNotiPacketMessage(user, "称号无法强化！", 1);
                }
        
        }
        else {
            api_CUser_SendNotiPacketMessage(user, "装备栏无装备！", 1);
            }
        }
*/
//抽取幸运在线玩家活动
function on_event_lucky_online_user()
{
    //在线玩家数量
    var online_player_cnt = GameWorld_get_UserCount_InWorld(G_GameWorld());

    //没有在线玩家时跳过本轮活动
    if(online_player_cnt > 0)
    {
        //幸运在线玩家
        var lucky_user = null;

        //遍历在线玩家列表
        var it = api_gameworld_user_map_begin();
        var end = api_gameworld_user_map_end();

        //随机抽取一名在线玩家
        var user_index = get_random_int(0, online_player_cnt);

        while(user_index >= 0)
        {
            user_index--;

            //判断在线玩家列表遍历是否已结束
            if(gameworld_user_map_not_equal(it, end))
            {
                //当前被遍历到的玩家
                lucky_user = api_gameworld_user_map_get(it);

                //state > 2 的玩家才有资格参加抽奖
                if(CUser_get_state(lucky_user) < 3)
                {
                    lucky_user = null;
                }

                //继续遍历下一个玩家
                api_gameworld_user_map_next(it);
            }
            else
            {
                break;
            }
        }

        //给幸运玩家发奖
        if(lucky_user)
        {
            //获取该活动配置文件
            var config = global_config["lucky_online_user_event"];

            //道具奖励
            var reward_msg = '';
            for(var i=0; i<config["reward_items_list"].length; ++i)
            {
                var item_id = config["reward_items_list"][i][0];
                var item_cnt = config["reward_items_list"][i][1];

                api_CUser_AddItem(lucky_user, item_id, item_cnt);

                reward_msg += api_CItem_GetItemName(item_id) + '*' + item_cnt + '\n';
            }

            //点券奖励
            api_recharge_cash_cera(lucky_user, config["reward_cash_cera"]);
            reward_msg += '点券*' + config["reward_cash_cera"];

            //世界广播本轮幸运在线玩家
            api_GameWorld_SendNotiPacketMessage('<幸运在线玩家活动>开奖:\n恭喜 【' + api_CUserCharacInfo_getCurCharacName(lucky_user) + '】 成为本轮活动幸运玩家, 已发送奖励:\n' + reward_msg, 0);

            //log('<幸运在线玩家活动>幸运玩家:' + api_CUserCharacInfo_getCurCharacName(lucky_user));
        }
    }

    //定时开启下一次活动
    start_event_lucky_online_user();
}

//每小时开启抽取幸运在线玩家活动
function start_event_lucky_online_user()
{
    //获取当前系统时间
    var cur_time = api_CSystemTime_getCurSec();
    //计算距离下次抽取幸运玩家时间(每小时执行一次)
    var delay_time = 3600 - (cur_time % 3600) + 3;

    //log('距离下次抽取幸运在线玩家还有:' + delay_time/60 + '分钟');

    //定时开启活动
    api_scheduleOnMainThread_delay(on_event_lucky_online_user, null, delay_time*1000);
}
//获取时装在数据库中的uid
function api_get_avartar_ui_id(avartar)
{
    return avartar.add(7).readInt();
}

//设置时装插槽数据(时装插槽数据指针, 插槽, 徽章id)
//jewel_type: 红=0x1, 黄=0x2, 绿=0x4, 蓝=0x8, 白金=0x10
function api_set_JewelSocketData(jewelSocketData, slot, emblem_item_id)
{
    if(!jewelSocketData.isNull())
    {
        //每个槽数据长6个字节: 2字节槽类型+4字节徽章item_id
        //镶嵌不改变槽类型, 这里只修改徽章id
        jewelSocketData.add(slot*6+2).writeInt(emblem_item_id);
    }

    return;
}

//修复时装镶嵌
function fix_use_emblem()
{
    //Dispatcher_UseJewel::dispatch_sig
    Interceptor.attach(ptr(0x8217BD6), {

        onEnter: function (args) {

            try {
                var user = args[1];
                var packet_buf = args[2];

                log('收到角色[' + api_CUserCharacInfo_getCurCharacName(user) + ']的镶嵌请求');

                //校验角色状态是否允许镶嵌
                var state = CUser_get_state(user);
                if(state != 3)
                {
                    return;
                }


                //解析packet_buf

                //时装所在的背包槽
                var avartar_inven_slot = api_PacketBuf_get_short(packet_buf);
                //时装item_id
                var avartar_item_id = api_PacketBuf_get_int(packet_buf);
                //本次镶嵌徽章数量
                var emblem_cnt = api_PacketBuf_get_byte(packet_buf);

                //log('avartar_inven_slot=' + avartar_inven_slot + ', avartar_item_id=' + avartar_item_id + ', emblem_cnt=' + emblem_cnt);

                //获取时装道具
                var inven = CUserCharacInfo_getCurCharacInvenW(user);
                var avartar = CInventory_GetInvenRef(inven, INVENTORY_TYPE_AVARTAR, avartar_inven_slot);

                //校验时装 数据是否合法
                if(Inven_Item_isEmpty(avartar) || (Inven_Item_getKey(avartar) != avartar_item_id) || CUser_CheckItemLock(user, 2, avartar_inven_slot))
                {
                    return;
                }

                //获取时装插槽数据
                var avartar_add_info = Inven_Item_get_add_info(avartar);
                var inven_avartar_mgr = CInventory_GetAvatarItemMgrR(inven);
                var jewel_socket_data = WongWork_CAvatarItemMgr_getJewelSocketData(inven_avartar_mgr, avartar_add_info);
                //log('jewel_socket_data=' + jewel_socket_data + ':' + bin2hex(jewel_socket_data, 30));

                if(jewel_socket_data.isNull())
                {
                    return;
                }

                //最多只支持3个插槽
                if(emblem_cnt <= 3)
                {
                    var emblems = {};

                    for(var i=0; i<emblem_cnt; i++)
                    {
                        //徽章所在的背包槽
                        var emblem_inven_slot = api_PacketBuf_get_short(packet_buf);
                        //徽章item_id
                        var emblem_item_id = api_PacketBuf_get_int(packet_buf);
                        //该徽章镶嵌的时装插槽id
                        var avartar_socket_slot = api_PacketBuf_get_byte(packet_buf);

                        //log('emblem_inven_slot=' + emblem_inven_slot + ', emblem_item_id=' + emblem_item_id + ', avartar_socket_slot=' + avartar_socket_slot);

                        //获取徽章道具
                        var emblem = CInventory_GetInvenRef(inven, INVENTORY_TYPE_ITEM, emblem_inven_slot);

                        //校验徽章及插槽数据是否合法
                        if(Inven_Item_isEmpty(emblem) || (Inven_Item_getKey(emblem) != emblem_item_id) || (avartar_socket_slot >= 3))
                        {
                            return;
                        }

                        //校验徽章是否满足时装插槽颜色要求

                        //获取徽章pvf数据
                        var citem = CDataManager_find_item(G_CDataManager(), emblem_item_id);
                        if(citem.isNull())
                        {
                            return;
                        }

                        //校验徽章类型
                        if(!CItem_is_stackable(citem) ||  (CStackableItem_GetItemType(citem) != 20))
                        {
                            return;
                        }

                        //获取徽章支持的插槽
                        var emblem_socket_type = CStackableItem_getJewelTargetSocket(citem);

                        //获取要镶嵌的时装插槽类型
                        var avartar_socket_type = jewel_socket_data.add(avartar_socket_slot*6).readShort();

                        if(!(emblem_socket_type & avartar_socket_type))
                        {
                            //插槽类型不匹配
                            //log('socket type not match!');
                            return;
                        }

                        emblems[avartar_socket_slot] = [emblem_inven_slot, emblem_item_id];
                    }



                    //开始镶嵌
                    for (var avartar_socket_slot in emblems) {
                        //删除徽章
                        var emblem_inven_slot = emblems[avartar_socket_slot][0];
                        CInventory_delete_item(inven, 1, emblem_inven_slot, 1, 8, 1);

                        //设置时装插槽数据
                        var emblem_item_id = emblems[avartar_socket_slot][1];
                        api_set_JewelSocketData(jewel_socket_data, avartar_socket_slot, emblem_item_id);

                        //log('徽章item_id=' + emblem_item_id + '已成功镶嵌进avartar_socket_slot=' + avartar_socket_slot + '的槽内!');
                    }

                    //时装插槽数据存档
                    DB_UpdateAvatarJewelSlot_makeRequest(CUserCharacInfo_getCurCharacNo(user), api_get_avartar_ui_id(avartar), jewel_socket_data);

                    //通知客户端时装数据已更新
                    CUser_SendUpdateItemList(user, 1, 1, avartar_inven_slot);

                    //回包给客户端
                    var packet_guard = api_PacketGuard_PacketGuard();
                    InterfacePacketBuf_put_header(packet_guard, 1, 204);
                    InterfacePacketBuf_put_int(packet_guard, 1);
                    InterfacePacketBuf_finalize(packet_guard, 1);
                    CUser_Send(user, packet_guard);
                    Destroy_PacketGuard_PacketGuard(packet_guard);

                    //log('镶嵌请求已处理完成!');
                }


            } catch (error) {
                //log('fix_use_emblem throw Exception:' + error);
            }


        },
        onLeave: function (retval) {
            //返回值改为0  不再踢线
            retval.replace(0);
        }
    });


}


//根据角色id查询角色名
function api_get_charac_name_by_charac_no(charac_no)
{
    //从数据库中查询角色名
    if(api_MySQL_exec(mysql_taiwan_cain, "select charac_name from charac_info where charac_no=" + charac_no + ";"))
    {
        if(MySQL_get_n_rows(mysql_taiwan_cain) == 1)
        {
            if(MySQL_fetch(mysql_taiwan_cain))
            {
                var charac_name = api_MySQL_get_str(mysql_taiwan_cain, 0);
                return charac_name;
            }
        }
    }

    return charac_no.toString();
}
//怪物攻城活动当前状态
const VILLAGEATTACK_STATE_P1 = 0;               //一阶段
const VILLAGEATTACK_STATE_P2 = 1;               //二阶段
const VILLAGEATTACK_STATE_P3 = 2;               //三阶段
const VILLAGEATTACK_STATE_END = 3;              //活动已结束

const TAU_CAPTAIN_MONSTER_ID = 50071;           //牛头统帅id(P1阶段击杀该怪物可提升活动难度等级)
const GBL_POPE_MONSTER_ID = 262;                //GBL教主教(P2/P3阶段城镇存在该怪物 持续减少PT点数)
const TAU_META_COW_MONSTER_ID = 17;             //机械牛(P3阶段世界BOSS)


const EVENT_VILLAGEATTACK_START_HOUR = 12;      //每日北京时间20点开启活动
const EVENT_VILLAGEATTACK_TARGET_SCORE = [1, 2, 3];      //各阶段目标PT
const EVENT_VILLAGEATTACK_TOTAL_TIME = 1800;    //活动总时长(秒)

//怪物攻城活动数据
var villageAttackEventInfo = {
    'state': VILLAGEATTACK_STATE_END,           //活动当前状态
    'score': 0,                                 //当前阶段频道内总PT
    'start_time': 0,                            //活动开始时间(UTC)
    'difficult': 0,                             //活动难度(0-4)
    'next_village_monster_id': 0,               //下次刷新的攻城怪物id
    'last_killed_monster_id': 0,                //上次击杀的攻城怪物id
    'p2_last_killed_monster_time': 0,           //P2阶段上次击杀攻城怪物时间
    'p2_kill_combo': 0,                         //P2阶段连续击杀相同攻城怪物数量
    'gbl_cnt': 0,                               //城镇中存活的GBL主教数量
    'defend_success': 0,                        //怪物攻城活动防守成功

    'user_pt_info': {},                         //角色个人pt数据
}

//从数据库载入怪物攻城活动数据
function event_villageattack_load_from_db()
{
    if(api_MySQL_exec(mysql_frida, "select event_info from game_event where event_id = 'villageattack';"))
    {
        if(MySQL_get_n_rows(mysql_frida) == 1)
        {
            MySQL_fetch(mysql_frida);

            var info = api_MySQL_get_str(mysql_frida, 0);
            try
            {
                var decoded = JSON.parse(info);
                if(decoded == null || typeof decoded !== 'object' || Array.isArray(decoded)) return false;
                villageAttackEventInfo = decoded;
            }
            catch(error)
            {
                return false;
            }
            return true;
        }
        return MySQL_get_n_rows(mysql_frida) === 0;
    }
    return false;
}

//怪物攻城活动数据存档
function event_villageattack_save_to_db()
{
    if(!mysql_frida_ready) return false;
    return api_MySQL_exec(mysql_frida, "replace into game_event (event_id, event_info) values ('villageattack', '" + JSON.stringify(villageAttackEventInfo) + "');");
}

//世界广播怪物攻城活动当前进度/难度
function event_villageattack_broadcast_diffcult()
{
    if(villageAttackEventInfo.state != VILLAGEATTACK_STATE_END)
    {
        api_GameWorld_SendNotiPacketMessage('<怪物攻城活动> 当前阶段:' + (villageAttackEventInfo.state + 1) + ', 当前难度等级: ' + villageAttackEventInfo.difficult, 14);
    }
}

//计算活动剩余时间
function event_villageattack_get_remain_time()
{
    var cur_time = api_CSystemTime_getCurSec();
    var event_end_time = villageAttackEventInfo.start_time + EVENT_VILLAGEATTACK_TOTAL_TIME;
    var remain_time = event_end_time - cur_time;
    return remain_time;
}

//怪物攻城活动计时器(每5秒触发一次)
function event_villageattack_timer()
{
    if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_END)
        return;

    //活动结束检测
    var remain_time = event_villageattack_get_remain_time();
    console.log("have time:"+remain_time)
    if(remain_time <= 0)
    {
        //活动结束
        on_end_event_villageattack();
        return;
    }

    //当前应扣除的PT
    var damage = 0;

    //P2/P3阶段GBL主教扣PT
    if((villageAttackEventInfo.state == VILLAGEATTACK_STATE_P2) || (villageAttackEventInfo.state == VILLAGEATTACK_STATE_P3))
    {
        for(var i=0; i<villageAttackEventInfo.gbl_cnt; ++i)
        {
            if(get_random_int(0, 100) < (4 + villageAttackEventInfo.difficult))
            {
                damage += 1;
            }
        }
    }

    //P3阶段世界BOSS自身回血
    if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_P3)
    {
        if(get_random_int(0, 100) < (6 + villageAttackEventInfo.difficult))
        {
            damage += 1;
        }
    }

    //扣除PT
    if(damage > 0)
    {
        villageAttackEventInfo.score -= damage;
        if(villageAttackEventInfo.score < EVENT_VILLAGEATTACK_TARGET_SCORE[villageAttackEventInfo.state-1])
        {
            villageAttackEventInfo.score = EVENT_VILLAGEATTACK_TARGET_SCORE[villageAttackEventInfo.state-1]
        }

        //更新PT
        gameworld_update_villageattack_score();
    }

    //重复触发计时器
    if(villageAttackEventInfo.state != VILLAGEATTACK_STATE_END)
    {
        api_scheduleOnMainThread_delay(event_villageattack_timer, null, 5000);
    }
}

//设置怪物攻城副本难度(0-4: 普通-英雄)
function set_villageattack_dungeon_difficult(difficult)
{
    Memory.protect(ptr(0x085B9605), 4, 'rwx');      //修改内存保护属性为可写
    ptr(0x085B9605).writeInt(difficult);
}

//怪物攻城活动相关patch
function hook_VillageAttack()
{
    //hook挑战攻城怪物副本结束事件, 更新怪物攻城活动各阶段状态
    //village_attacked::CVillageMonster::SendVillageMonsterFightResult
    Interceptor.attach(ptr(0x086B330A), {
        onEnter: function (args) {

            this.village_monster = args[0];             //当前挑战的攻城怪物
            this.user = args[1];                        //当前挑战的角色
            this.result = args[2].toInt32();            //挑战结果: 1==成功
        },
        onLeave: function (retval) {
            //玩家杀死了攻城怪物
            if(this.result == 1)
            {
                if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_END)             //攻城活动已结束
                    return;

                //当前杀死的攻城怪物id
                var village_monster_id = this.village_monster.add(2).readUShort();

                //当前阶段杀死每只攻城怪物PT点数奖励: (1, 2, 4, 8, 16)
                var bonus_pt = 2 ** villageAttackEventInfo.difficult;

                //玩家所在队伍
                var party = CUser_GetParty(this.user);
                if(party.isNull())
                    return;

                //更新队伍中的所有玩家PT点数
                for(var i=0; i<4; ++i)
                {
                    var user = CParty_get_user(party, i);
                    if(!user.isNull())
                    {
                        //角色当前PT点数(游戏中的原始PT数据记录在village_attack_dungeon表中)
                        var charac_no = CUserCharacInfo_getCurCharacNo(user).toString();
                        if(!(charac_no in villageAttackEventInfo.user_pt_info))
                            villageAttackEventInfo.user_pt_info[charac_no] = [CUser_get_acc_id(user), 0];   //记录角色accid, 方便离线充值

                        //更新角色当前PT点数
                        villageAttackEventInfo.user_pt_info[charac_no][1] += bonus_pt;

                        //击杀世界BOSS, 额外获得PT奖励
                        if((village_monster_id == TAU_META_COW_MONSTER_ID) && (villageAttackEventInfo.state == VILLAGEATTACK_STATE_P3))
                        {
                            villageAttackEventInfo.user_pt_info[charac_no][1] += 1000*(1+villageAttackEventInfo.difficult);
                        }
                    }
                }


                if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_P1)              //怪物攻城一阶段
                {
                    //更新频道内总PT
                    villageAttackEventInfo.score += bonus_pt;

                    //P1阶段未完成
                    if(villageAttackEventInfo.score < EVENT_VILLAGEATTACK_TARGET_SCORE[0])
                    {
                        //若杀死了牛头统帅, 则攻城难度+1
                        if(village_monster_id == TAU_CAPTAIN_MONSTER_ID)
                        {
                            if(villageAttackEventInfo.difficult < 4)
                            {
                                villageAttackEventInfo.difficult += 1;

                                //怪物攻城副本难度
                                set_villageattack_dungeon_difficult(villageAttackEventInfo.difficult);


                                //下次刷新出的攻城怪物为: 牛头统帅
                                villageAttackEventInfo.next_village_monster_id = TAU_CAPTAIN_MONSTER_ID;

                                //公告通知客户端活动进度
                                event_villageattack_broadcast_diffcult();
                            }
                        }
                    }
                    else
                    {
                        //P1阶段已结束, 进入P2
                        villageAttackEventInfo.state = VILLAGEATTACK_STATE_P2;
                        villageAttackEventInfo.score = EVENT_VILLAGEATTACK_TARGET_SCORE[0];
                        villageAttackEventInfo.p2_last_killed_monster_time = 0;
                        villageAttackEventInfo.last_killed_monster_id = 0;
                        villageAttackEventInfo.p2_kill_combo = 0;

                        //公告通知客户端活动进度
                        event_villageattack_broadcast_diffcult();
                    }
                }
                else if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_P2)         //怪物攻城二阶段
                {
                    //计算连杀时间
                    var cur_time = api_CSystemTime_getCurSec();
                    var diff_time = cur_time - villageAttackEventInfo.p2_last_killed_monster_time;

                    //1分钟内连续击杀相同攻城怪物
                    if((diff_time < 60) && (village_monster_id == villageAttackEventInfo.last_killed_monster_id))
                    {
                        //连杀点数+1
                        villageAttackEventInfo.p2_kill_combo += 1;

                        if(villageAttackEventInfo.p2_kill_combo >= 3)
                        {
                            //三连杀增加当前阶段总PT
                            villageAttackEventInfo.score += 33;

                            //重新计算连杀
                            villageAttackEventInfo.last_killed_monster_id = 0;
                            villageAttackEventInfo.p2_kill_combo = 0;
                        }
                    }
                    else
                    {
                        //重新计算连杀
                        villageAttackEventInfo.last_killed_monster_id = village_monster_id;
                        villageAttackEventInfo.p2_kill_combo = 1;
                    }

                    //保存本次击杀时间
                    villageAttackEventInfo.p2_last_killed_monster_time = cur_time;

                    //P2阶段已结束, 进入P3
                    if(villageAttackEventInfo.score >= EVENT_VILLAGEATTACK_TARGET_SCORE[1])
                    {
                        //P2阶段已结束, 进入P3
                        villageAttackEventInfo.state = VILLAGEATTACK_STATE_P3;
                        villageAttackEventInfo.score = EVENT_VILLAGEATTACK_TARGET_SCORE[1];
                        villageAttackEventInfo.next_village_monster_id = TAU_META_COW_MONSTER_ID;

                        //公告通知客户端活动进度
                        event_villageattack_broadcast_diffcult();
                    }
                }
                else if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_P3)         //怪物攻城三阶段
                {
                    //击杀世界boss
                    if(village_monster_id == TAU_META_COW_MONSTER_ID)
                    {
                        //更新世界BOSS血量(PT)
                        villageAttackEventInfo.score += 25;
                        //继续刷新世界BOSS
                        villageAttackEventInfo.next_village_monster_id = TAU_META_COW_MONSTER_ID;

                        //世界广播
                        api_GameWorld_SendNotiPacketMessage('<怪物攻城活动> 世界BOSS已被【' + api_CUserCharacInfo_getCurCharacName(this.user) + '】击杀!', 14);

                        //P3阶段已结束
                        if(villageAttackEventInfo.score >= EVENT_VILLAGEATTACK_TARGET_SCORE[2])
                        {
                            //怪物攻城活动防守成功, 立即结束活动
                            villageAttackEventInfo.defend_success = 1;
                            api_scheduleOnMainThread(on_end_event_villageattack, null);
                            return;
                        }
                    }
                }

                //世界广播当前活动进度
                gameworld_update_villageattack_score();

                //通知队伍中的所有玩家更新PT点数
                for(var i=0; i<4; ++i)
                {
                    var user = CParty_get_user(party, i);
                    if(!user.isNull())
                    {
                        notify_villageattack_score(user);
                    }
                }

                //更新存活GBL主教数量
                if(village_monster_id == GBL_POPE_MONSTER_ID)
                {
                    if(villageAttackEventInfo.gbl_cnt > 0)
                    {
                        villageAttackEventInfo.gbl_cnt -= 1;
                    }
                }
            }
        }
    });

    //hook 刷新攻城怪物函数, 控制下一只刷新的攻城怪物id
    //village_attacked::CVillageMonsterArea::GetAttackedMonster
    Interceptor.attach(ptr(0x086B3AEA), {

        onEnter: function (args) {
        },
        onLeave: function (retval) {
            //返回值为下一次刷新的攻城怪物
            if(retval != 0)
            {
                //下一只刷新的攻城怪物
                var next_village_monster = ptr(retval);
                var next_village_monster_id = next_village_monster.readUShort();

                //当前刷新的怪物为机制怪物
                if((next_village_monster_id == TAU_META_COW_MONSTER_ID) || (next_village_monster_id == TAU_CAPTAIN_MONSTER_ID))
                {
                    //替换为随机怪物
                    next_village_monster.writeUShort(get_random_int(1, 17));
                }

                //如果需要刷新指定怪物
                if(villageAttackEventInfo.next_village_monster_id)
                {
                    if((villageAttackEventInfo.state == VILLAGEATTACK_STATE_P1) || (villageAttackEventInfo.state == VILLAGEATTACK_STATE_P2))
                    {
                        //P1 P2阶段立即刷新怪物
                        next_village_monster.writeUShort(villageAttackEventInfo.next_village_monster_id);
                        villageAttackEventInfo.next_village_monster_id = 0;
                    }
                    else if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_P3)
                    {
                        //P3阶段 几率刷新出世界BOSS
                        if(get_random_int(0, 100) < 44)
                        {
                            next_village_monster.writeUShort(villageAttackEventInfo.next_village_monster_id);
                            villageAttackEventInfo.next_village_monster_id = 0;

                            //世界广播
                            api_GameWorld_SendNotiPacketMessage('<怪物攻城活动> 世界BOSS已刷新, 请勇士们前往挑战!', 14);
                        }
                    }
                }

                //统计存活GBL主教数量
                if(next_village_monster.readUShort() == GBL_POPE_MONSTER_ID)
                {
                    villageAttackEventInfo.gbl_cnt += 1;
                }
            }
        }
    });

    //当前正在处理挑战的攻城怪物请求
    var state_on_fighting = false;
    //当前正在被挑战的怪物id
    var on_fighting_village_monster_id = 0;

    //hook 挑战攻城怪物函数 控制副本刷怪流程
    //CParty::OnFightVillageMonster
    Interceptor.attach(ptr(0x085B9596), {

        onEnter: function (args) {
            state_on_fighting = true;
            on_fighting_village_monster_id = 0;
        },
        onLeave: function (retval) {
            on_fighting_village_monster_id = 0;
            state_on_fighting = false;
        }
    });

    //village_attacked::CVillageMonster::OnFightVillageMonster
    Interceptor.attach(ptr(0x086B3240), {

        onEnter: function (args) {

            if(state_on_fighting)
            {
                var village_monster = args[0];

                //记录当前正在挑战的攻城怪物id
                on_fighting_village_monster_id = village_monster.add(2).readU16();
            }
        },
        onLeave: function (retval) {
        }
    });


    //hook 副本刷怪函数 控制副本内怪物的数量和属性
    //MapInfo::Add_Mob
    var read_f = new NativeFunction(ptr(0x08151612), 'int', ['pointer', 'pointer'], {"abi":"sysv"});
    Interceptor.replace(ptr(0x08151612), new NativeCallback(function (map_info, monster) {

        //当前刷怪的副本id
        var map_id = map_info.add(4).readUInt();

        //怪物攻城副本
        if((map_id >= 40001) && (map_id <= 40095))

        if(state_on_fighting)
        {
            //怪物攻城活动未结束
            if(villageAttackEventInfo != VILLAGEATTACK_STATE_END)
            {
                //正在挑战世界BOSS
                if(on_fighting_village_monster_id == TAU_META_COW_MONSTER_ID)
                {
                    //P3阶段
                    if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_P3)
                    {
                        //副本中有几率刷新出世界BOSS, 当前PT点数越高, 活动难度越大, 刷新出世界BOSS概率越大
                        if(get_random_int(0, 100) < ((villageAttackEventInfo.score-EVENT_VILLAGEATTACK_TARGET_SCORE[1])+(6*villageAttackEventInfo.difficult)))
                        {
                            monster.add(0xc).writeUInt(TAU_META_COW_MONSTER_ID);
                        }
                    }
                }

                if(villageAttackEventInfo.difficult == 0)
                {
                    //难度0: 无变化
                    return read_f(map_info, monster);
                }
                else if(villageAttackEventInfo.difficult == 1)
                {
                    //难度1: 怪物等级提升至100级
                    monster.add(16).writeU8(100);
                    return read_f(map_info, monster);
                }
                else if(villageAttackEventInfo.difficult == 2)
                {
                    //难度2: 怪物等级提升至110级; 随机刷新紫名怪
                    monster.add(16).writeU8(110);
                    //非BOSS怪
                    if(monster.add(8).readU8() != 3)
                    {
                        if(get_random_int(0, 100) < 50)
                        {
                            monster.add(8).writeU8(1);     //怪物类型: 0-3
                        }
                    }
                    return read_f(map_info, monster);
                }
                else if(villageAttackEventInfo.difficult == 3)
                {
                    //难度3: 怪物等级提升至120级; 随机刷新不灭粉名怪; 怪物数量*2
                    monster.add(16).writeU8(120);
                    //非BOSS怪
                    if(monster.add(8).readU8() != 3)
                    {
                        if(get_random_int(0, 100) < 75)
                        {
                            monster.add(8).writeU8(2);     //怪物类型: 0-3
                        }
                    }

                    //执行原始刷怪流程
                    read_f(map_info, monster);

                    //刷新额外的怪物(同一张地图内, 怪物index和怪物uid必须唯一, 这里为怪物分配新的index和uid)

                    //额外刷新怪物数量
                    var cnt = 1;
                    //新的怪物uid偏移
                    var uid_offset = 1000;
                    //返回值
                    var ret = 0;

                    while(cnt > 0)
                    {
                        --cnt;

                        //新增怪物index
                        monster.writeUInt(monster.readUInt()+uid_offset);
                        //新增怪物uid
                        monster.add(4).writeUInt(monster.add(4).readUInt()+uid_offset);

                        //为当前地图刷新额外的怪物
                        ret = read_f(map_info, monster);
                    }

                    return ret;
                }
                else if(villageAttackEventInfo.difficult == 4)
                {
                    //难度4: 怪物等级提升至127级; 随机刷新橙名怪; 怪物数量*4
                    monster.add(16).writeU8(127);
                    //非BOSS怪
                    if(monster.add(8).readU8() != 3)
                    {
                        //英雄级副本精英怪类型等于2的怪为橙名怪
                        monster.add(8).writeU8(get_random_int(1, 3));     //怪物类型: 0-3
                    }

                    //执行原始刷怪流程
                    read_f(map_info, monster);

                    //刷新额外的怪物(同一张地图内, 怪物index和怪物uid必须唯一, 这里为怪物分配新的index和uid)

                    //额外刷新怪物数量
                    var cnt = 3;
                    //新的怪物uid偏移
                    var uid_offset = 1000;
                    //返回值
                    var ret = 0;

                    while(cnt > 0)
                    {
                        --cnt;

                        //新增怪物index
                        monster.writeUInt(monster.readUInt()+uid_offset);
                        //新增怪物uid
                        monster.add(4).writeUInt(monster.add(4).readUInt()+uid_offset);

                        //为当前地图刷新额外的怪物
                        ret = read_f(map_info, monster);
                    }

                    return ret;
                }
            }
        }

        //执行原始刷怪流程
        return read_f(map_info, monster);
    }, 'int', ['pointer', 'pointer']));


    //每次通关额外获取当前等级升级所需经验的0%-10%
    //village_attacked::CVillageMonsterMgr::OnKillVillageMonster
    Interceptor.attach(ptr(0x086B4866), {

        onEnter: function (args) {

            this.user = args[1];
            this.result = args[2].toInt32();
        },
        onLeave: function (retval) {
            if(retval == 0)
            {
                //挑战成功
                if(this.result)
                {
                    //玩家所在队伍
                    var party = CUser_GetParty(this.user);
                    //怪物攻城挑战成功, 给队伍中所有成员发送额外通关发经验
                    for(var i=0; i<4; ++i)
                    {
                        var user = CParty_get_user(party, i);
                        if(!user.isNull())
                        {
                            //随机经验奖励
                            var cur_level = CUserCharacInfo_get_charac_level(user);
                            var reward_exp = Math.floor(CUserCharacInfo_get_level_up_exp(user, cur_level) * get_random_int(0, 100) / 1000);

                            //发经验
                            api_CUser_gain_exp_sp(user, reward_exp);

                            //通知玩家获取额外奖励
                            api_CUser_SendNotiPacketMessage(user, '怪物攻城挑战成功, 获取额外经验奖励' + reward_exp, 0);
                        }
                    }
                }
            }
        }
    });
}

//开启怪物攻城活动
function start_villageattack()
{
    var a3 = Memory.alloc(100);
    a3.add(10).writeInt(EVENT_VILLAGEATTACK_TOTAL_TIME);                       //活动剩余时间
    a3.add(14).writeInt(villageAttackEventInfo.score);                         //当前频道PT点数
    a3.add(18).writeInt(EVENT_VILLAGEATTACK_TARGET_SCORE[2]);                  //成功防守所需点数

    Inter_VillageAttackedStart_dispatch_sig(ptr(0), ptr(0), a3);
}

//通知玩家怪物攻城进度
function notify_villageattack_score(user)
{
    //玩家当前PT点
    var charac_no = CUserCharacInfo_getCurCharacNo(user).toString();
    var villageattack_pt = 0;
    if(charac_no in villageAttackEventInfo.user_pt_info)
        villageattack_pt = villageAttackEventInfo.user_pt_info[charac_no][1];

    //计算活动剩余时间
    var remain_time = event_villageattack_get_remain_time();
    if((remain_time <= 0) || (villageAttackEventInfo.state == VILLAGEATTACK_STATE_END))
        return;

    //发包通知角色打开怪物攻城UI并更新当前进度
    var packet_guard = api_PacketGuard_PacketGuard();
    InterfacePacketBuf_put_header(packet_guard, 0, 248);                                                                        //协议: ENUM_NOTIPACKET_STARTED_VILLAGE_ATTACKED
    InterfacePacketBuf_put_int(packet_guard, remain_time);                                                                      //活动剩余时间
    InterfacePacketBuf_put_int(packet_guard, villageAttackEventInfo.score);                                                     //当前频道PT点数
    InterfacePacketBuf_put_int(packet_guard, EVENT_VILLAGEATTACK_TARGET_SCORE[2]);                                              //成功防守所需点数
    InterfacePacketBuf_put_int(packet_guard, villageattack_pt);                                                                 //个人PT点数
    InterfacePacketBuf_finalize(packet_guard, 1);
    CUser_Send(user, packet_guard);
    Destroy_PacketGuard_PacketGuard(packet_guard);
}


//更新怪物攻城当前进度(广播给频道内在线玩家)
function gameworld_update_villageattack_score()
{
    //计算活动剩余时间
    var remain_time = event_villageattack_get_remain_time();
    if((remain_time <= 0) || (villageAttackEventInfo.state == VILLAGEATTACK_STATE_END))
        return;

    var packet_guard = api_PacketGuard_PacketGuard();
    InterfacePacketBuf_put_header(packet_guard, 0, 247);                                                                        //协议: ENUM_NOTIPACKET_UPDATE_VILLAGE_ATTACKED
    InterfacePacketBuf_put_int(packet_guard, remain_time);                                                                      //活动剩余时间
    InterfacePacketBuf_put_int(packet_guard, villageAttackEventInfo.score);                                                     //当前频道PT点数
    InterfacePacketBuf_put_int(packet_guard, EVENT_VILLAGEATTACK_TARGET_SCORE[2]);                                              //成功防守所需点数
    InterfacePacketBuf_finalize(packet_guard, 1);
    GameWorld_send_all(G_GameWorld(), packet_guard);
    Destroy_PacketGuard_PacketGuard(packet_guard);
}

//结束怪物攻城活动(立即销毁攻城怪物, 不开启逆袭之谷, 不发送活动奖励)
function end_villageattack()
{
    village_attacked_CVillageMonsterMgr_OnDestroyVillageMonster(GlobalData_s_villageMonsterMgr.readPointer(), 2);
}

//重置活动数据
function reset_villageattack_info()
{
    villageAttackEventInfo.state = VILLAGEATTACK_STATE_P1;
    villageAttackEventInfo.score = 0;
    villageAttackEventInfo.difficult = 0;
    villageAttackEventInfo.next_village_monster_id = TAU_CAPTAIN_MONSTER_ID;
    villageAttackEventInfo.last_killed_monster_id = 0;
    villageAttackEventInfo.p2_kill_combo = 0;

    villageAttackEventInfo.user_pt_info = {};

    set_villageattack_dungeon_difficult(villageAttackEventInfo.difficult);

    villageAttackEventInfo.start_time = api_CSystemTime_getCurSec();
    console.log("start_time:"+villageAttackEventInfo.start_time)
}

//开始怪物攻城活动
function on_start_event_villageattack()
{
    //重置活动数据
    reset_villageattack_info();

    //通知全服玩家活动开始 并刷新城镇怪物
    start_villageattack();

    //开启活动计时器
    api_scheduleOnMainThread_delay(event_villageattack_timer, null, 5000);

    //公告通知当前活动进度
    event_villageattack_broadcast_diffcult();
}



//结束怪物攻城活动
function on_end_event_villageattack()
{
    if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_END)
        return;

    //设置活动状态
    villageAttackEventInfo.state = VILLAGEATTACK_STATE_END;

    //立即结束怪物攻城活动
    end_villageattack();

    //防守成功
    if(villageAttackEventInfo.defend_success)
    {
        //频道内在线玩家发奖

        //发信奖励: 金币+道具
        var reward_gold = 1000000*(1+villageAttackEventInfo.difficult);     //金币
        var reward_item_list = [
            [7745, 5*(1+villageAttackEventInfo.difficult)],                 //士气冲天
            [2600021, 10*(1+villageAttackEventInfo.difficult)],              //精神刺激灵药
            [7454, 10*(1+villageAttackEventInfo.difficult)],                   //黑钻硬币
            [202112062, 5+villageAttackEventInfo.difficult],                     //时空结晶体
            [3290, 10+villageAttackEventInfo.difficult],                     //特级恶魔结晶
        ];
        api_gameworld_send_mail('<怪物攻城活动>', '恭喜勇士!', reward_gold, reward_item_list);

        //特殊奖励
        api_gameworld_foreach(function (user, args){
            //设置绝望之塔当前层数为100层
            api_TOD_UserState_setEnterLayer(user, 99);

            //随机选择一件穿戴中的装备
            var inven = CUserCharacInfo_getCurCharacInvenW(user);
            var slot = get_random_int(10, 21);          //12件装备slot范围10-21
            var equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_BODY, slot);
            if(Inven_Item_getKey(equ))
            {
                //读取装备强化等级
                var upgrade_level = equ.add(6).readU8();
                if(upgrade_level < 31)
                {
                    //提升装备的强化/增幅等级
                    var bonus_level = get_random_int(1, 1 + villageAttackEventInfo.difficult);
                    upgrade_level += bonus_level;

                    if(upgrade_level >= 31)
                        upgrade_level = 31;

                    //提升强化/增幅等级
                    equ.add(1).writeU8(upgrade_level);

                    //通知客户端更新装备
                    CUser_SendUpdateItemList(user, 1, 3, slot);
                }
            }
        }, null);


        //榜一大哥
        var rank_first_charac_no = 0;
        var rank_first_account_id = 0;
        var max_pt = 0;

        //论功行赏
        for(var charac_no in villageAttackEventInfo.user_pt_info)
        {
            //发点券
            var account_id = villageAttackEventInfo.user_pt_info[charac_no][0];
            var pt = villageAttackEventInfo.user_pt_info[charac_no][1];
            var reward_cera = pt*100;                                                   //点券奖励 = 个人PT * 100
            api_recharge_cash_cera_offline(account_id, 'GM', reward_cera);

            //找出榜一大哥
            if(pt > max_pt)
            {
                rank_first_charac_no = charac_no;
                rank_first_account_id = account_id;
                max_pt = pt;
            }
        }


        //频道内公告活动已结束
        api_GameWorld_SendNotiPacketMessage('<怪物攻城活动> 防守成功, 奖励已发送!', 14);

        if(rank_first_charac_no)
        {
            //个人积分排行榜第一名 额外获得10倍点券奖励
            api_recharge_cash_cera_offline(rank_first_account_id, 'GM', max_pt*100);

            //频道内广播本轮活动排行榜第一名玩家名字
            var rank_first_charac_name = api_get_charac_name_by_charac_no(rank_first_charac_no);
            api_GameWorld_SendNotiPacketMessage('<怪物攻城活动> 恭喜勇士 【' + rank_first_charac_name + '】 成为个人积分排行榜第一名(' + max_pt + 'pt)!', 14);
        }
    }
    else
    {
        //防守失败
        api_gameworld_foreach(function (user, args){

            //获取角色背包
            var inven = CUserCharacInfo_getCurCharacInvenW(user);

            //在线玩家被攻城怪物随机掠夺一件穿戴中的装备
            if(get_random_int(0, 100) < 7)
            {
                //随机删除一件穿戴中的装备
                var slot = get_random_int(10, 21);          //12件装备slot范围10-21
                var equ = CInventory_GetInvenRef(inven, INVENTORY_TYPE_BODY, slot);

                if(Inven_Item_getKey(equ))
                {
                    Inven_Item_reset(equ);

                    //通知客户端更新装备
                    CUser_SendNotiPacket(user, 1, 2, 3);
                }
            }

            //在线玩家被攻城怪物随机掠夺1%-10%所持金币
            var rate = get_random_int(1, 11);
            var cur_gold = CInventory_get_money(inven);
            var tax = Math.floor((rate / 100) * cur_gold);
            CInventory_use_money(inven, tax, 0, 0);

            //通知客户端更新金币数量
            CUser_SendUpdateItemList(user, 1, 0, 0);
        }, null);


        //频道内公告活动已结束
        api_GameWorld_SendNotiPacketMessage('<怪物攻城活动> 防守失败, 请勇士们再接再厉!', 14);
    }

    //释放空间
    villageAttackEventInfo.user_pt_info = {};

    //存档
    event_villageattack_save_to_db();

    //开启怪物攻城活动定时器
    start_event_villageattack_timer();
}

//开启怪物攻城活动定时器
function start_event_villageattack_timer()
{
    //获取当前系统时间
    var cur_time = api_CSystemTime_getCurSec();

    //计算距离下次开启怪物攻城活动的时间
    var delay_time = (3600*EVENT_VILLAGEATTACK_START_HOUR) - (cur_time % (3600*24));
    if(delay_time <= 0)
        delay_time += 3600*24;

    //log('距离下次开启<怪物攻城活动>还有:' + delay_time/3600 + '小时');
    //on_start_event_villageattack()
    //定时开启活动
    api_scheduleOnMainThread_delay(on_start_event_villageattack, null, delay_time*1000);
}

//开启怪物攻城活动
function start_event_villageattack()
{
    //patch相关函数, 修复活动流程
    hook_VillageAttack();

    if(villageAttackEventInfo.state == VILLAGEATTACK_STATE_END)
    {
        //开启怪物攻城活动定时器
        start_event_villageattack_timer();
    }
}
// 获取账号金库一个空的格子
var CAccountCargo_GetEmptySlot_NEW;
// 将已经物品移动到某个格子 第一个账号金库，第二个移入的物品，第三个格子位置
var CAccountCargo_InsertItem_NEW;
// 向客户端发送账号金库列表
var CAccountCargo_SendItemList_NEW;
// 存放所有用户的账号金库数据
var accountCargfo = {};
var initMaxSolt = 0;
function setMaxCAccountCargoSolt(maxSolt){
    // console.log(1);
    initMaxSolt = maxSolt;
    GetMoney(maxSolt);
    CAccountCargo(maxSolt);
    GetCapacity(maxSolt);
    SetDBData(maxSolt);
    Clear(maxSolt);
    InsertItem(maxSolt);
    DeleteItem(maxSolt);
    MoveItem(maxSolt);
    DepositMoney(maxSolt);
    WithdrawMoney(maxSolt);
    CheckMoneyLimit(maxSolt);
    CheckValidSlot(maxSolt);
    GetEmptySlot(maxSolt);
    GetSpecificItemSlot(maxSolt);
    AddMoney(maxSolt);
    SubMoney(maxSolt);
    GetItemCount(maxSolt);
    SendNotifyMoney(maxSolt);
    SendItemList(maxSolt);
    IsAlter(maxSolt);
    SetCapacity(maxSolt);
    SetStable(maxSolt);
    DB_SaveAccountCargo_makeRequest(maxSolt);
    GetAccountCargo();
    MakeItemPacket(maxSolt);
    CheckStackLimit(maxSolt);
    CheckSlotEmpty(maxSolt);
    // CheckInsertCondition(maxSolt);
    GetSlotRef(maxSolt);
    GetSlot(maxSolt);
    ResetSlot(maxSolt);
    DB_LoadAccountCargo_dispatch(maxSolt);
    DB_SaveAccountCargo_dispatch(maxSolt);
    IsExistAccountCargo();
    // userLogout();
    console.log(12);
}

function IsExistAccountCargo(){
    Interceptor.attach(ptr(0x0822fc30), {

        onEnter: function (args) {
            // console.log('IsExistAccountCargo start:'+args[0])
        },
        onLeave: function (retval) {
            // console.log('IsExistAccountCargo end:'+retval)
        }
    });
}

function DB_SaveAccountCargo_dispatch(maxSolt){
    Interceptor.replace(ptr(0x0843b7c2), new NativeCallback(function (dbcargoRef,a2,a3,a4) {
        // console.log("DB_SaveAccountCargo_dispatch -------------:")
        var v14 = Memory.alloc(4);
        v14.writeU32(0);
        Stream_operator_p(a4, v14.toInt32());
        var v4 = NumberToString(v14.readU32(),0);
        // console.log("mid:"+ptr(v4).readUtf8String(-1));

        var out = Stream_GetOutBuffer_SIG_ACCOUNT_CARGO_DATA(a4);
        var outPtr = ptr(out);
        var v17Addr = Memory.alloc(4);
        v17Addr.writeInt(61*maxSolt);
        var readBuff = Memory.alloc(61*maxSolt);
        if(compress_zip(readBuff,v17Addr,outPtr.add(8),61*maxSolt) !=1){
            return 0;
        }
        var dbHandelAddr = DBMgr_GetDBHandle(ptr(ptr(0x0940BDAC).readU32()),2,0);
        var dbHandel = ptr(dbHandelAddr);
        var blobPtr = MySQL_blob_to_str(dbHandel,0,readBuff,v17Addr.readU32());
        // console.log('blob: '+blobPtr +' '+outPtr.readU32()+' '+outPtr.add(4).readU32()+'  ');
        MySQL_set_query_6(dbHandel,Memory.allocUtf8String("upDate account_cargo set capacity=%u, money=%u, cargo='%s' where m_id = %s")
            ,outPtr.readU32(),outPtr.add(4).readU32(),blobPtr.toInt32(),ptr(v4).toInt32());
        return MySQL_exec(dbHandel,1)==1?1:0;
    }, 'int', ['pointer','int','int','pointer']));
}

function DB_LoadAccountCargo_dispatch(maxSolt){
    Interceptor.replace(ptr(0x0843b3b6), new NativeCallback(function (dbcargoRef,a2,a3,a4) {
        console.log('DB_LoadAccountCargo_dispatch:::'+dbcargoRef+','+a2+','+a3+','+a4);

        var v19 = Memory.alloc(4);
        v19.writeU32(0);
        Stream_operator_p(a4, v19.toInt32());
        var v4 = NumberToString(v19.readU32(),0);
        // console.log("mid:"+ptr(v4).readUtf8String(-1))

        var dbHandelAddr = DBMgr_GetDBHandle(ptr(ptr(0x0940BDAC).readU32()),2,0);
        var dbHandel = ptr(dbHandelAddr);
        // console.log('dbHandel:'+dbHandel);

        MySQL_set_query_3_ptr(dbHandel,Memory.allocUtf8String('seLect capacity, money, cargo from account_cargo where m_id = %s'),ptr(v4));
        if(MySQL_exec(dbHandel,1)!=1){
            // console.log("exec fail :")
            return 0;
        }
        if(MySQL_get_n_rows(dbHandel)==0){
            // console.log("get rows  = 0 ")
            return 1;
        }
        if(MySQL_fetch(dbHandel)!=1){
            // console.log("fetch fial  = 0 ")
            return 0;
        }
        var v18 = Memory.alloc(8);
        var v6 = StreamPool_Acquire(ptr(ptr(0x0940BD6C).readU32()),Memory.allocUtf8String('DBThread.cpp'),35923);
        CStreamGuard_CStreamGuard(v18,v6,1);
        var v7 = CStreamGuard_operator(v18.toInt32());
        CStreamGuard_operator_int(ptr(v7),a2);
        var v8 = CStreamGuard_operator(v18.toInt32());
        CStreamGuard_operator_int(ptr(v8),a3);
        var v9= CStreamGuard_operator_p(v18.toInt32());
        var v21 = CStreamGuard_GetInBuffer_SIG_ACCOUNT_CARGO_DATA(ptr(v9));
        v21.writeU32(0);
        v21.add(4).writeU32(0);
        var cargoRefAdd = v21.add(8);
        for (var i = 0; i < maxSolt; i++) {
            cargoRefAdd.writeU32(0);
            cargoRefAdd = cargoRefAdd.add(61);
        }
        v21.add(8+61*maxSolt).writeU32(0);
        v21.add(8+61*maxSolt).writeU32(0);
        var res =0;
        if(MySQL_get_uint(dbHandel,0,v21)!=1){
            // console.log('uint capacity get error')
            res = 0;
        }else if(MySQL_get_uint(dbHandel,1,v21.add(4))!=1){
            // console.log('uint money get error')
            res = 0;
        }else{
            var v10 = Memory.alloc(61*maxSolt*4);
            for (var i = 0; i < 61*maxSolt; i++) {
                v10.add(i*4).writeU32(0);
            }
            var binaryLength = MySQL_get_binary_length(dbHandel,2);
            if( MySQL_get_binary(dbHandel,2,v10, binaryLength)!=1 ){
                // console.log('read val length 0');
                // 解决创建账号金库后什么也不操作 然后保存字节为0 导致创建的打不开
                for (var i = 0; i < maxSolt; i++) {
                    v21.add(8+i*61).writeU32(0);
                }
                var msgName = ptr(ptr(0x0940BD68).readU32());
                MsgQueueMgr_put(msgName.toInt32(),1,v18);
                res = 1;
            }else{
                binaryLength = MySQL_get_binary_length(dbHandel,2);
                var v17Addr = Memory.alloc(4);
                v17Addr.writeInt(61*maxSolt)
                if(uncompress_zip(v21.add(8), v17Addr, v10, binaryLength) !=1){
                    // console.log("uncompress_zip error  !!!")
                    res = 0;
                }else if(v17Addr.readU32()!=0 && v17Addr.readU32()%(61*maxSolt) !=0){
                    res = 0;
                }else{
                    var msgName = ptr(ptr(0x0940BD68).readU32());
                    MsgQueueMgr_put(msgName.toInt32(),1,v18);
                    res = 1;

                }
                // console.log("v17 length:"+v17Addr.readU32());
            }
        }
        // console.log('money or capacity:'+v21.readU32()+','+v21.add(4).readU32()+','+v21.add(8).readU32()+' ,'+res)
        Destroy_CStreamGuard_CStreamGuard(v18);
        return res;
    }, 'int', ['pointer','int','int','pointer']));
}

function ResetSlot(maxSolt){
    Interceptor.replace(ptr(0x082898C0), new NativeCallback(function (cargoRef,solt) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('ResetSlot------------------------------------'+cargoRef)
        if(CAccountCargo_CheckValidSlot(cargoRef,solt)==0){
            return 0;
        }
        cargoRef = getCargoRef(accId,cargoRef);
        return Inven_Item_reset(cargoRef.add(61*solt+4));
    }, 'int', ['pointer','int']));
}

function GetSlot(maxSolt){
    Interceptor.replace(ptr(0x082898F8), new NativeCallback(function (buff,cargo,solt) {
        var cargoRef = ptr(cargo);
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return buff;
        }
        // console.log('GetSlot------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        if(CAccountCargo_CheckValidSlot(cargoRef,solt)==0){
            buff.writeU32(cargoRef.add(61*solt+4).readU32());
            buff.add(1*4).writeU32(0);
            buff.add(2*4).writeU32(0);
            buff.add(3*4).writeU32(0);
            buff.add(4*4).writeU32(0);
            buff.add(5*4).writeU32(0);
            buff.add(6*4).writeU32(0);
            buff.add(7*4).writeU32(0);
            buff.add(8*4).writeU32(0);
            buff.add(9*4).writeU32(0);
            buff.add(10*4).writeU32(0);
            buff.add(11*4).writeU32(0);
            buff.add(12*4).writeU32(0);
            buff.add(13*4).writeU32(0);
            buff.add(14*4).writeU32(0);
            buff.add(60).writeU8(0);
        }else {
            buff.writeU32(cargoRef.add(61*solt+4).readU32());
            buff.add(1*4).writeU32(cargoRef.add(61*solt+8).readU32());
            buff.add(2*4).writeU32(cargoRef.add(61*solt+12).readU32());
            buff.add(3*4).writeU32(cargoRef.add(61*solt+16).readU32());
            buff.add(4*4).writeU32(cargoRef.add(61*solt+20).readU32());
            buff.add(5*4).writeU32(cargoRef.add(61*solt+24).readU32());
            buff.add(6*4).writeU32(cargoRef.add(61*solt+28).readU32());
            buff.add(7*4).writeU32(cargoRef.add(61*solt+32).readU32());
            buff.add(8*4).writeU32(cargoRef.add(61*solt+36).readU32());
            buff.add(9*4).writeU32(cargoRef.add(61*solt+40).readU32());
            buff.add(10*4).writeU32(cargoRef.add(61*solt+44).readU32());
            buff.add(11*4).writeU32(cargoRef.add(61*solt+48).readU32());
            buff.add(12*4).writeU32(cargoRef.add(61*solt+52).readU32());
            buff.add(13*4).writeU32(cargoRef.add(61*solt+56).readU32());
            buff.add(14*4).writeU32(cargoRef.add(61*solt+60).readU32());
            buff.add(60).writeU8(cargoRef.add(61*solt+64).readU8());
        }
        return buff;
    }, 'pointer', ['pointer','int','int']));
}

function GetSlotRef(maxSolt){
    Interceptor.replace(ptr(0x08289A0C), new NativeCallback(function (cargoRef,solt) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log("GetSlotRef ------------------------"+cargoRef)
        if(CAccountCargo_CheckValidSlot(cargoRef,solt)==0){
            return 0;
        }
        cargoRef.add(12+61*56).writeU8(1); // 标志
        cargoRef = getCargoRef(accId,cargoRef);
        cargoRef.add(12+61*maxSolt).writeU8(1); // 标志
        return cargoRef.add(61*solt+4);
    }, 'pointer', ['pointer','int']));
}

// todo 没有写替换
function CheckInsertCondition(maxSolt){
    Interceptor.replace(ptr(0x08289A4A), new NativeCallback(function (cargoRef,itemInven) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('CheckInsertCondition------------------------------------'+cargoRef)
        var itemId = itemInven.add(2).readU32();
        var item = CDataManager_find_item(G_CDataManager(),itemId);
        if(item==0){
            return 0;
        }
        if(CItem_isPackagable(item)!=1){
            return 0;
        }
        var lock = stAmplifyOption_t_GetLock(itemInven.add(17));
        if(lock!=0){
            var characExpandDataR = CUser_GetCharacExpandDataR(cargoRef.readU32(),2);
            if(item_lock_CItemLock_CheckItemLock(characExpandDataR,lock)!=0){
                return 0;
            }
        }
        var typeVal = itemInven.add(1).readU8();
        if(typeVal==4||typeVal==5||typeVal==6||typeVal==7||typeVal==8){
            return 0;
        }
        if(itemId>0x1963 && itemId<=0x1B57){
            return 0;
        }
        var attachType = CItem_GetAttachType(item);
        if(attachType==1 || attachType==2){
            return 0;
        }
        if(attachType == 3 && itemInven.readU8()!=1){
            return 0;
        }
        if(UpgradeSeparateInfo_IsTradeRestriction(itemInven.add(51)) !=0){
            return 0;
        }
        var tempMethod = new NativeFunction(ptr(item.add(16*4).readU32()), 'int', ['pointer'], {"abi":"sysv"});
        // ||tempMethod(item)==1
        var isGMUser = CUser_isGMUser(ptr(cargoRef.readU32()));
        if(isGMUser==1){
            return 1;
        }
        if(CItem_getUsablePeriod(item)==0 && CItem_getExpirationDate(item)==0){
            return 1;
        }
        if(CItem_getUsablePeriod(item)==0 && CItem_getExpirationDate(item)==0){
            return 0;
        }
        var expDate = 86400 * itemInven.add(11).readU16()+1151683200;
        return expDate > CSystemTime_getCurSec(ptr(0x0941F714))?1:0;
    }, 'int', ['pointer','pointer']));
}

function CheckSlotEmpty(maxSolt){
    Interceptor.replace(ptr(0x0828A5D4), new NativeCallback(function (cargoRef,solt) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('CheckSlotEmpty------------------------------------'+cargoRef)
        var buffCargoRef = getCargoRef(accId,cargoRef);
        // console.log("CheckSlotEmpty accId:"+accId)
        return (CAccountCargo_CheckValidSlot(cargoRef,solt)!=0 && buffCargoRef.add(61*solt+6).readU32()!=0)?1:0;
    }, 'int', ['pointer','int']));
}

function CheckStackLimit(maxSolt){
    Interceptor.replace(ptr(0x0828A670), new NativeCallback(function (cargoRef,solt,itemId,size) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('CheckStackLimit------------------------------------'+cargoRef)
        if(CAccountCargo_CheckValidSlot(cargoRef,solt)==0){
            return 0;
        }
        cargoRef = getCargoRef(accId,cargoRef);;
        if(cargoRef.add(61*solt+6).readU32() != itemId){
            return 0;
        }
        var item = CDataManager_find_item(G_CDataManager(),itemId);
        if(item==0){
            return 0;
        }
        if(CItem_is_stackable(item)!=1){
            return 0;
        }
        var allSize = size + cargoRef.add(61*solt+11).readU32();
        var limit = CStackableItem_getStackableLimit(item);
        return limit< allSize||allSize<0 ? 0:1;
    }, 'int', ['pointer','int','int','int']));
}

function MakeItemPacket(maxSolt){
    Interceptor.replace(ptr(0x0828AB1C), new NativeCallback(function (cargoRef,buff,solt) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('MakeItemPacket------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        // console.log("MakeItemPacket accId:"+accId)
        InterfacePacketBuf_put_short(buff, solt);
        if ( cargoRef.add(61*solt+6).readU32() !=0 ) {
            InterfacePacketBuf_put_int(buff, cargoRef.add(61*solt+6).readU32());
            InterfacePacketBuf_put_int(buff, cargoRef.add(61*solt+11).readU32());
            var integratedPvPItemAttr = GetIntegratedPvPItemAttr(cargoRef.add(61*solt+4));
            InterfacePacketBuf_put_byte(buff, integratedPvPItemAttr);
            InterfacePacketBuf_put_short(buff, cargoRef.add(61*solt+15).readU16());
            InterfacePacketBuf_put_byte(buff, cargoRef.add(61*solt+4).readU8());
            if ( GameWorld_IsEnchantRevisionChannel(G_GameWorld())!=0 ){
                InterfacePacketBuf_put_int(buff, 0);
            }else{
                InterfacePacketBuf_put_int(buff, cargoRef.add(61*solt+17).readU32());
            }
            var abilityType = stAmplifyOption_t_getAbilityType(cargoRef.add(61*solt+21));
            InterfacePacketBuf_put_byte(buff, abilityType);
            var abilityValue = stAmplifyOption_t_getAbilityValue(cargoRef.add(61*solt+21));
            InterfacePacketBuf_put_short(buff, abilityValue);
            InterfacePacketBuf_put_byte(buff, 0);
            return InterfacePacketBuf_put_packet(buff, cargoRef.add(61*solt+4));
        }else{
            InterfacePacketBuf_put_int(buff, -1);
            InterfacePacketBuf_put_int(buff, 0);
            InterfacePacketBuf_put_byte(buff, 0);
            InterfacePacketBuf_put_short(buff, 0);
            InterfacePacketBuf_put_byte(buff, 0);
            InterfacePacketBuf_put_int(buff, 0);
            InterfacePacketBuf_put_byte(buff, 0);
            InterfacePacketBuf_put_short(buff, 0);
            InterfacePacketBuf_put_byte(buff, 0);
            return InterfacePacketBuf_put_packet(buff, ptr(0x0943DDC0).readPointer());
        }
    }, 'int', ['pointer','pointer','int']));
}

function GetAccountCargo(){
    Interceptor.replace(ptr(0x0822fc22), new NativeCallback(function (cargoRef) {
        // var accId =  CUser_get_acc_id(cargoRef);
        // if(accId == -1){
        //     return 0;
        // }
        console.log('GetAccountCargo------------------------------------'+cargoRef)
        // if(accountCargfo[accId]){
        //     return  accountCargfo[accId];
        // }
        // 返回原来的地址
        return cargoRef.add(454652);
    }, 'pointer', ['pointer']));
}

function  DB_SaveAccountCargo_makeRequest(maxSolt){
    Interceptor.replace(ptr(0x0843B946), new NativeCallback(function (a1,a2,cargo) {
        console.log("DB_SaveAccountCargo_makeRequest---------"+ptr(cargo)+','+a1+',,,'+a2);
        var cargoRef = ptr(cargo);
        var accId =  getUserAccId(cargoRef);
        // console.log('makeRequest------accId-----'+accId);
        cargoRef = getCargoRef(accId,cargoRef);
        var v8 = Memory.alloc(61*maxSolt+9);
        var v3 = StreamPool_Acquire(ptr(ptr(0x0940BD6C).readU32()),Memory.allocUtf8String('DBThread.cpp'),35999);
        CStreamGuard_CStreamGuard(v8,v3,1);
        var v4 = CStreamGuard_operator(v8.toInt32());
        CStreamGuard_operator_int(ptr(v4),497);
        var v5 = CStreamGuard_operator(v8.toInt32());
        CStreamGuard_operator_int(ptr(v5),a1.toInt32());
        var v6 = CStreamGuard_operator(v8.toInt32());
        CStreamGuard_operator_int(ptr(v6),a2);
        var v7 = CStreamGuard_operator_p(v8.toInt32());
        var v9 = CStreamGuard_GetInBuffer_SIG_ACCOUNT_CARGO_DATA(ptr(v7));
        v9.writeU32(0);
        var cargoRefAdd = v9.add(4);
        for (var i = 0; i < maxSolt; i++) {
            cargoRefAdd.writeU32(0);
            cargoRefAdd = cargoRefAdd.add(61);
        }
        var money = cargoRef.add(4+61*maxSolt).readU32();
        var capacity = cargoRef.add(8+61*maxSolt).readU32();
        // console.log('money or capacity:'+money+','+capacity)
        v9.writeU32(capacity); // 钱
        v9.add(4).writeU32(money); // 容量
        Memory.copy(v9.add(8),cargoRef.add(4),maxSolt*61);
        MsgQueueMgr_put(ptr(ptr(0x0940BD68).readU32()).toInt32(),2,v8);
        CAccountCargo_SetStable(cargoRef);
        Destroy_CStreamGuard_CStreamGuard(v8);
        // console.log("makeRequest success")
    }, 'void', ['pointer','int','uint']));
}

function SetStable(maxSolt){
    Interceptor.replace(ptr(0x0844DC16), new NativeCallback(function (cargoRef) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log("SetStable ---------------------"+cargoRef)
        var buffCargoRef = getCargoRef(accId,cargoRef);;
        buffCargoRef.add(12+61*maxSolt).writeU8(0); // 标志
        return cargoRef;
    }, 'pointer', ['pointer']));
}

function SetCapacity(maxSolt){
    Interceptor.replace(ptr(0x084EBE46), new NativeCallback(function (cargoRef,capacity) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log("SetCapacity--------------------"+cargoRef)
        var buffCargoRef = getCargoRef(accId,cargoRef);
        buffCargoRef.add(8+61*maxSolt).writeU32(capacity); // 容量
        return cargoRef;
    }, 'pointer', ['pointer','uint']));
}

function IsAlter(maxSolt){
    Interceptor.replace(ptr(0x08695A0C), new NativeCallback(function (cargoRef) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('IsAlter------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);;
        return cargoRef.add(12+61*maxSolt).readU8(); // 标志
    }, 'int', ['pointer']));
}

function SendItemList(maxSolt){
    var tempFunc = new NativeCallback(function (cargoRef) {
        // console.log("SendItemList-------------"+cargoRef)
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        var buffCargoRef =getCargoRef(accId,cargoRef);;
        var buff = Memory.alloc(61*maxSolt+9);
        PacketGuard_PacketGuard(buff);
        InterfacePacketBuf_put_header(buff, 0, 13);
        InterfacePacketBuf_put_byte(buff, 12);
        InterfacePacketBuf_put_short(buff,buffCargoRef.add(8+61*maxSolt).readU32());
        InterfacePacketBuf_put_int(buff, buffCargoRef.add(4+61*maxSolt).readU32());
        var itemCount = CAccountCargo_GetItemCount(cargoRef);
        InterfacePacketBuf_put_short(buff, itemCount);
        for (var i = 0; buffCargoRef.add(8+61*maxSolt).readU32() > i; ++i ) {
            if ( buffCargoRef.add(61*i+6).readU32() !=0 ) {
                InterfacePacketBuf_put_short(buff, i);
                InterfacePacketBuf_put_int(buff, buffCargoRef.add(61*i+6).readU32());
                InterfacePacketBuf_put_int(buff, buffCargoRef.add(61*i+11).readU32());
                var integratedPvPItemAttr = GetIntegratedPvPItemAttr(buffCargoRef.add(61*i+4));
                InterfacePacketBuf_put_byte(buff, integratedPvPItemAttr);
                InterfacePacketBuf_put_short(buff, buffCargoRef.add(61*i+15).readU16());
                InterfacePacketBuf_put_byte(buff, buffCargoRef.add(61*i+4).readU8());
                if ( GameWorld_IsEnchantRevisionChannel(G_GameWorld())!=0 ){
                    InterfacePacketBuf_put_int(buff, 0);
                }else{
                    InterfacePacketBuf_put_int(buff, buffCargoRef.add(61*i+17).readU32());
                }
                var abilityType = stAmplifyOption_t_getAbilityType(buffCargoRef.add(61*i+21));
                InterfacePacketBuf_put_byte(buff, abilityType);
                var abilityValue = stAmplifyOption_t_getAbilityValue(buffCargoRef.add(61*i+21));
                InterfacePacketBuf_put_short(buff, abilityValue);
                InterfacePacketBuf_put_byte(buff, 0);
                InterfacePacketBuf_put_packet(buff, buffCargoRef.add(61*i+4));
            }
        }
        InterfacePacketBuf_finalize(buff, 1);
        var v6 = CUser_Send(ptr(cargoRef.readU32()), buff);
        Destroy_PacketGuard_PacketGuard(buff);
        return v6;
    }, 'int', ['pointer']);
    CAccountCargo_SendItemList_NEW =  new NativeFunction(tempFunc, 'int', ['pointer'], {"abi":"sysv"});
    Interceptor.replace(ptr(0x0828a88a), tempFunc);
}

function SendNotifyMoney(maxSolt){
    Interceptor.replace(ptr(0x0828A7DC), new NativeCallback(function (cargo,a2) {
        // console.log("SendNotifyMoney------------"+ptr(cargo))
        var cargoRef = ptr(cargo);
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return;
        }
        var buffCargoRef = getCargoRef(accId,cargoRef);;
        var buff = Memory.alloc(20);
        PacketGuard_PacketGuard(buff);
        InterfacePacketBuf_put_header(buff, 1, a2);
        InterfacePacketBuf_put_byte(buff, 1);
        InterfacePacketBuf_put_int(buff, buffCargoRef.add(4+61*maxSolt).readU32());
        InterfacePacketBuf_finalize(buff, 1);
        CUser_Send(ptr(cargoRef.readU32()), buff);
        Destroy_PacketGuard_PacketGuard(buff);
    }, 'void', ['int','int']));
}

function GetItemCount(maxSolt){
    Interceptor.replace(ptr(0x0828A794), new NativeCallback(function (cargoRef) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('GetItemCount------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        var cap = cargoRef.add(8+61*maxSolt).readU32();
        var index = 0;
        for (var i = 0; i < cap; i++) {
            if( cargoRef.add(61*i+6).readU32()!=0 ){
                index++;
            }
        }
        // console.log("GetItemCount  val:"+index)
        return index;
    }, 'int', ['pointer']));
}

function SubMoney(maxSolt){
    Interceptor.replace(ptr(0x0828A764), new NativeCallback(function (cargoRef,money) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('SubMoney------------------------------------')
        var buffCargoRef = getCargoRef(accId,cargoRef);
        var res;
        if(money!=0){
            res = cargoRef;
            var add = buffCargoRef.add(4+61*maxSolt).readU32();
            if(add >= money){
                buffCargoRef.add(4+61*maxSolt).writeU32(add-money);
            }
        }
        return res;
    }, 'pointer', ['pointer','uint']));
}

function AddMoney(maxSolt){
    Interceptor.replace(ptr(0x0828A742), new NativeCallback(function (cargoRef,money) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('AddMoney------------------------------------')
        var buffCargoRef = getCargoRef(accId,cargoRef);
        var res;
        if(money!=0){
            res = cargoRef;
            var add = buffCargoRef.add(4+61*maxSolt).readU32();
            buffCargoRef.add(4+61*maxSolt).writeU32(add+money);
        }
        return res;
    }, 'pointer', ['pointer','uint']));
}

function GetSpecificItemSlot(maxSolt){
    Interceptor.replace(ptr(0x0828A61A), new NativeCallback(function (cargoRef,itemId) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('GetSpecificItemSlot------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        var cap = cargoRef.add(8+61*maxSolt).readU32();
        if(cap > maxSolt){
            cap =maxSolt;
        }
        for (var i = 0; i < cap; i++) {
            if( cargoRef.add(61*i+6).readU32()==itemId ){
                return i;
            }
        }
        return -1;
    }, 'int', ['pointer','int']));
}

function GetEmptySlot(maxSolt){
    var tempFunc = new NativeCallback(function (cargoRef) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        console.log('GetEmptySlot------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        console.log("GetEmptySlot accId:"+accId+' '+cargoRef)
        var cap = cargoRef.add(8+61*maxSolt).readU32();
        if(cap > maxSolt){
            cap =maxSolt;
        }
        for (var i = 0; i < cap; i++) {
            if( cargoRef.add(61*i+6).readU32()==0 ){
                return i;
            }
        }
        return -1;
    }, 'int', ['pointer']);
    CAccountCargo_GetEmptySlot_NEW = new NativeFunction(tempFunc, 'int', ['pointer'], {"abi":"sysv"});
    Interceptor.replace(ptr(0x0828a580), tempFunc);
}

function CheckValidSlot(maxSolt){
    Interceptor.replace(ptr(0x0828A554), new NativeCallback(function (cargoRef,solt) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('CheckValidSlot------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        var cap = cargoRef.add(8+61*maxSolt).readU32();
        return (solt>=0 && solt<=maxSolt && cap>solt)?1:0;
    }, 'int', ['pointer','int']));
}

function CheckMoneyLimit(maxSolt){
    Interceptor.replace(ptr(0x0828A4CA), new NativeCallback(function (cargoRef,money) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('CheckMoneyLimit------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        var cap = cargoRef.add(8+61*maxSolt).readU32();
        var nowMoney = cargoRef.add(4+61*maxSolt).readU32()
        var manager = G_CDataManager();
        var currUpfradeIfo = AccountCargoScript_GetCurrUpgradeInfo(manager.add(42976),cap);
        return (currUpfradeIfo!=0 && ptr(currUpfradeIfo).add(4).readU32() >= (money+nowMoney))?1:0;
    }, 'int', ['pointer','uint32']));
}

function WithdrawMoney(maxSolt){
    Interceptor.replace(ptr(0x0828A2F6), new NativeCallback(function (cargoRef,money) {
        // console.log("WithdrawMoney------------"+cargoRef)
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        var buffCargoRef = getCargoRef(accId,cargoRef);
        var manage = ARAD_Singleton_ServiceRestrictManager_Get();
        var isRestricted = ServiceRestrictManager_isRestricted(manage.toInt32(),cargoRef,1,26);
        if(isRestricted!=0){
            CUser_SendCmdErrorPacket(cargoRef,309,0xD1);
            return 0;
        }
        var check = CSecu_ProtectionField_Check(ptr(ptr(0x0941F7CC).readU32()),cargoRef,3);
        if(check!=0){
            CUser_SendCmdErrorPacket(cargoRef,309,check);
            return 0;
        }
        // console.log("WithdrawMoney now money:"+money)
        if(money > CAccountCargo_GetMoney(cargoRef) || (money & 0x80000000) !=0) {
            CUser_SendCmdErrorPacket(cargoRef,309,0xA);
            return 0;
        }
        if( CUser_CheckMoney(ptr(cargoRef.readU32()),money)==0 ){
            // console.log('CUser_CheckMoney ---')
            CUser_SendCmdErrorPacket(cargoRef,308,0x5e);
            return 0;
        }else{
            CAccountCargo_SubMoney(cargoRef, money);
            var curCharacInvenW = CUserCharacInfo_getCurCharacInvenW(ptr(cargoRef.readU32()));
            if(CInventory_gain_money(curCharacInvenW, money, 27, 1,0)==0){
                CUser_SendCmdErrorPacket(cargoRef,309,0xA);
                return 0;
            }
        }
        CAccountCargo_SendNotifyMoney(cargoRef.toInt32(),309);
        buffCargoRef.add(12+61*maxSolt).writeU8(1);
        cargoRef.add(12+61*56).writeU8(1);
        // console.log("WithdrawMoney success")
        return 1;
    }, 'int', ['pointer','uint32']));
}

function DepositMoney(maxSolt){
    Interceptor.replace(ptr(0x0828A12A), new NativeCallback(function (cargoRef,money) {
        // console.log("DepositMoney------------"+cargoRef)
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        var buffCargoRef = getCargoRef(accId,cargoRef);
        var manage = ARAD_Singleton_ServiceRestrictManager_Get();
        var isRestricted = ServiceRestrictManager_isRestricted(manage.toInt32(),cargoRef,1,26);
        if(isRestricted!=0){
            CUser_SendCmdErrorPacket(cargoRef,308,0xD1);
            return 0;
        }
        var check = CSecu_ProtectionField_Check(ptr(ptr(0x0941F7CC).readU32()),cargoRef,2);
        if(check!=0){
            CUser_SendCmdErrorPacket(cargoRef,308,check);
            return 0;
        }
        // console.log("DepositMoney now money:"+money+','+CUserCharacInfo_getCurCharacMoney(ptr(cargoRef.readU32()))+','+((money & 0x80000000) !=0))
        if(money > CUserCharacInfo_getCurCharacMoney(ptr(cargoRef.readU32())) || (money & 0x80000000) !=0) {
            CUser_SendCmdErrorPacket(cargoRef,308,0xA);
            return 0;
        }
        // console.log("DepositMoney 2 now money:"+money)
        if(CAccountCargo_CheckMoneyLimit(cargoRef,money)==0){
            // console.log('CAccountCargo_CheckMoneyLimit error')
            CUser_SendCmdErrorPacket(cargoRef,308,0x5f);
            return 0;
        }else{
            // console.log("DepositMoney 3 now money:"+money)
            var curCharacInvenW = CUserCharacInfo_getCurCharacInvenW(ptr(cargoRef.readU32()));
            if(CInventory_use_money(curCharacInvenW, money, 40, 1) !=1){
                CUser_SendCmdErrorPacket(cargoRef,308,0xA);
                return 0;
            }
        }
        // console.log("DepositMoney 4 now money:"+money)
        // 有addMoney方法修改 改这里不重要
        CAccountCargo_AddMoney(cargoRef, money);
        CAccountCargo_SendNotifyMoney(cargoRef.toInt32(),308);
        buffCargoRef.add(12+61*maxSolt).writeU8(1);
        cargoRef.add(12+61*56).writeU8(1);
        // console.log("DepositMoney success")
        return 1;
    }, 'int', ['pointer','uint32']));
}

function MoveItem(maxSolt){
    Interceptor.replace(ptr(0x08289F26), new NativeCallback(function (cargoRef,slot1,slot2) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        console.log('MoveItem------------------------------------'+cargoRef)

        if(CAccountCargo_CheckValidSlot(cargoRef,slot1)==0 || CAccountCargo_CheckValidSlot(cargoRef,slot2)==0 || slot1==slot2){
            return 0;
        }
        cargoRef.add(12+61*56).writeU8(1);
        cargoRef = getCargoRef(accId,cargoRef);
        var temp = Memory.alloc(61);
        Memory.copy(temp,cargoRef.add(61*slot1+4),61-4);
        Memory.copy(cargoRef.add(61*slot1+4),cargoRef.add(61*slot2+4),61-4);
        Memory.copy(cargoRef.add(61*slot2+4),temp,61-4);
        cargoRef.add(12+61*maxSolt).writeU8(1);
        return 1;
    }, 'int', ['pointer','int','int']));
}

function DeleteItem(maxSolt){
    Interceptor.replace(ptr(0x08289E3C), new NativeCallback(function (cargoRef,slot,number) {
        console.log('DeleteItem---'+cargoRef)
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        var buffCargoRef = getCargoRef(accId,cargoRef);
        if(CAccountCargo_CheckValidSlot(cargoRef,slot)==0){
            return 0;
        }
        if( buffCargoRef.add(61*slot+6).readU32()==0 || number<=0 ){
            return 0;
        }
        if(Inven_Item_isEquipableItemType(buffCargoRef.add(61*slot+4)) !=0){
            CAccountCargo_ResetSlot(cargoRef,slot);
            buffCargoRef.add(12+61*maxSolt).writeU8(1);
            cargoRef.add(12+61*56).writeU8(1);
            return 1;
        }
        if(buffCargoRef.add(61*slot+11).readU32() < number ){
            return 0;
        }
        if(buffCargoRef.add(61*slot+11).readU32() <= number ){
            CAccountCargo_ResetSlot(cargoRef,slot);
        }else{
            var num = buffCargoRef.add(61*slot+11).readU32();
            buffCargoRef.add(61*slot+11).writeU32(num-number);
        }
        buffCargoRef.add(12+61*maxSolt).writeU8(1);
        cargoRef.add(12+61*56).writeU8(1);
        return 1;
    }, 'int', ['pointer','int','int']));
}

function InsertItem(maxSolt){

    var tempFunc = new NativeCallback(function (cargoRef,item,slot) {
        console.log('InsertItem-------------------'+cargoRef+' '+slot)
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        var buffCargoRef = getCargoRef(accId,cargoRef);
        if(CAccountCargo_CheckValidSlot(cargoRef,slot)==0){
            // console.log("slot error")
            return -1;
        }
        // console.log("slot success!!!")
        var res = -1;
        if(Inven_Item_isEquipableItemType(item)!=0){
            console.log("Inven_Item_isEquipableItemType  success："+cargoRef.add(61*slot+6).readU32())
            if( buffCargoRef.add(61*slot+6).readU32() ==0 ){
                var v4 = 61*slot;
                buffCargoRef.add(v4+4).writeU32(item.readU32());
                buffCargoRef.add(v4+8).writeU32(item.add(1*4).readU32());
                buffCargoRef.add(v4+12).writeU32(item.add(2*4).readU32());
                buffCargoRef.add(v4+16).writeU32(item.add(3*4).readU32());
                buffCargoRef.add(v4+20).writeU32(item.add(4*4).readU32());
                buffCargoRef.add(v4+24).writeU32(item.add(5*4).readU32());
                buffCargoRef.add(v4+28).writeU32(item.add(6*4).readU32());
                buffCargoRef.add(v4+32).writeU32(item.add(7*4).readU32());
                buffCargoRef.add(v4+36).writeU32(item.add(8*4).readU32());
                buffCargoRef.add(v4+40).writeU32(item.add(9*4).readU32());
                buffCargoRef.add(v4+44).writeU32(item.add(10*4).readU32());
                buffCargoRef.add(v4+48).writeU32(item.add(11*4).readU32());
                buffCargoRef.add(v4+52).writeU32(item.add(12*4).readU32());
                buffCargoRef.add(v4+56).writeU32(item.add(13*4).readU32());
                buffCargoRef.add(v4+60).writeU32(item.add(14*4).readU32());
                buffCargoRef.add(v4+64).writeU8(item.add(60).readU8());
                res = slot;
            }
        }else{
            console.log("Inven_Item_isEquipableItemType  fail："+cargoRef.add(61*slot+6).readU32())
            if(item.add(2).readU32() == buffCargoRef.add(61*slot+6).readU32()){
                var size = buffCargoRef.add(61*slot+11).readU32();
                buffCargoRef.add(61*slot+11).writeU32(size+item.add(7).readU32());
            }else{
                var v4 = 61*slot;
                buffCargoRef.add(v4+4).writeU32(item.readU32());
                buffCargoRef.add(v4+8).writeU32(item.add(1*4).readU32());
                buffCargoRef.add(v4+12).writeU32(item.add(2*4).readU32());
                buffCargoRef.add(v4+16).writeU32(item.add(3*4).readU32());
                buffCargoRef.add(v4+20).writeU32(item.add(4*4).readU32());
                buffCargoRef.add(v4+24).writeU32(item.add(5*4).readU32());
                buffCargoRef.add(v4+28).writeU32(item.add(6*4).readU32());
                buffCargoRef.add(v4+32).writeU32(item.add(7*4).readU32());
                buffCargoRef.add(v4+36).writeU32(item.add(8*4).readU32());
                buffCargoRef.add(v4+40).writeU32(item.add(9*4).readU32());
                buffCargoRef.add(v4+44).writeU32(item.add(10*4).readU32());
                buffCargoRef.add(v4+48).writeU32(item.add(11*4).readU32());
                buffCargoRef.add(v4+52).writeU32(item.add(12*4).readU32());
                buffCargoRef.add(v4+56).writeU32(item.add(13*4).readU32());
                buffCargoRef.add(v4+60).writeU32(item.add(14*4).readU32());
                buffCargoRef.add(v4+64).writeU8(item.add(60).readU8());
            }
            res = slot;
        }
        buffCargoRef.add(12+61*maxSolt).writeU8(1);
        cargoRef.add(12+61*56).writeU8(1);
        // console.log("InsertItem:"+res);
        return res;
    }, 'int', ['pointer','pointer','int']);
    CAccountCargo_InsertItem_NEW = new NativeFunction(tempFunc, 'int', ['pointer','pointer','int'], {"abi":"sysv"});
    Interceptor.replace(ptr(0x08289C82), tempFunc);
}
function Clear(maxSolt){
    Interceptor.replace(ptr(0x0828986C), new NativeCallback(function (cargoRef) {
        // // console.log('Clear:'+cargoRef)
        // 离线是清零
        cargoRef.writeU32(0);
        var cargoRefAdd = cargoRef.add(4);
        for (var i = 0; i < maxSolt; i++) {
            Inven_Item_Inven_Item(cargoRefAdd);
            cargoRefAdd.writeU32(0);
            cargoRefAdd = cargoRefAdd.add(61);
        }
        cargoRef.add(4+61*maxSolt).writeU32(0); // 钱
        cargoRef.add(8+61*maxSolt).writeU32(0); // 容量
        cargoRef.add(12+61*maxSolt).writeU8(0); // 标志
        return cargoRef;
    }, 'pointer', ['pointer']));
}

function SetDBData(maxSolt){
    Interceptor.replace(ptr(0x08289816), new NativeCallback(function (cargoRef,user,item,money,copacity) {
        console.log('SetDBData-------------------'+cargoRef+' '+user+' ,'+item+','+money+'  '+copacity)
        var accId =  CUser_get_acc_id(user);
        // 再设置是 将 重新申请账号金库空间  61*maxSolt是格子 4个字节的钱  4个字节的容量 1个字节的标志
        accountCargfo[accId] = Memory.alloc(61*maxSolt+4+4+1+30);
        var buffCargoRef = cargoRef;
        if(accountCargfo[accId]){
            // 给原来的设置一些默认值，防止获取不到金库
            cargoRef.writePointer(user);
            cargoRef.add(4+61*56).writeU32(money);
            cargoRef.add(8+61*56).writeU32(copacity);
            cargoRef.add(12+61*56).writeU8(0);
            buffCargoRef = accountCargfo[accId];
            // 初始化数据
            for (var i = 0; i < maxSolt; i++) {
                buffCargoRef.add(4+i*61).writeU32(0);
            }
        }
        buffCargoRef.writePointer(user);
        buffCargoRef.add(4+61*maxSolt).writeU32(money);
        buffCargoRef.add(8+61*maxSolt).writeU32(copacity);
        buffCargoRef.add(12+61*maxSolt).writeU8(0);
        if(item!=0){
            Memory.copy(cargoRef.add(4),item,56*61);
            Memory.copy(buffCargoRef.add(4),item,maxSolt*61);
        }
        return cargoRef;
    }, 'pointer', ['pointer','pointer','pointer','uint32','uint32']));
}

function CAccountCargo(maxSolt){
    Interceptor.replace(ptr(0x08289794), new NativeCallback(function (cargoRef) {
        cargoRef.writeU32(0);
        var cargoRefAdd = cargoRef.add(4);
        for (var i = 0; i < 56; i++) {
            Inven_Item_Inven_Item(cargoRefAdd);
            cargoRefAdd.writeU32(0);
            cargoRefAdd = cargoRefAdd.add(61);
        }
        cargoRef.add(4+61*56).writeU32(0); // 钱
        cargoRef.add(8+61*56).writeU32(0); // 容量
        cargoRef.add(12+61*56).writeU8(0); // 标志
    }, 'void', ['pointer']));
}

function GetMoney(maxSolt){
    Interceptor.replace(ptr(0x0822F020), new NativeCallback(function (cargoRef) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('GetMoney------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        // console.log("GetMoney accId:"+accId)
        return cargoRef.add(4+61*maxSolt).readU32();
    }, 'int', ['pointer']));
}

function GetCapacity(maxSolt){
    Interceptor.replace(ptr(0x0822F012), new NativeCallback(function (cargoRef) {
        var accId =  getUserAccId(cargoRef);
        if(accId == -1){
            return 0;
        }
        // console.log('GetCapacity------------------------------------'+cargoRef)
        cargoRef = getCargoRef(accId,cargoRef);
        return cargoRef.add(8+61*maxSolt).readU32();
    }, 'int', ['pointer']));
}


function getCargoRef(accId,cargoRef){
    if(accountCargfo[accId]){
        cargoRef = accountCargfo[accId];
    }else{
        // 解决 判断文件中是否有缓存的配置 如果有就加载
        if(initCharacAccountCargoDbData(accId, cargoRef)){
            cargoRef = accountCargfo[accId];
        }
    }
    return cargoRef;
}

function initCharacAccountCargoDbData(accId,cargoRef){
    console.log("initCharacAccountCargoDbData:"+accId)
    var dbHandelAddr = DBMgr_GetDBHandle(ptr(ptr(0x0940BDAC).readU32()),2,0);
    var dbHandel = ptr(dbHandelAddr);
    var mId = Memory.allocUtf8String(accId+'');
    MySQL_set_query_3_ptr(dbHandel,Memory.allocUtf8String('seLect capacity, money, cargo from account_cargo where m_id = %s'),mId);
    if(MySQL_exec(dbHandel,1)!=1){
        console.log("pre one 111")
        return false;
    }
    if(MySQL_get_n_rows(dbHandel)==0){
        console.log("pre one 222")
        return false;
    }
    if(MySQL_fetch(dbHandel)!=1){
        console.log("pre one 333")
        return false;
    }
    var maxSolt = initMaxSolt;
    accountCargfo[accId] = Memory.alloc(61*maxSolt+4+4+1+30);
    var buffCargoRef = accountCargfo[accId];
    // 初始化数据
    for (var i = 0; i < maxSolt; i++) {
        buffCargoRef.add(4+i*61).writeU32(0);
    }
    buffCargoRef.writePointer(ptr(cargoRef).readPointer());
    buffCargoRef.add(12+61*maxSolt).writeU8(0);
    var res = false;
    if(MySQL_get_uint(dbHandel,0,buffCargoRef.add(8+61*maxSolt))!=1){
        // console.log('uint capacity get error')
        console.log("pre one 444")
        res =  false;
    }else if(MySQL_get_uint(dbHandel,1,buffCargoRef.add(4+61*maxSolt))!=1){
        console.log('uint money get error')
        res =  false;
    }else{
        var v10 = Memory.alloc(61*maxSolt*4);
        for (var i = 0; i < 61*maxSolt; i++) {
            v10.add(i*4).writeU32(0);
        }
        var binaryLength = MySQL_get_binary_length(dbHandel,2);
        if( MySQL_get_binary(dbHandel,2,v10, binaryLength)!=1 ){
            res =  true;
        }else{
            binaryLength = MySQL_get_binary_length(dbHandel,2);
            var maxLength = 61*maxSolt;
            var v17Addr = Memory.alloc(4);
            v17Addr.writeInt(maxLength)
            if(uncompress_zip(buffCargoRef.add(4), v17Addr, v10, binaryLength) !=1){
                // console.log("uncompress_zip error  !!!")
                res =  false;
            }else if(v17Addr.readU32()!=0 && v17Addr.readU32()%(61*maxSolt) !=0){
                res =  false;
            }else{
                res =  true;
            }
        }
    }
    if(!res){
        delete accountCargfo[accId]
    }
    return res;
}

function getUserAccId(cargoRef){
    if(cargoRef == 0){
        return -1;
    }
    var userAddr = ptr(cargoRef.readU32());
    if(userAddr == 0){
        return -1;
    }
    return CUser_get_acc_id(userAddr);
}
function setMaxUpGrade(maxLevel){
    if(maxLevel) {
        console.log("1")
        calcurateUserMaxLevel(maxLevel);
        setUserMaxLevel(maxLevel);
        isThereDailyTrainingQuestList(maxLevel);
        calLevelUpItemCheck(maxLevel);
        getLevelSectionExp();
        getSpAtLevelUp(maxLevel);
        setLevelExp(maxLevel);
        setRewardSp(maxLevel);
        checkLevelUp(maxLevel);
        gainExpSp(maxLevel);
        onLevelUp(maxLevel);
        increaseStatus(maxLevel);
        getReturnUserLevelKey(maxLevel)
        console.log("13");

    }
}

function getReturnUserLevelKey(maxLevel){
    Interceptor.replace(ptr(0x0869230a), new NativeCallback(function (stNotifyIngameADInfo, a2, a3) {
        console.log('getReturnUserLevelKey '+stNotifyIngameADInfo+' '+a2+' '+a3);
        if(a3!=0){
            if(a2 <=19){
                return 15;
            }
        }else{
            if(a2 <=9){
                return 5;
            }
            if(a2 <=14){
                return 10;
            }
            if(a2 <=19){
                return 15;
            }
        }
        if(a2 <=29){
            return 20;
        }
        if(a2 <=39){
            return 30;
        }
        if(a2 <=49){
            return 40;
        }
        if(a2 <=59){
            return 50;
        }
        if(a2 <=69){
            return 60;
        }
        if(a2 <=79){
            return 70;
        }
        if(a2 <=86){
            return 80;
        }
        return 60;
    }, 'int', ['pointer', 'int', 'int']));
}

/**
 * 物品使用状态  1205	背刺蜘蛛
 */
function increaseStatus(maxLevel){
    // Interceptor.attach(ptr(0x086657fc), {
    //
    //     onEnter: function (args) {
    //         console.log("increaseStatus:"+args[0]+','+args[1]);
    //         var userItem =  Memory.alloc(128);
    //         var curCharacInvenRef = CUserCharacInfo_getCurCharacInvenR(args[0]);
    //         CInventory_GetInvenSlot(userItem,curCharacInvenRef.toInt32(),1,parseInt(args[1]));
    //         console.log('use Item:'+userItem.readU16());
    //         console.log('use Item:'+userItem.add(2).readU32()); // itemId
    //     }
    // });
    Interceptor.replace(ptr(0x086657fc), new NativeCallback(function (user, slof) {
        console.log("increaseStatus:"+user+','+slof);
        var buff =  Memory.alloc(12);
        PacketGuard_PacketGuard(buff);
        console.log('CUser_CheckInTrade(user):'+CUser_CheckInTrade(user))
        if(CUser_CheckInTrade(user)!=0){
            CUser_SendCmdErrorPacket(user, 32, 19, buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        var userItem =  Memory.alloc(128);
        var curCharacInvenRef = CUserCharacInfo_getCurCharacInvenR(user);
        CInventory_GetInvenSlot(userItem,curCharacInvenRef.toInt32(),1,parseInt(slof));
        var itemId = userItem.add(2).readU32();
        // 标志类型
        var v184 = -1;
        // 要加的值
        var guildExpBook = 0;

        if(itemId == 1205){
            v184 = 17;
            guildExpBook = 5;
            goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
            return;
        }
        if(itemId <= 1205){
            if(itemId == 1036){
                v184 = 1;
                guildExpBook = 10000;
                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId <= 1036){
                if(itemId!= 201){
                    if(itemId > 201){
                        if(itemId == 963){
                            goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        if(itemId > 963){
                            if(itemId == 1034){
                                v184 = 1;
                                guildExpBook = 100;
                                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                                return;
                            }
                            if(itemId>1034){
                                v184 = 1;
                                guildExpBook = 1000;
                                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                                return;
                            }
                            if(itemId == 1031){
                                v184 = 0;
                                guildExpBook = 5;
                                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                                return;
                            }
                            goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        if(itemId==916 || itemId==960){
                            goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(itemId!=42){
                        if(itemId<=42){
                            if(itemId!=3 && itemId!=28){
                                goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                                return;
                            }
                            goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        if(itemId==109 || itemId>=109 && itemId-161<=1){
                            goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    v184 = 21;
                    guildExpBook = 1;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                var repeatEvent = CEventManager_GetRepeatEvent(ptr(ptr(0x0941F730).readU32()),15);
                var tempMethod =  new NativeFunction(repeatEvent.add(52*4), 'int', ['pointer','int'], {"abi":"sysv"});
                if(repeatEvent!=0 && tempMethod(repeatEvent,0)!=0){
                    InterfacePacketBuf_clear(buff);
                    InterfacePacketBuf_put_header(buff,1,32);
                    InterfacePacketBuf_put_byte(buff,0);
                    InterfacePacketBuf_put_byte(buff,19);
                    InterfacePacketBuf_finalize(buff,1);
                    CUser_Send(user,buff);
                    PacketGuard_free_PacketGuard(buff);
                    return;
                }
                if(CUser_getCurCharacTotalFatigue(user)!=0){
                    InterfacePacketBuf_clear(buff);
                    InterfacePacketBuf_put_header(buff,1,32);
                    InterfacePacketBuf_put_byte(buff,0);
                    InterfacePacketBuf_put_byte(buff,67);
                    InterfacePacketBuf_finalize(buff,1);
                    CUser_Send(user,buff);
                    PacketGuard_free_PacketGuard(buff);
                    return;
                }
                v184 = 14;
                guildExpBook = 0;
                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId == 1043){
                v184 = 2;
                guildExpBook = 250;
                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId <= 1043){
                if(itemId == 1039){
                    v184 = 4;
                    guildExpBook = 50;
                }else if(itemId > 1039){
                    if(itemId == 1041){
                        v184 = 5;
                        guildExpBook = 50;
                    }else{
                        if(itemId > 1041){
                            v184 = 7;
                        }else{
                            v184 = 6;
                        }
                        guildExpBook = 50;
                    }
                }else if(itemId == 1037){
                    v184 = 1;
                    guildExpBook = 100000;
                }else{
                    v184 = 0;
                    guildExpBook =20;
                }
                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId == 1046){
                // 抗性之石
                v184 = 9;
                guildExpBook = 10;
                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId <= 1046){
                // todo 魔力之石
                if(itemId == 1044){
                    v184 = 3;
                    guildExpBook = 250;
                }else {
                    v184 = 8;
                    guildExpBook = 10;
                }
                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId < 1200){
                goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId > 1202){
                if(itemId == 1204){
                    v184 = 17;
                    guildExpBook = 1;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            goto171(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
            return;
        }
        if(itemId <= 8268){
            if(itemId < 8267){
                if(itemId == 7101){
                    if(CUser_get_state(user)!=3){
                        CUser_SendCmdErrorPacket(user,32,19,buff);
                        PacketGuard_free_PacketGuard(buff);
                        return;
                    }
                    v184 = 13;
                    guildExpBook = 8;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                if(itemId <= 7101){
                    if(itemId==1232){
                        v184 = 1;
                        guildExpBook = 10000000;
                        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(itemId <= 1232){
                        if(itemId!=1206){
                            if(itemId==1231){
                                v184 = 1;
                                guildExpBook = 1000000;
                                goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                                return;
                            }
                            goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(itemId == 1253){
                        goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(itemId !=3204){
                        if(itemId==1247){
                            v184 = 19;
                            guildExpBook = 10;
                            goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(CUserCharacInfo_get_charac_guildkey(user)!=0){
                        v184 = 11;
                        guildExpBook = 10;
                        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    CUser_SendCmdErrorPacket(user,32,64,buff);
                    PacketGuard_free_PacketGuard(buff);
                    return;
                }
                if(itemId==7181){
                    v184 = 13;
                    guildExpBook = 5;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                if(itemId <= 7181){
                    if(itemId != 7105){
                        if(itemId == 7180){
                            v184 = 13;
                            guildExpBook = 1;
                            goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                            return;
                        }
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(CUserCharacInfo_get_charac_guildkey(user)){
                        v184 = 11;
                        guildExpBook = 50;
                        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    CUser_SendCmdErrorPacket(user,32,64,buff);
                    PacketGuard_free_PacketGuard(buff);
                    return;
                }
                if(itemId == 7958){
                    v184 = 20;
                    guildExpBook = 0;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                if(itemId!=8049){
                    if(itemId != 7298){
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(CUser_get_state(user)!=3){
                        CUser_SendCmdErrorPacket(user,32,19,buff);
                        PacketGuard_free_PacketGuard(buff);
                        return;
                    }
                    v184 = 13;
                    guildExpBook = 2;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
            }
            goto96(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
            return;
        }
        if(itemId==2670008){
            var repeatEvent = CEventManager_GetRepeatEvent(ptr(ptr(0x0941F730).readU32()),15);
            var tempMethod =  new NativeFunction(repeatEvent.add(52*4), 'int', ['pointer','int'], {"abi":"sysv"});
            if(repeatEvent!=0 && tempMethod(repeatEvent,0)!=0){
                InterfacePacketBuf_clear(buff);
                InterfacePacketBuf_put_header(buff,1,32);
                InterfacePacketBuf_put_byte(buff,0);
                InterfacePacketBuf_put_byte(buff,19);
                InterfacePacketBuf_finalize(buff,1);
                CUser_Send(user,buff);
                PacketGuard_free_PacketGuard(buff);
                return;
            }
            if(CUser_getCurCharacTotalFatigue(user)!=0){
                InterfacePacketBuf_clear(buff);
                InterfacePacketBuf_put_header(buff,1,32);
                InterfacePacketBuf_put_byte(buff,0);
                InterfacePacketBuf_put_byte(buff,67);
                InterfacePacketBuf_finalize(buff,1);
                CUser_Send(user,buff);
                PacketGuard_free_PacketGuard(buff);
                return;
            }
            v184 = 14;
            guildExpBook = 0;
            goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
            return;
        }
        if(itemId>2670008){
            if(itemId!=10000915){
                if(itemId<=10000915){
                    if(itemId==2675021){
                        v184 = 21;
                        guildExpBook = 1;
                        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(itemId!=2675388){
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    goto96(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                if ( itemId == 690000220 ){
                    goto171(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                if ( itemId == 690000301 )
                {
                    v184 = 25;
                    guildExpBook = 1;
                    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                if ( itemId != 690000097 ){
                    goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
            }
            var tempV184Tag = Memory.alloc(1);
            tempV184Tag.writeU8(v184);
            var tempGuildExpBookTag = Memory.alloc(4);
            tempGuildExpBookTag.writeU32(guildExpBook);
            if(CUser_CalLevelUpItemState(user,tempV184Tag,tempGuildExpBookTag,1,69)!=1){
                v184 = tempV184Tag.readU8();
                guildExpBook = tempGuildExpBookTag.readU32();
                CUser_SendCmdErrorPacket(user,32,19,buff);
                PacketGuard_free_PacketGuard(buff);
                return;
            }
            v184 = tempV184Tag.readU8();
            guildExpBook = tempGuildExpBookTag.readU32();
            goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
            return;
        }
        if(itemId != 2660171){
            if(itemId<=2660171){
                if(itemId-8270>1){
                    goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                    return;
                }
                goto96(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
            if(itemId!=2660232){
                if(itemId!=2660396){
                    if(itemId!=2660172){
                        goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    if(GameWorld_IsPvPSkilTreeChannel(G_GameWorld)!=1){
                        CUser_SendCmdErrorPacket(user,32,23,buff);
                        PacketGuard_free_PacketGuard(buff);
                        return;
                    }
                    var check = CSecu_ProtectionField_Check(ptr(ptr(0x0941F7CC).readU32()),user,44);
                    if(check!=0){
                        CUser_SendCmdErrorPacket(user,32,check,buff);
                        PacketGuard_free_PacketGuard(buff);
                        return;
                    }
                    if(CUser_isAffectedPremium(user.toInt32(),33)==1){
                        guildExpBook = 0;
                        v184 = 23;
                        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                        return;
                    }
                    CUser_SendCmdErrorPacket(user,32,216,buff);
                    PacketGuard_free_PacketGuard(buff);
                    return;
                }
                goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
                return;
            }
        }
        if(GameWorld_IsPvPSkilTreeChannel(G_GameWorld())!=1){
            CUser_SendCmdErrorPacket(user,32,23,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        var check = CSecu_ProtectionField_Check(ptr(ptr(0x0941F7CC).readU32()),user,44);
        if(check!=0){
            CUser_SendCmdErrorPacket(user,32,check,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        if(WongWork_CSkillChanger_CheckCondition(user)!=1){
            CUser_SendCmdErrorPacket(user,32,1,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        guildExpBook = 0;
        v184 = 22;
        if(itemId == 2660232){
            guildExpBook = 1;
        }
        // 根据类型处理数据 -------------------------------------------------------------------------
        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
        return;
    }, 'void', ['pointer', 'int']));
}

function goto171(user,buff,v184,guildExpBook,slof,itemId,maxLevel){
    if(itemId == 690000220 && CUser_IsGuildMaster(user)!=1){
        CUser_SendCmdErrorPacket(user,32,219,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    var guildVal = user.add(577595).readU32() +1;
    var guildLevelUpParam = GuildParameterScript_getGuildLevelUpParam(G_CDataManager().add(42252),guildVal);
    var guildDBInfo = CUser_GetGuildDBInfo(user);
    if(guildLevelUpParam!=0 || guildDBInfo.add(41).readU32() >=guildLevelUpParam.add(4).readU32()){
        CUser_SendCmdErrorPacket(user,32,210,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    v184 = 16;
    guildExpBook = GuildParameterScript_getGuildExpBook(G_CDataManager().add(42252),itemId);
    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
}

function goto96(user,buff,v184,guildExpBook,slof,itemId,maxLevel){
    var tempV184Tag = Memory.alloc(1);
    tempV184Tag.writeU8(v184);
    var tempGuildExpBookTag = Memory.alloc(4);
    tempGuildExpBookTag.writeU32(guildExpBook);
    if(CUser_CalLevelUpItemCheck(user,item_id)!=0 && CUser_CalLevelUpItemState(user,tempV184Tag,tempGuildExpBookTag,19,59)!=1){
        v184 = tempV184Tag.readU8();
        guildExpBook = tempGuildExpBookTag.readU32();
        CUser_SendCmdErrorPacket(user,32,19,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    v184 = tempV184Tag.readU8();
    guildExpBook = tempGuildExpBookTag.readU32();
    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
}

function goto125(user,buff,v184,guildExpBook,slof,itemId,maxLevel){
    var check = CSecu_ProtectionField_Check(ptr(ptr(0x0941F7CC).readU32()),user,44);
    if(check!=0){
        CUser_SendCmdErrorPacket(user,32,check,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    if(GameWorld_IsPvPSkilTreeChannel(G_GameWorld())!=0){
        CUser_SendCmdErrorPacket(user,32,23,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    if(CUser_CheckItemLock(user,1,slof)!=0){
        CUser_SendCmdErrorPacket(user,32,213,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    if(WongWork_CSkillChanger_CheckCondition(user)!=1){
        var levelExp = CDataManager_get_level_exp(G_CDataManager(),CUserCharacInfo_get_charac_level(user));
        CUserCharacInfo_setCurCharacExp(user,levelExp);
    }
    if(itemId!=1206 && itemId!=1253){
        if(itemId==916){
            guildExpBook = 1;
            v184 = 10;
        }else if(itemId == 960){
            guildExpBook =  1;
            v184 = 24;
            var expandData = CUser_GetCharacExpandData(user,11);
            if(expandData){
                CQuestShop_clearQP(expandData,user);
                CQuestShop_sendCharacQp(expandData,user,0);
            }
        }else{
            guildExpBook  = itemId==963 || itemId==2660396;
            v184 = 10;
        }
        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
        return;
    }
    if(CUserCharacInfo_get_charac_level(user) <=49){
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    if(CUser_isAffectedPremium(user.toInt32(),33)==0){
        guildExpBook = 1;
        v184 = 18;
        goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
        return;
    }
    CUser_SendCmdErrorPacket(user,32,216,buff);
    PacketGuard_free_PacketGuard(buff);
    return;
}

function goto178(user,buff,v184,guildExpBook,slof,itemId,maxLevel){
    var itemIdVal = G_CDataManager().add(20684).readU32();
    console.log('itemIdVal : '+itemIdVal);
    if(itemId == itemIdVal){
        var item = CDataManager_find_item(G_CDataManager(),itemIdVal);
        if(itemIdVal==0){
            CUser_SendCmdErrorPacket(user,32,17,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        if(CItem_is_stackable(item)!=1){
            CUser_SendCmdErrorPacket(user,32,17,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        var characLevel = CUserCharacInfo_get_charac_level(user);
        var usableLevel =CItem_GetUsableLevel(item);
        if(characLevel < usableLevel){
            CUser_SendCmdErrorPacket(user,32,17,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        if(characLevel > maxLevel+1){
            CUser_SendCmdErrorPacket(user,32,17,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        v184 =1;
        var alloc = Memory.alloc(4);
        if(CItem_GetIncreaseStatusIntData(item,0,alloc)!=1){
            CUser_SendCmdErrorPacket(user,32,17,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
        guildExpBook = alloc.readU32();
    }else{
        var item = CDataManager_find_item(G_CDataManager(),itemId);
        if(item!=0){
            if(CItem_GetIncreaseStatusType(item) ==1){
                var alloc = Memory.alloc(4);
                CItem_GetIncreaseStatusIntData(item,0,alloc);
                guildExpBook = alloc.readU32();
                v184 = 1;
            }
        }
    }
    goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel);
    return;
}

function goto191(user,buff,v184,guildExpBook,slof,itemId,maxLevel){
    if(v184==-1){
        CUser_SendCmdErrorPacket(user,32,17,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    if(v184==15){
        if(CUser_get_state(user)==3){
            var curCharacFatigue = CUserCharacInfo_getCurCharacFatigue(user);
            if(curCharacFatigue < guildExpBook){
                CUser_SendCmdErrorPacket(user,32,95,buff);
                PacketGuard_free_PacketGuard(buff);
                return;
            }
        }else{
            CUser_SendCmdErrorPacket(user,32,19,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
    }
    if(v184!=0 || v184==17 || v184==1){
        if(GameWorld_IsPvPSkilTreeChannel(G_GameWorld())!=0){
            CUser_SendCmdErrorPacket(user,32,19,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
    }
    var item = CDataManager_find_item(G_CDataManager(),itemId);
    if(item==0){
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    var usablePvpRank = CItem_GetUsablePvPRank(item);
    if(usablePvpRank>CUserCharacInfo_get_pvp_grade(user)){
        CUser_SendCmdErrorPacket(user,32,1,buff);
        PacketGuard_free_PacketGuard(buff);
        return;
    }
    var tempMethod =  new NativeFunction(ptr(item.readU32()).add(12*4).readPointer(), 'int', ['pointer'], {"abi":"sysv"});
    if(tempMethod(item) !=21){
        var curCharacInvenW = CUserCharacInfo_getCurCharacInvenW(user);
        if( CInventory_delete_item(curCharacInvenW,1,slof,1,10,1) !=1 ){
            CUser_SendCmdErrorPacket(user,32,17,buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        }
    }
    var v172 = Memory.alloc(151);
    console.log("item all handle success: "+v184+" ,"+guildExpBook);
    switch (v184){
        case 0:
            CUser_gain_sp(user,guildExpBook);
            CUser_history_log_sp(user,-1,guildExpBook,1);
            InterfacePacketBuf_clear(buff);
            InterfacePacketBuf_put_header(buff, 1, 32);
            InterfacePacketBuf_put_byte(buff, 1);
            InterfacePacketBuf_put_short(buff, slof);
            InterfacePacketBuf_put_byte(buff, v184);
            InterfacePacketBuf_put_int(buff, guildExpBook);
            InterfacePacketBuf_put_short(buff, 0);
            InterfacePacketBuf_put_short(buff, 0);
            InterfacePacketBuf_finalize(buff, 1);
            CUser_Send(user, buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        case 1:
            if(CUserCharacInfo_get_charac_level(user)>maxLevel-1){
                guildExpBook = 0;
            }
            var v180 = Memory.alloc(4);
            v180.writeU32(0);
            var v179 = Memory.alloc(4);
            v179.writeU32(0);
            var v181 = Memory.alloc(4);
            v181.writeU32(0);
            var spVal = Memory.alloc(4);
            spVal.writeU32(0);
            if(itemId == G_CDataManager().add(20684).readU32()){
                CUser_rewardExp(user,guildExpBook,spVal.toInt32(),v181.toInt32(),v180,v179,3,1);
            }else{
                CUser_rewardExp(user,guildExpBook,spVal.toInt32(),v181.toInt32(),v180,v179,0,1);
            }
            console.log('1111111111111')
            InterfacePacketBuf_clear(buff);
            InterfacePacketBuf_put_header(buff, 1, 32);
            InterfacePacketBuf_put_byte(buff, 1);
            InterfacePacketBuf_put_short(buff, slof);
            InterfacePacketBuf_put_byte(buff, v184);
            InterfacePacketBuf_put_int(buff, guildExpBook);
            InterfacePacketBuf_put_short(buff, v179.readU32()-v180.readU32());
            InterfacePacketBuf_put_short(buff, v181.readU32());
            InterfacePacketBuf_finalize(buff, 1);
            CUser_Send(user, buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        case 2:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            ptr(userAddInfo).writeU32(ptr(userAddInfo).readU32()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 3:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            userAddInfo = userAddInfo.add(4);
            ptr(userAddInfo).writeU32(ptr(userAddInfo).readU32()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 4:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            userAddInfo = userAddInfo.add(8);
            ptr(userAddInfo).writeU16(ptr(userAddInfo).readU16()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 5:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            userAddInfo = userAddInfo.add(10);
            ptr(userAddInfo).writeU16(ptr(userAddInfo).readU16()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 6:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            userAddInfo = userAddInfo.add(12);
            ptr(userAddInfo).writeU16(ptr(userAddInfo).readU16()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 7:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            userAddInfo = userAddInfo.add(14);
            ptr(userAddInfo).writeU16(ptr(userAddInfo).readU16()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 8:
            var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
            userAddInfo = userAddInfo.add(66);
            ptr(userAddInfo).writeU32(ptr(userAddInfo).readU32()+guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 9:
            for (var i = 0; i < 3; i++) {
                var userAddInfo = CUserCharacInfo_getCurCharacAddInfoRefW(user);
                userAddInfo = userAddInfo.add(2*(i*8));
                ptr(userAddInfo).writeU16(ptr(userAddInfo).readU16()+guildExpBook);
            }
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 10:
        case 24:
            if(guildExpBook ==1){
                var curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_clear_all_skills_both(curCharacSkillW);
                curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_parent(curCharacSkillW,user);
                var charcJob = CUserCharacInfo_get_charac_job(user);
                curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                addSkillOnCreateCharacter(curCharacSkillW+70,charcJob);
                charcJob = CUserCharacInfo_get_charac_job(user);
                curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                addSkillOnCreateCharacter(curCharacSkillW+478,charcJob);
                // 这应该是技能只能12个原因
                var skillList = Memory.alloc(12);
                std_vector_std_pair_int_int_vector(skillList);

                var curCharSecondGrowType = CUserCharacInfo_getCurCharSecondGrowType(user);
                var curCharFirstGrowType = CUserCharacInfo_getCurCharFirstGrowType(user);
                var dataManage = G_CDataManager().add(20).readU32();
                var giveSkill = CCharacter_get_give_skill(dataManage+2012*CUserCharacInfo_get_charac_job(user),curCharFirstGrowType,curCharSecondGrowType,skillList.toInt32(),0);
                var v80;
                if(giveSkill !=1){
                    var charcName = '';
                    var charcNameVal = CUserCharacInfo_getCurCharacName(userInfo);
                    if(!charcNameVal.isNull())
                    {
                        charcName =  charcNameVal.readUtf8String(-1);
                    }
                    var v184Adrr = Memory.alloc(1);
                    v184Adrr.writeU8(v184);
                    LogManager_logFormat(Memory.alloc(1),Memory.allocUtf8String("user.cpp").toInt32()
                        ,Memory.allocUtf8String('void CUser::increase_status(short int)'),ptr(0x4aee)
                        ,Memory.allocUtf8String('User %s - CUser::set_grow_type %d'),Memory.allocUtf8String(charcName),v184Adrr);
                    v80 = 0;
                }else{
                    for (var i = 0;; i++) {
                        if(std_vector_std_pair_int_int_size(skillList) <=i){
                            break;
                        }
                        var skill = std_vector_std_pair_int_int_operator(skillList,i);
                        SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),skill.readU8(),skill.add(1).readU8(),0);
                        SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),skill.readU8(),skill.add(1).readU8(),1);
                    }
                    var curCharacExpertJobType =CUserCharacInfo_GetCurCharacExpertJobType(user);
                    var expertJobScript = CDataManager_GetExpertJobScript(G_CDataManager(),curCharacExpertJobType);
                    if(expertJobScript!=0){
                        for (var i = 0; ; i++) {
                            if(std_vector_std_pair_int_int_size(expertJobScript.add(12)) <=i){
                                break;
                            }
                            var curCharacExpertJobExp = CUserCharacInfo_GetCurCharacExpertJobExp(user);
                            var curExpertJobLevel = CUser_GetCurExpertJobLevel(user, curCharacExpertJobExp);
                            var expertJob = std_vector_std_pair_int_int_operator(expertJobScript.add(12),i);
                            SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),expertJob.readU16(),curExpertJobLevel,0);
                            SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),expertJob.readU16(),curExpertJobLevel,1);
                        }
                    }
                    WongWork_CSkillChanger_CSkillChanger(v172);
                    WongWork_CSkillChanger_SkillInitialize(v172,user,0,0);
                    CUser_send_skill_info(user);
                    var premiumLetheManager = CGameManager_GetPremiumLetheManager(G_CGameManager());
                    if(CUser_isAffectedPremium(user.toInt32(),33)!=0){
                        if(premiumLetheManager!=0 && CUser_isAffectedPremium(user.toInt32(),33)!=0){
                            CPremiumLetheManager_InitLetheSkill(premiumLetheManager, user, 0);
                            CPremiumLetheManager_InitLetheSkill(premiumLetheManager, user, 1);
                        }
                    }else if(premiumLetheManager!=0){
                        CPremiumLetheManager_UpdateBackupSkillFlag(premiumLetheManager,user,0);
                        CPremiumLetheManager_UpdateBackupSkillFlag(premiumLetheManager,user,1);
                    }
                    WongWork_CSkillChanger_d_CSkillChanger(v172);
                    v80 = 1;
                }
                std_vector_std_pair_int_int_d_vector(skillList);
                if(v80==1){
                    InterfacePacketBuf_clear(buff);
                    InterfacePacketBuf_put_header(buff, 1, 32);
                    InterfacePacketBuf_put_byte(buff, 1);
                    InterfacePacketBuf_put_short(buff, slof);
                    InterfacePacketBuf_put_byte(buff, v184);
                    InterfacePacketBuf_put_int(buff, guildExpBook);
                    InterfacePacketBuf_put_short(buff, 0);
                    InterfacePacketBuf_put_short(buff, 0);
                    InterfacePacketBuf_finalize(buff, 1);
                    CUser_Send(user, buff);
                    if(user.add(144369*4).readU32() == 5){
                        var party = CUser_GetParty(user);
                        if(party!=0){
                            InterfacePacketBuf_clear(buff);
                            InterfacePacketBuf_put_header(buff, 0, 2);
                            InterfacePacketBuf_put_byte(buff, 1);
                            InterfacePacketBuf_put_short(buff, 1);
                            CUser_make_basic_info(user, buff, 1);
                            InterfacePacketBuf_finalize(buff, 1);
                            CParty_send_to_party(party, buff);
                        }
                    }else if(user.add(144369*4).readU32() == 8){
                        var warRoom = CUser_GetWarRoom(user);
                        if(warRoom!=0){
                            InterfacePacketBuf_clear(buff);
                            InterfacePacketBuf_put_header(buff, 0, 2);
                            InterfacePacketBuf_put_byte(buff, 1);
                            InterfacePacketBuf_put_short(buff, 1);
                            CUser_make_basic_info(user, buff, 1);
                            InterfacePacketBuf_finalize(buff, 1);
                            WarRoom_SendToRoom(warRoom, buff);
                        }
                    }
                }
            }else {
                var curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                var curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_clear_all_skills(curCharacSkillW,user);
                curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_parent(curCharacSkillW,user);
                curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                if(curCharacSkillTreeIndex==-1 || curCharacSkillTreeIndex!=0){
                    curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                    addSkillOnCreateCharacter(curCharacSkillW.add(70),CUserCharacInfo_get_charac_job(user));
                }else {
                    curCharacSkillW = CUserCharacInfo_getCurCharacSkillW(user);
                    addSkillOnCreateCharacter(curCharacSkillW.add(478),CUserCharacInfo_get_charac_job(user));
                }
                // 这应该是技能只能12个原因
                var skillList = Memory.alloc(12);
                std_vector_std_pair_int_int_vector(skillList);

                var curCharSecondGrowType = CUserCharacInfo_getCurCharSecondGrowType(user);
                var curCharFirstGrowType = CUserCharacInfo_getCurCharFirstGrowType(user);
                var dataManage = G_CDataManager().add(20).readU32();
                var giveSkill = CCharacter_get_give_skill(dataManage+2012*CUserCharacInfo_get_charac_job(user),curCharFirstGrowType,curCharSecondGrowType,skillList.toInt32(),0);
                var v80;
                if(giveSkill !=1){
                    var charcName = '';
                    var charcNameVal = CUserCharacInfo_getCurCharacName(userInfo);
                    if(!charcNameVal.isNull())
                    {
                        charcName =  charcNameVal.readUtf8String(-1);
                    }
                    var v184Adrr = Memory.alloc(1);
                    v184Adrr.writeU8(v184);
                    LogManager_logFormat(Memory.alloc(1),Memory.allocUtf8String("user.cpp").toInt32()
                        ,Memory.allocUtf8String('void CUser::increase_status(short int)'),ptr(0x4B48)
                        ,Memory.allocUtf8String('User %s - CUser::set_grow_type %d'),Memory.allocUtf8String(charcName),v184Adrr);
                    v80 = 0;
                }else{
                    for (var i = 0;; i++) {
                        if(std_vector_std_pair_int_int_size(skillList) <=i){
                            break;
                        }
                        var skill = std_vector_std_pair_int_int_operator(skillList,i);
                        SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),skill.readU8(),skill.add(1).readU8()
                            ,CUserCharacInfo_GetCurCharacSkillTreeIndex(user));
                    }
                    var curCharacExpertJobType =CUserCharacInfo_GetCurCharacExpertJobType(user);
                    var expertJobScript = CDataManager_GetExpertJobScript(G_CDataManager(),curCharacExpertJobType);
                    if(expertJobScript!=0){
                        for (var i = 0; ; i++) {
                            if(std_vector_std_pair_int_int_size(expertJobScript.add(12)) <=i){
                                break;
                            }
                            var curCharacExpertJobExp = CUserCharacInfo_GetCurCharacExpertJobExp(user);
                            var curExpertJobLevel = CUser_GetCurExpertJobLevel(user, curCharacExpertJobExp);
                            var expertJob = std_vector_std_pair_int_int_operator(expertJobScript.add(12),i);
                            SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),expertJob.readU16(),curExpertJobLevel,0);
                            SkillSlot_growtype_skill(CUserCharacInfo_getCurCharacSkillW(user),CUserCharacInfo_get_charac_job(user),expertJob.readU16(),curExpertJobLevel,1);
                        }
                    }
                    WongWork_CSkillChanger_CSkillChanger(v172);
                    var v219 = 0;
                    curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                    if(curCharacSkillTreeIndex==-1 || curCharacSkillTreeIndex!=0){
                        v219 = 1;
                    }else {
                        v219 = 2;
                    }
                    WongWork_CSkillChanger_SkillInitialize(v172,user,v219,0);
                    CUser_send_skill_info(user);
                    var premiumLetheManager = CGameManager_GetPremiumLetheManager(G_CGameManager());
                    if(CUser_isAffectedPremium(user.toInt32(),33)!=0){
                        if(premiumLetheManager!=0 && CUser_isAffectedPremium(user.toInt32(),33)!=0){
                            curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                            CPremiumLetheManager_InitLetheSkill(premiumLetheManager, user, curCharacSkillTreeIndex);
                        }
                    }else if(premiumLetheManager!=0){
                        curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                        CPremiumLetheManager_UpdateBackupSkillFlag(premiumLetheManager,user,curCharacSkillTreeIndex);
                    }
                    WongWork_CSkillChanger_d_CSkillChanger(v172);
                    v80 = 1;
                }
                std_vector_std_pair_int_int_d_vector(skillList);
                if(v80==1){
                    goto32(user,buff,v184,guildExpBook,slof);
                    return;
                }
            }
            PacketGuard_free_PacketGuard(buff);
            return;
        case 11:
            if(CUserCharacInfo_get_charac_guildkey(user)!=0){
                CUserCharacInfo_add_guild_exp(user,guildExpBook);
            }
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 12:
            CUserCharacInfo_setCurCharacStamina(user,guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 13:
            var invenR = CUserCharacInfo_getCurCharacInvenR(user);
            var coin = CInventory_GetEventCoin(invenR);
            var allCoin = coin+guildExpBook;
            var invenW = CUserCharacInfo_getCurCharacInvenW(user);
            CInventory_SetEventCoin(invenW,allCoin);
            invenR = CUserCharacInfo_getCurCharacInvenR(user);
            coin = CInventory_GetEventCoin(invenR);
            cUserHistoryLog_EventCoinAdd(user.add(497408),coin,guildExpBook,2);
            CUser_SendUpdateItemList(user,1,0,1);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 14:
            CUser_RecoverFatigue(user,guildExpBook);
            CUser_SendFatigue(user);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 15:
            var curCharacFatigue = CUserCharacInfo_getCurCharacFatigue(user);
            var val = curCharacFatigue - guildExpBook <0 ?0:curCharacFatigue - guildExpBook
            CUserCharacInfo_setCurCharacFatigue(user,val);
            CUser_SendFatigue(user);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 16:
            if(CUserCharacInfo_get_charac_guildkey(user)!=0){
                var charcNo = CUser_get_charac_no(user,-1);
                var charcGuildKey = CUserCharacInfo_get_charac_guildkey(user);
                var serverGroup = CUser_GetServerGroup(user);
                var serverProxy = CServerProxyMgr_CGuildServerProxy_GetServerProxy(ptr(ptr(0x0940BE2C).readU32()),serverGroup);
                CGuildServerProxy_SendIncreaseGuildExp(serverProxy,charcGuildKey,charcNo,guildExpBook,1);
            }
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 17:
            CUser_gain_sfp(user, guildExpBook);
            CUser_history_log_sfp(user, -1, guildExpBook, 1);
            InterfacePacketBuf_clear(buff);
            InterfacePacketBuf_put_header(buff, 1, 32);
            InterfacePacketBuf_put_byte(buff, 1);
            InterfacePacketBuf_put_short(buff, slof);
            InterfacePacketBuf_put_byte(buff, v184);
            InterfacePacketBuf_put_int(buff, guildExpBook);
            InterfacePacketBuf_put_short(buff, 0);
            InterfacePacketBuf_put_short(buff, 0);
            InterfacePacketBuf_finalize(buff, 1);
            CUser_Send(user, buff);
            PacketGuard_free_PacketGuard(buff);
            return;
        case 18:
            SkillSlot_clear_sfp_skills(CUserCharacInfo_getCurCharacSkillW(user).toInt32(),CUserCharacInfo_get_charac_job(user),ptr(CUserCharacInfo_GetCurCharacSkillTreeIndex(user)));
            SkillSlot_set_parent(CUserCharacInfo_getCurCharacSkillW(user),user);
            var v202 = 0;
            if ( CUserCharacInfo_GetCurCharacSkillTreeIndex(user) == -1 || CUserCharacInfo_GetCurCharacSkillTreeIndex(user)!=0 ){
                v202 = 3;
            }else{
                v202 = 4;
            }
            WongWork_CSkillChanger_CSkillChanger(v172);
            WongWork_CSkillChanger_SkillInitialize(v172, user, v202, 0);
            CUser_send_skill_info(user);
            WongWork_CSkillChanger_d_CSkillChanger(v172);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 19:
            CUserCharacInfo_IncreasePowerWarPoint(user,guildExpBook);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 20:
            CUser_adjust_charac_stat(user);
            if(user.add(144369).readU32()==3){
                InterfacePacketBuf_clear(buff);
                InterfacePacketBuf_put_header(buff, 0, 2);
                InterfacePacketBuf_put_byte(buff, 1);
                InterfacePacketBuf_put_short(buff, 1);
                CUser_make_basic_info(user, buff, 1);
                InterfacePacketBuf_finalize(buff, 1);
                CUser_Send(user, buff);
            }
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 21:
            var coin = CInventory_GetCoin(CUserCharacInfo_getCurCharacInvenR(user));
            CInventory_SetCoin(CUserCharacInfo_getCurCharacInvenW(user),coin+guildExpBook);
            coin = CInventory_GetCoin(CUserCharacInfo_getCurCharacInvenR(user));
            cUserHistoryLog_CoinAdd(user.add(497408),coin,guildExpBook,3);
            CUser_SendUpdateItemList(user,1,0,1);
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 22:
            if(guildExpBook==1){
                var skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_clear_all_skills_both(skillW);
                skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_parent(skillW,user);
                CUser_givePvPSkillTree(user,0,1,3);
                var pvpGrade = CUserCharacInfo_get_pvp_grade(user);
                var pvPSkillPoint = PvPSkillTreeParameterScript_getPvPSkillPoint(
                    G_CDataManager().add(43008),CUserCharacInfo_get_charac_job(user)
                    ,CUserCharacInfo_getCurCharFirstGrowType(user)
                    ,CUserCharacInfo_getCurCharSecondGrowType(user)
                    ,pvpGrade,0
                );
                skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_remain_sp_at_index(skillW.toInt32(),pvPSkillPoint,0);
                skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_remain_sp_at_index(skillW.toInt32(),pvPSkillPoint,1);
                CUser_send_skill_info(user);
                var premiumLetheManager = CGameManager_GetPremiumLetheManager(G_CGameManager());
                if(CUser_isAffectedPremium(user.toInt32(),33)!=0){
                    if(premiumLetheManager!=0 && CUser_isAffectedPremium(user.toInt32(),33)!=0){
                        CPremiumLetheManager_InitLetheSkill(premiumLetheManager, user, 0);
                        CPremiumLetheManager_InitLetheSkill(premiumLetheManager, user, 1);
                    }
                }else if(premiumLetheManager!=0){
                    CPremiumLetheManager_UpdateBackupSkillFlag(premiumLetheManager,user,0);
                    CPremiumLetheManager_UpdateBackupSkillFlag(premiumLetheManager,user,1);
                }
            }else{
                var curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                var skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_clear_all_skills(skillW,curCharacSkillTreeIndex);
                skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_parent(skillW,user);
                var v219 = 0;
                curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                if(curCharacSkillTreeIndex==-1 || curCharacSkillTreeIndex!=0){
                    v219 = 1;
                }else {
                    v219 = 2;
                }
                CUser_givePvPSkillTree(user,0,1,v219);
                var pvpGrade = CUserCharacInfo_get_pvp_grade(user);
                var pvPSkillPoint = PvPSkillTreeParameterScript_getPvPSkillPoint(
                    G_CDataManager().add(43008),CUserCharacInfo_get_charac_job(user)
                    ,CUserCharacInfo_getCurCharFirstGrowType(user)
                    ,CUserCharacInfo_getCurCharSecondGrowType(user)
                    ,pvpGrade,0
                );
                skillW = CUserCharacInfo_getCurCharacSkillW(user);
                SkillSlot_set_remain_sp_at_index(skillW.toInt32(),pvPSkillPoint,CUserCharacInfo_GetCurCharacSkillTreeIndex(user));
                CUser_send_skill_info(user);
                var premiumLetheManager = CGameManager_GetPremiumLetheManager(G_CGameManager());
                if(CUser_isAffectedPremium(user.toInt32(),33)!=0){
                    if(premiumLetheManager!=0 && CUser_isAffectedPremium(user.toInt32(),33)!=0){
                        curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                        CPremiumLetheManager_InitLetheSkill(premiumLetheManager, user, curCharacSkillTreeIndex);
                    }
                }else if(premiumLetheManager!=0){
                    curCharacSkillTreeIndex = CUserCharacInfo_GetCurCharacSkillTreeIndex(user);
                    CPremiumLetheManager_UpdateBackupSkillFlag(premiumLetheManager,user,curCharacSkillTreeIndex);
                }
            }
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 23:
            var vlog3 = Memory.alloc(16);
            var charcName = '';
            var charcNameVal = CUserCharacInfo_getCurCharacName(stNotifyIngameADInfo);
            if(!charcNameVal.isNull())
            {
                charcName =  charcNameVal.readUtf8String(-1);
            }
            cMyTrace_cMyTrace(vlog3,Memory.allocUtf8String("void CUser::increase_status(short int)"),19466,0);
            cMyTrace_operator(vlog3.toInt32(),Memory.allocUtf8String("ONE_DAY_LETHE : BUY_SKILL_CONFIRM_TICKET, char(%s), char_no(%d), style(%d)"),Memory.allocUtf8String(charcName));
            var premiumLetheManager = CGameManager_GetPremiumLetheManager(G_CGameManager());
            if(premiumLetheManager!=0){
                CPremiumLetheManager_ConfirmSkillReq(premiumLetheManager,user);
            }
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        case 25:
            var ivenW = CUserCharacInfo_getCurCharacInvenW(user);
            AvatarCoin_Add(ivenW.add(1624),guildExpBook);
            AvatarCoin_SaveToDB(user);
            AvatarCoin_SendSyncPacket(user);
            AvatarCoin_HistoryLog_AddLog(user,ptr(guildExpBook));
            goto32(user,buff,v184,guildExpBook,slof);
            return;
        default:
            goto32(user,buff,v184,guildExpBook,slof);
            return;
    }
}

function goto32(user,buff,v184,guildExpBook,slof){
    InterfacePacketBuf_clear(buff);
    InterfacePacketBuf_put_header(buff, 1, 32);
    InterfacePacketBuf_put_byte(buff, 1);
    InterfacePacketBuf_put_short(buff, slof);
    InterfacePacketBuf_put_byte(buff, v184);
    InterfacePacketBuf_put_int(buff, guildExpBook);
    InterfacePacketBuf_put_short(buff, 0);
    InterfacePacketBuf_put_short(buff, 0);
    InterfacePacketBuf_finalize(buff, 1);
    CUser_Send(user, buff);
    if(user.add(144369*4).readU32() == 5){
        var party = CUser_GetParty(user);
        if(party!=0){
            InterfacePacketBuf_clear(buff);
            InterfacePacketBuf_put_header(buff, 0, 2);
            InterfacePacketBuf_put_byte(buff, 1);
            InterfacePacketBuf_put_short(buff, 1);
            CUser_make_basic_info(user, buff, 1);
            InterfacePacketBuf_finalize(buff, 1);
            CParty_send_to_party(party, buff);
        }
    }else if(user.add(144369*4).readU32() == 8){
        var warRoom = CUser_GetWarRoom(user);
        if(warRoom!=0){
            InterfacePacketBuf_clear(buff);
            InterfacePacketBuf_put_header(buff, 0, 2);
            InterfacePacketBuf_put_byte(buff, 1);
            InterfacePacketBuf_put_short(buff, 1);
            CUser_make_basic_info(user, buff, 1);
            InterfacePacketBuf_finalize(buff, 1);
            WarRoom_SendToRoom(warRoom, buff);
        }
    }
    PacketGuard_free_PacketGuard(buff);
}

//幸运点上下限
var MAX_LUCK_POINT = 100;
var MIN_LUCK_POINT = 1;

//设置角色幸运点
function api_CUserCharacInfo_SetCurCharacLuckPoint(user, new_luck_point)
{
    if(new_luck_point > MAX_LUCK_POINT)
        new_luck_point = MAX_LUCK_POINT;
    else if(new_luck_point < MIN_LUCK_POINT)
        new_luck_point = MIN_LUCK_POINT;
    CUserCharacInfo_enableSaveCharacStat(user);
    CUserCharacInfo_SetCurCharacLuckPoint(user, new_luck_point);
    return new_luck_point;
}

//使用命运硬币后, 可以改变自身幸运点
//查询角色当前幸运点GM命令: //show lp
//当前角色幸运点拉满GM命令: //max lp
function use_ftcoin_change_luck_point(user)
{
    //抛命运硬币
    var rand = get_random_int(0, 100);

    //当前幸运点数
    var new_luck_point = null;

    if(rand == 0)
    {
        //1%几率将玩家幸运点充满(最大值10W)
        new_luck_point = MAX_LUCK_POINT;
    }
    else if(rand == 1)
    {
        //1%几率将玩家幸运点耗尽
        new_luck_point = MIN_LUCK_POINT;
    }
    else if(rand < 51)
    {
        //49%几率当前幸运点增加20%
        new_luck_point = Math.floor(CUserCharacInfo_GetCurCharacLuckPoint(user) * 1.2);
    }
    else
    {
        //49%几率当前幸运点降低20%
        new_luck_point = Math.floor(CUserCharacInfo_GetCurCharacLuckPoint(user) * 0.8);
    }
    //修改角色幸运点
    new_luck_point = api_CUserCharacInfo_SetCurCharacLuckPoint(user, new_luck_point);
    //通知客户端当前角色幸运点已改变
    api_CUser_SendNotiPacketMessage(user, '命运已被改变, 当前幸运点数: ' + new_luck_point, 0);
}

//使用角色幸运值加成装备爆率
function enable_drop_use_luck_piont()
{
    //由于roll点爆装函数拿不到user, 在杀怪和翻牌函数入口保存当前正在处理的user
    var cur_luck_user = null;
    //DisPatcher_DieMob::dispatch_sig
    Interceptor.attach(ptr(0x81EB0C4),
    {
        onEnter: function (args)
        {
            cur_luck_user = args[1];
        },
        onLeave: function (retval)
        {
            cur_luck_user = null;
        }
    });

    //CParty::SetPlayResult
    Interceptor.attach(ptr(0x85B2412),
    {
        onEnter: function (args)
        {
            cur_luck_user = args[1];
        },
        onLeave: function (retval)
        {
            cur_luck_user = null;
        }
    });

    //修改决定出货品质(rarity)的函数 使出货率享受角色幸运值加成
    //CLuckPoint::GetItemRarity
    var CLuckPoint_GetItemRarity_ptr = ptr(0x8550BE4);
    var CLuckPoint_GetItemRarity = new NativeFunction(CLuckPoint_GetItemRarity_ptr,  'int', ['pointer', 'pointer', 'int', 'int'], {"abi":"sysv"});
    Interceptor.replace(CLuckPoint_GetItemRarity_ptr, new NativeCallback(function (a1, a2, roll, a4)
    {
        //使用角色幸运值roll点代替纯随机roll点
        if(cur_luck_user)
        {
            //获取当前角色幸运值
            var luck_point = CUserCharacInfo_GetCurCharacLuckPoint(cur_luck_user);

            //roll点范围1-100W, roll点越大, 出货率越高
            //角色幸运值范围1-10W
            //使用角色 [当前幸运值*10] 作为roll点下限, 幸运值越高, roll点越大
            roll = get_random_int(luck_point*10, 1000000);
        }
        //执行原始计算爆装品质函数
        var rarity = CLuckPoint_GetItemRarity(a1, a2, roll, a4);
        //调整角色幸运值
        if(cur_luck_user)
        {
            var rate = 1.0;

            //出货粉装以上, 降低角色幸运值
            if(rarity >= 3)
            {
                //出货品质越高, 幸运值下降约快
                rate = 1 - (rarity*0.01);
            }
            else
            {
                //未出货时, 提升幸运值
                rate = 1.01;
            }
            //设置新的幸运值
            var new_luck_point = Math.floor(CUserCharacInfo_GetCurCharacLuckPoint(cur_luck_user) * rate);
            api_CUserCharacInfo_SetCurCharacLuckPoint(cur_luck_user, new_luck_point);
        }
        return rarity;
     }, 'int', ['pointer', 'pointer', 'int', 'int']));
}

Interceptor.attach(ptr(0x080FC850),{
onEnter: function (args) {
this.equiPos = args[2].add(27).readU16();
this.user = args[1];
},
onLeave: function (retval){
CUser_SendUpdateItemList(this.user, 1, 0, this.equiPos);
}
});


function processing_data(item_id, user, award_item_id, award_item_count, count) {

const itemName = api_CItem_GetItemName(item_id);
if (award_item_id == 0 && count != 0) {

api_GameWorld_SendNotiPacketMessage("恭喜玩家<" +
"" + api_CUserCharacInfo_getCurCharacName(user) + "" +
">在地下城中获得了[" + itemName + "]，奖励点券：☆" + count + "☆", 14);

api_recharge_cash_cera(user, count);
}

if (award_item_id != 0 && count == 0) {

api_GameWorld_SendNotiPacketMessage("恭喜玩家<" +
"" + api_CUserCharacInfo_getCurCharacName(user) + "" +
">在地下城中获得了[" + itemName + "]，奖励：☆" + api_CItem_GetItemName(award_item_id) + award_item_count+"个☆", 14);

api_CUser_AddItem(user, award_item_id, award_item_count);
}

if (award_item_id != 0 && count != 0) {

api_GameWorld_SendNotiPacketMessage("恭喜玩家<" +
"" + api_CUserCharacInfo_getCurCharacName(user) + "" +
">在地下城中获得了[" + itemName + "]，奖励：☆" + api_CItem_GetItemName(award_item_id) + "☆，奖励点券：" + count, 14);

api_CUser_AddItem(user, award_item_id, award_item_count);

api_recharge_cash_cera(user, count);
//api_recharge_cash_cera_point(user, count);
}


CUser_send_itemspace(user, INVENTORY_TYPE_ITEM);

}





function start()
{
    log('++++++++++++++++++++ frida init ++++++++++++++++++++');
    //加载本地配置文件
    load_config('frida_config.json');


    //挂接消息分发线程 执行需要在主线程运行的代码
    hook_TimerDispatcher_dispatch();

    //初始化数据库
    api_scheduleOnMainThread(init_db, null);

//setMaxCAccountCargoSolt(120); // 设置账号金库格子数量
    //开启怪物攻城活动
//    api_scheduleOnMainThread(start_event_villageattack, null);
    //开启抽取幸运在线玩家活动
 //   start_event_lucky_online_user();
    //角色登入登出处理
   hook_user_inout_game_world();

    //每日首次登录处理
    //hook_user_first_login();

    //处理GM信息
hook_gm_command();
    //魔法封印自动解封
    //auto_unseal_random_option_equipment();
    //允许赛利亚房间的人互相可见
    //share_seria_room();

    //所有账号角色开启GM权限
  //  hook_check_gm();

    //解除每日创建角色数量限制
  //  disable_check_create_character_limit();

    hook_history_log();
 //   fix_TOD(true); // 修复绝望之塔
    // 在线奖励
   // enable_online_reward();
    // 修复时装镶嵌
  //  fix_use_emblem();


  //  setMaxUpGrade(70); // 设置等级

  //  cancel_epic_ok(); // 取消史诗确认框
  //  enable_createCreator(); // 开启创建缔造者

  //startHellParty(); // 开启深渊模式

    // test();
    //Interceptor.flush();
}

//延迟加载插件
function awake()
{
    //Hook check_argv
    Interceptor.attach(ptr(0x829EA5A), {

        onEnter: function (args) {
        },
        onLeave: function (retval) {
            //等待check_argv函数执行结束 再加载插件
            start();
        }
    });
}

rpc.exports = {
    init: function (stage, parameters) {
        console.log('[init] stage=' + stage + ', parameters=' + JSON.stringify(parameters));
        if(stage == 'early')
        {
            //首次加载插件 等待服务器初始化后再加载
            awake();
        }
        else
        {
            //热重载:  直接加载
            start();
        }

    },
    dispose: function () {
        uninit_db()
        console.log('[dispose]');
    }
};
