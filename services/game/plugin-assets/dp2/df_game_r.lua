local dp = _DP
local dpx = _DPX
local log = dp.log

log.info("插件运行中")
-- talk is cheap, show me the code !
-- 获得运行参数
dpx.opt()
-- 允许创建缔造者
dpx.enable_creator()
-- 绝望之塔通关后仍可继续挑战(需门票) 
dpx.set_unlimit_towerofdespair()
-- 设置物品免确认(异界装备不影响)
dpx.disable_item_routing()
-- 禁用安全机制, 解除100级及以上的限制（注意pvf中也要做一些修改）
dpx.disable_security_protection()
-- 解除交易限额, 已达上限的第二天生效
dpx.disable_trade_limit()
-- 设置使用拍卖行的最低等级
dpx.set_auction_min_level(10)
-- 修复拍卖行消耗品上架, 参数是最大总价, 建议值2E
dpx.fix_auction_regist_item(200000000)
-- 关闭NPC回购系统【如果要开启，把下面一行代码开头的-- 删除即可，缔造者是无法开启回购系统的！】
-- dpx.disable_redeem_item()
-- 跨界石与完成券
local game = require("df.game")
local item_handler = { }


-- 以下是跨界石代码 只要在背包装备栏的第一格，无论什么品级都可以被转移（不用就删掉这一大段）!
item_handler[2021458801] = function(user, item_id)
    if not user.MoveToAccCargo(game.ItemSpace.INVENTORY, 9) then
        dpx.item.add(user.ptr, item_id)
    end
end
local my_useitem2 = function(_user, item_id)
    local user = game.fac.user(_user)
    local handler = item_handler[item_id]
    if handler then
        handler(user, item_id)
        log.info(string.format("[useitem] acc: %d chr: %d item_id: %d", user.GetAccId(), user.GetCharacNo(), item_id))
    end
end
-- 以上是跨界石代码 只要在背包装备栏的第一格，无论什么品级都可以被转移（不用就删掉这一大段）!

-- 主线任务完成
item_handler[2021458802] = function(user, item_id)
    local quest = dpx.quest
    local lst = quest.all(user.ptr)
    local chr_level = user.GetCharacLevel()
    for i, v in ipairs(lst) do
        local id = v
        local info = quest.info(user.ptr, id)
        if info then
            if not info.is_cleared and info.type == game.QuestType.epic and info.min_level <=  chr_level  then
               quest.clear(user.ptr, v)
           end
        end
    end
	quest.update(user.ptr)
end
local my_useitem2 = function(_user, item_id)
    local user = game.fac.user(_user)
	local handler = item_handler[item_id]
	if handler then
        handler(user, item_id)
		log.info(string.format("[useitem] acc: %d chr: %d item_id: %d", user.GetAccId(), user.GetCharacNo(), item_id))
    end
end
dpx.hook(game.HookType.UseItem2, my_useitem2)

-- 支线/普通任务完成（请转职觉醒之后在用，否者转职觉醒任务会被清理！）
item_handler[2021458803] = function(user, item_id)
    local quest = dpx.quest
    local lst = quest.all(user.ptr)
    local chr_level = user.GetCharacLevel()
    for i, v in ipairs(lst) do
        local id = v
        local info = quest.info(user.ptr, id)
        if info then
            if not info.is_cleared and info.type == game.QuestType.common_unique and info.min_level <=  chr_level  then
                quest.clear(user.ptr, v)
            end
        end
    end
    quest.update(user.ptr)
end


-- 每日任务完成
item_handler[2021458808] = function(user, item_id)
    local quest = dpx.quest
    local lst = quest.all(user.ptr)
    local chr_level = user.GetCharacLevel()
    for i, v in ipairs(lst) do
        local id = v
        local info = quest.info(user.ptr, id)
        if info then
            if not info.is_cleared and info.type == game.QuestType.daily and info.min_level <=  chr_level  then
                quest.clear(user.ptr, v)
            end
        end
    end
    quest.update(user.ptr)
end
-- 成就任务完成
item_handler[2021458809] = function(user, item_id)
    local quest = dpx.quest
    local lst = quest.all(user.ptr)
    local chr_level = user.GetCharacLevel()
    for i, v in ipairs(lst) do
        local id = v
        local info = quest.info(user.ptr, id)
        if info then
            if not info.is_cleared and info.type == game.QuestType.achievement and info.min_level <=  chr_level  then
                quest.clear(user.ptr, v)
            end
        end
    end
    quest.update(user.ptr)
end

-- 以下是女鬼剑转换券代码 将任意一级职业转换为女鬼剑（不用就删掉这一大段）!
item_handler[2021458807] = function(user, item_id)
    local level = user.GetCharacLevel()
    if level==1 then
        log.info(string.format("will covert at swordman [useitem] acc: %d chr: %d", user.GetAccId(), user.GetCharacNo()))
        os.execute(string.format("sh /dp2/script/covert_at_swordman.sh %d",user.GetCharacNo()));
    end
end
local my_useitem2 = function(_user, item_id)
    local user = game.fac.user(_user)
    local handler = item_handler[item_id]
    if handler then
        handler(user, item_id)
        log.info(string.format("[useitem] acc: %d chr: %d item_id: %d", user.GetAccId(), user.GetCharacNo(), item_id))
    end
end
-- 以下是女鬼剑转换券代码 将任意一级职业转换为女鬼剑（不用就删掉这一大段）!

-- 以下是宠物删除券代码 删除宠物前2栏（不用就删掉这一大段）!
item_handler[2021458806] = function(user, item_id)
    for i=0,13,1 do
        local info = dpx.item.info(user.ptr, 7, i)
        if info then
            log.info(string.format("will delete [iteminfo] id: %d count: %d name: %s attach: %d", info.id, info.count, info.name, info.attach_type))
            os.execute(string.format("sh /dp2/script/delete_creature_item.sh %d %d %d",user.GetCharacNo(),i,info.id));
        end
    end
end
local my_useitem2 = function(_user, item_id)
    local user = game.fac.user(_user)
    local handler = item_handler[item_id]
    if handler then
        handler(user, item_id)
        log.info(string.format("[useitem] acc: %d chr: %d item_id: %d", user.GetAccId(), user.GetCharacNo(), item_id))
    end
end
-- 以上是宠物删除券代码 删除宠物前2栏（不用就删掉这一大段）!


-- 以下是异界重置券代码 上面是E2重置，下面是E3重置（不用就删掉这一大段）!
item_handler[2021458804] = function(user, item_id)
    user.ResetDimensionInout(0)
    user.ResetDimensionInout(1)
    user.ResetDimensionInout(2)
end
item_handler[2021458805] = function(user, item_id)
    user.ResetDimensionInout(3)
    user.ResetDimensionInout(4)
    user.ResetDimensionInout(5)
end
local my_useitem2 = function(_user, item_id)
    local user = game.fac.user(_user)
    local handler = item_handler[item_id]
    if handler then
        handler(user, item_id)
        log.info(string.format("[useitem] acc: %d chr: %d item_id: %d", user.GetAccId(), user.GetCharacNo(), item_id))
    end
end
-- 以上是异界重置券代码 上面是E2重置，下面是E3重置（不用就删掉这一大段）!


-- 以下是修复绝望之塔提示金币异常代码（不用就删掉这一大段）!
local function MyUseAncientDungeonItems(next, _party, _dungeon, _item)
    local party = game.fac.party(_party)
    local dungeon= game.fac.dungeon(_dungeon)
    local dungeon_index = dungeon.GetIndex()
    if dungeon_index >= 11008 and dungeon_index <= 11107 then
        return true
    end
    return next()
end
-- 以下是修复绝望之塔提示金币异常代码（不用就删掉这一大段）!



