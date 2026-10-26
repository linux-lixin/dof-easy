local _M = {}

local pairs = pairs
local tonumber = tonumber

local sym = _DP.sym
local ffi = require("ffi")

local function add_symbols()
    local dpx = _DPX
    local symbols = require("df.game.symbols")

    for k, v in pairs(symbols) do
        local va = tonumber(k);

        if va then
            local addr = dpx.reloc(va)
            local name = v;

            sym.add(name, addr)
        end
    end
end

add_symbols()

ffi.cdef [[
    struct PacketBuf;
    struct MSG_BASE;
    struct Inven_Item;
    struct DisPatcher_AnyOne;

    struct CUser;
    struct CParty;
    struct CDungeon;
    struct CBattle_Field;
]]

ffi.cdef [[
    int PacketBuf_Clear(PacketBuf* obj);
    int PacketBuf_GetIndex(PacketBuf* obj);
    void PacketBuf_SetIndex(PacketBuf* obj, int pos);
    char* PacketBuf_GetPacket(PacketBuf* obj, int offset);

    int PacketBuf_GetInt8(PacketBuf* obj, char* v);
    int PacketBuf_GetInt16(PacketBuf* obj, short* v);
    int PacketBuf_GetInt32(PacketBuf* obj, int* v);
    int PacketBuf_PutInt8(PacketBuf* obj, int v);
    int PacketBuf_PutInt16(PacketBuf* obj, int v);
    int PacketBuf_PutInt32(PacketBuf* obj, int v);
    int PacketBuf_PutHeader(PacketBuf* obj, int type, int id);
    int PacketBuf_Finalize(PacketBuf* obj, int v);
    int PacketBuf_IsFinalized(PacketBuf* obj);
    ]]

sym.alias("PacketBuf_Clear", "_ZN9PacketBuf5clearEv")
sym.alias("PacketBuf_GetIndex", "_ZN9PacketBuf9get_indexEv")
sym.alias("PacketBuf_SetIndex", "_ZN9PacketBuf9set_indexEi")
sym.alias("PacketBuf_GetPacket", "_ZN9PacketBuf10get_packetEi")
sym.alias("PacketBuf_GetInt8", "_ZN9PacketBuf8get_byteERc")
sym.alias("PacketBuf_GetInt16", "_ZN9PacketBuf9get_shortERs")
sym.alias("PacketBuf_GetInt32", "_ZN9PacketBuf7get_intERi")
sym.alias("PacketBuf_PutInt8", "_ZN9PacketBuf8put_byteEi")
sym.alias("PacketBuf_PutInt16", "_ZN9PacketBuf9put_shortEi")
sym.alias("PacketBuf_PutInt32", "_ZN9PacketBuf7put_intEi")
sym.alias("PacketBuf_PutHeader", "_ZN9PacketBuf10put_headerEii")
sym.alias("PacketBuf_Finalize", "_ZN9PacketBuf8finalizeEb")
sym.alias("PacketBuf_IsFinalized", "_ZNK9PacketBuf13is_finallizedEv")

local buf_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('PacketBuf*', ptr)

    obj.cptr = cptr

    obj.Clear = function()
        return ffi.C.PacketBuf_Clear(cptr)
    end

    obj.GetIndex = function()
        return ffi.C.PacketBuf_GetIndex(cptr)
    end

    obj.SetIndex = function(pos)
        ffi.C.PacketBuf_SetIndex(cptr, pos)
    end

    obj.GetPacket = function(offset)
        return ffi.C.PacketBuf_GetPacket(cptr, offset or 0)
    end

    obj.GetInt8 = function()
        local v = ffi.new('char[1]')
        local n = ffi.C.PacketBuf_GetInt8(cptr, v)
        if n ~= 1 then return nil end
        return v[0]
    end

    obj.GetInt16 = function()
        local v = ffi.new('short[1]')
        local n = ffi.C.PacketBuf_GetInt16(cptr, v)
        if n ~= 1 then return nil end
        return v[0]
    end

    obj.GetInt32 = function()
        local v = ffi.new('int[1]')
        local n = ffi.C.PacketBuf_GetInt32(cptr, v)
        if n ~= 1 then return nil end
        return v[0]
    end

    obj.PutHeader = function(type, id)
        return ffi.C.PacketBuf_PutHeader(cptr, type, id)
    end

    obj.PutInt8 = function(v)
        return ffi.C.PacketBuf_PutInt8(cptr, v)
    end

    obj.PutInt16 = function(v)
        return ffi.C.PacketBuf_PutInt16(cptr, v)
    end

    obj.PutInt32 = function(v)
        return ffi.C.PacketBuf_PutInt32(cptr, v)
    end

    obj.Finalize = function(bUnknown)
        return ffi.C.PacketBuf_Finalize(cptr, bUnknown and 1 or 0)
    end

    obj.IsFinalized = function()
        return (ffi.C.PacketBuf_IsFinalized(cptr) == 1);
    end

    return obj
end

local msgbase_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('MSG_BASE*', ptr)

    obj.cptr = cptr

    return obj
end

ffi.cdef [[
    int DisPatcher_CreateCharac_read(DisPatcher_AnyOne* obj, PacketBuf* buf, MSG_BASE* msg);
    ]]

sym.alias('DisPatcher_CreateCharac_read', '_ZN23DisPatcher_CreateCharac4readER9PacketBufR8MSG_BASE');

local DisPatcher_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('DisPatcher_AnyOne*', ptr)

    obj.cptr = cptr

    obj.CreateCharacRead = function(buf, msg)
        return ffi.C.DisPatcher_CreateCharac_read(cptr, buf, msg)
    end

    return obj
end

ffi.cdef [[
    // impl in dp2
    void CUser_ResetDimensionInout(CUser* user, int idx);
    int CUser_MoveToAccCargo(CUser *user, int space, int slot);
    int CUser_ClearAchievement(CUser* user);

    // impl in game
    CParty* CUser_GetParty(CUser* user);
    int CUser_GetAccId(CUser* user);
    int CUser_GetCharacNo(CUser* user);
    const char* CUser_GetCharacName(CUser* user);
    int CUser_GetCharacLevel(CUser* user);
    int CUser_GetCharacJob(CUser* user);
    int CUser_GetCharacGrowType(CUser* user);
    int CUser_DisConnSig(CUser* user, int type, int is, int err);
    int CUser_CheckInBlueMarble(CUser*);
]]

sym.alias('CUser_GetParty', '_ZN5CUser8GetPartyEv')
sym.alias('CUser_GetAccId', '_ZNK5CUser10get_acc_idEv')
sym.alias('CUser_GetCharacNo', '_ZNK15CUserCharacInfo14getCurCharacNoEv')
sym.alias('CUser_GetCharacName', '_ZNK15CUserCharacInfo16getCurCharacNameEv')
sym.alias("CUser_GetCharacLevel", "_ZNK15CUserCharacInfo16get_charac_levelEv")
sym.alias("CUser_GetCharacJob", "_ZNK15CUserCharacInfo14get_charac_jobEv")
sym.alias("CUser_GetCharacGrowType", "_ZNK15CUserCharacInfo20getCurCharacGrowTypeEv")
sym.alias("CUser_DisConnSig", "_ZN5CUser10DisConnSigE11DISCONN_SIGbi")
sym.alias("CUser_CheckInBlueMarble", "_ZN5CUser17checkInBlueMarbleEv")

local user_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('CUser*', ptr)

    obj.ptr = ptr
    obj.cptr = cptr

    obj.GetParty = function()
        local p = ffi.C.CUser_GetParty(cptr)

        if p then
            return _M.fac.party(p)
        end

        return nil
    end

    obj.GetAccId = function()
        return ffi.C.CUser_GetAccId(cptr)
    end

    obj.GetCharacNo = function()
        return ffi.C.CUser_GetCharacNo(cptr)
    end

    obj.GetCharacJob = function()
        return ffi.C.CUser_GetCharacJob(cptr)
    end

    obj.GetCharacName = function()
        local str = ffi.C.CUser_GetCharacName(cptr)

        return ffi.string(str)
    end

    obj.GetCharacLevel = function()
        return ffi.C.CUser_GetCharacLevel(cptr)
    end

    obj.GetCharacGrowType = function()
        return ffi.C.CUser_GetCharacGrowType(cptr)
    end

    obj.Kick = function(src, p2, p3)
        return ffi.C.CUser_DisConnSig(cptr, src or 0, p2 or 0, p3 or 0)
    end

    obj.DisConn = function(err)
        return obj.Kick(10, 1, err or 1)
    end

    obj.CheckInBlueMarble = function()
        return (ffi.C.CUser_CheckInBlueMarble(cptr) == 1)
    end

    obj.ResetDimensionInout = function(idx)
        ffi.C.CUser_ResetDimensionInout(cptr, idx)
    end

    obj.MoveToAccCargo = function(space, slot)
        return (ffi.C.CUser_MoveToAccCargo(cptr, space, slot) == 0)
    end

    obj.ClearAchievement = function()
        return (ffi.C.CUser_ClearAchievement(cptr) == 0)
    end

    return obj
end

ffi.cdef [[
    // impl in dp2
    int CParty_Get282(CParty* party);
    CDungeon* CParty_GetDungeon(CParty* party);
    CBattle_Field* CParty_GetBattleField(CParty* party);

    // impl in game
    int CParty_ClearDungeon(CParty* party);
    int CParty_GetMapPlayingTime(CParty* party);
    int CParty_UseAncientDungeonItems(CParty* party, CDungeon* dungeon, Inven_Item* item, int*);
    int CParty_CheckValidUser(CParty* party, int pos);
    CUser* CParty_GetUser(CParty* party, int pos);
    int CParty_GetState(CParty* party);
    int CParty_GetDungeonClearState(CParty* party);
    int CParty_CheckBossRoom(CParty* party);
    int CParty_GetMemberCount(CParty* party);
    int CParty_GetMemberCount(CParty* party);
    int CParty_IsStartRoom(CParty* party);
    ]]

sym.alias("CParty_ClearDungeon", "_ZN6CParty12ClearDungeonEv")
sym.alias("CParty_GetMapPlayingTime", "_ZN6CParty17GetMapPlayingTimeEv")
sym.alias("CParty_UseAncientDungeonItems", "_ZN6CParty22UseAncientDungeonItemsEPK8CDungeonP10Inven_ItemPi")
sym.alias("CParty_CheckValidUser", "_ZN6CParty14checkValidUserEi")
sym.alias("CParty_GetUser", "_ZN6CParty8get_userEi")
sym.alias("CParty_GetState", "_ZN6CParty9get_stateEv")
sym.alias("CParty_GetDungeonClearState", "_ZN6CParty23get_dungeon_clear_stateEv")
sym.alias("CParty_CheckBossRoom", "_ZN6CParty13checkBossRoomEv")
sym.alias("CParty_GetMemberCount", "_ZN6CParty16get_member_countEv")
sym.alias("CParty_IsStartRoom", "_ZN6CParty14checkStartRoomEv")

local party_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('CParty*', ptr)

    obj.cptr = cptr

    obj.ClearDungeon = function()
        return ffi.C.CParty_ClearDungeon(cptr)
    end

    obj.GetMapPlayingTime = function()
        return ffi.C.CParty_GetMapPlayingTime(cptr)
    end

    obj.UseAncientDungeonItems = function(dungeon, item, p4)
        return ffi.C.CParty_UseAncientDungeonItems(cptr, dungeon, item, p4)
    end

    obj.Get282 = function()
        return ffi.C.CParty_Get282(cptr)
    end

    obj.GetState = function()
        return ffi.C.CParty_GetState(cptr)
    end

    obj.CheckValidUser = function(pos)
        return (ffi.C.CParty_CheckValidUser(cptr, pos) == 1)
    end

    obj.GetUser = function(pos)
        local p = ffi.C.CParty_GetUser(cptr, pos)

        if p then
            return _M.fac.user(p)
        end

        return nil
    end

    obj.GetDungeon = function()
        local p = ffi.C.CParty_GetDungeon(cptr)

        if p then
            return _M.fac.dungeon(p)
        end

        return nil
    end

    obj.GetDungeonClearState = function()
        return ffi.C.CParty_GetDungeonClearState(cptr)
    end

    obj.GetBattleField = function()
        local p = ffi.C.CParty_GetBattleField(cptr)

        return _M.fac.battle_field(p)
    end

    obj.CheckBossRoom = function()
        return (ffi.C.CParty_CheckBossRoom(cptr) == 1)
    end

    obj.GetMemberCount = function()
        return ffi.C.CParty_GetMemberCount(cptr)
    end

    obj.ForEachMember = function(func)
        for i = 1, 4 do
            local pos = i - 1
            if obj.CheckValidUser(pos) then
                local v = func(obj.GetUser(pos))
                if not v then
                    return 0
                end
            end
        end

        return 1
    end

    obj.IsStartRoom = function()
        return (ffi.C.CParty_IsStartRoom(cptr) == 1)
    end

    return obj
end

ffi.cdef [[
    int CBattle_Field_IsQuestMaze(CBattle_Field* ptr);
    int CBattle_Field_GetDungeonDiff(CBattle_Field* ptr);
    int CBattle_Field_IsEnableHellDungeon(CBattle_Field* ptr);
    int CBattleField_IsHellParty(CBattle_Field* ptr);
]]

sym.alias("CBattle_Field_IsQuestMaze", "_ZN13CBattle_Field11isQuestMazeEv")
sym.alias("CBattle_Field_GetDungeonDiff", "_ZN13CBattle_Field16get_dungeon_diffEv")
sym.alias("CBattle_Field_IsEnableHellDungeon", "_ZN13CBattle_Field19IsEnableHellDungeonEv")

local battle_field_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('CBattle_Field*', ptr)

    obj.IsQuestMaze = function()
        return (ffi.C.CBattle_Field_IsQuestMaze(cptr) == 1)
    end

    obj.GetDungeonDiff = function()
        return ffi.C.CBattle_Field_GetDungeonDiff(cptr)
    end

    obj.IsEnableHellDungeon = function()
        return (ffi.C.CBattle_Field_IsEnableHellDungeon(cptr) == 1)
    end

    obj.IsHellParty = function()
        return (ffi.C.CBattleField_IsHellParty(cptr) == 1)
    end

    return obj
end

ffi.cdef [[
    // impl at dp2
    int CDungeon_IsEventDungeon(CDungeon* dungeon);
    int CDungeon_IsAncientDungeon(CDungeon* dungeon);

    // impl at game
    int CDungeon_GetIndex(CDungeon* dungeon);
    int CDungeon_GetMinLevel(CDungeon* dungeon);
    int CDungeon_GetStandardLevel(CDungeon* dungeon);
    int CDungeon_GetKind(CDungeon* dungeon);
    const char* CDungeon_GetName(CDungeon* dungeon);
    int CDungeon_IsRiskDungeon(CDungeon* dungeon);
    int CDungeon_GetDimensionPossible(CDungeon* dungeon);
    ]]

sym.alias("CDungeon_GetIndex", "_ZNK8CDungeon9get_indexEv")
sym.alias("CDungeon_GetMinLevel", "_ZNK8CDungeon13get_min_levelEv")
sym.alias("CDungeon_GetStandardLevel", "_ZNK8CDungeon18get_standard_levelEv")
sym.alias("CDungeon_GetKind", "_ZNK8CDungeon14getDungeonKindEv")
sym.alias("CDungeon_GetName", "_ZNK8CDungeon14GetDungeonNameEv")
sym.alias("CDungeon_IsRiskDungeon", "_ZNK8CDungeon13isRiskDungeonEv")
sym.alias("CDungeon_GetDimensionPossible", "_ZNK8CDungeon22get_dimension_possibleEv")

local dungeon_fac = function(ptr)
    local obj = {}
    local cptr = ffi.cast('CDungeon*', ptr)

    obj.cptr = cptr

    obj.GetName = function()
        local str = ffi.C.CDungeon_GetName(cptr)

        return ffi.string(str)
    end

    obj.GetKind = function()
        return ffi.C.CDungeon_GetKind(cptr)
    end

    obj.GetIndex = function()
        return ffi.C.CDungeon_GetIndex(cptr)
    end

    obj.GetMinLevel = function()
        return ffi.C.CDungeon_GetMinLevel(cptr)
    end

    obj.GetStandardLevel = function()
        return ffi.C.CDungeon_GetStandardLevel(cptr)
    end

    obj.IsRiskDungeon = function()
        return (ffi.C.CDungeon_IsRiskDungeon(cptr) == 1)
    end

    obj.IsEventDungeon = function()
        return (ffi.C.CDungeon_IsEventDungeon(cptr) == 1)
    end

    -- 远古
    obj.IsAncientDungeon = function()
        return (ffi.C.CDungeon_IsAncientDungeon(cptr) == 1)
    end

    -- 异界
    obj.IsDimensionDungeon = function()
        return (ffi.C.CDungeon_GetDimensionPossible(cptr) > 0)
    end

    return obj
end

ffi.cdef [[
    int sleep_ext(int, int);
    const char* GetPacketName(int type, int id);
    ]]

sym.alias("sleep_ext", "_Z9sleep_extii")
sym.alias("GetPacketName", "_Z13GetPacketName16ENUM_PACKETCLASSt")

_M.sleep_ext = function(sec, usec)
    ffi.C.sleep_ext(sec, usec)
end

_M.GetPacketName = function(type, id)
    local str = ffi.C.GetPacketName(type, id)
    return ffi.string(str)
end

_M.fac = {
    buf = buf_fac,
    msgbase = msgbase_fac,
    dispatcher = DisPatcher_fac,

    user = user_fac,
    party = party_fac,
    dungeon = dungeon_fac,
    battle_field = battle_field_fac,
}

local HookType = {
    CParty_UseAncientDungeonItems = 1,  -- function(next, _party, _dungeon, _item)
    -- DisPatcher_CreateCharac_read = 2,   -- function(_disp, _buf, _msg)
    -- CUser_ResetCurCharac = 3,           -- function(_user)
    CParty_ClearDungeon = 4,            -- function(next, _party, pass_time)
    -- CParty_OnMapMove = 5,               -- function(_user)
    -- RandomOption_ChangePrice = 6,       -- function(next, rarity, usable_level) return next(rarity, usable_level) end
    -- RandomOption_ChangeOption = 7,      -- function(next, rarity, usable_level) return next(rarity, usable_level) end
    CUser_SaveTown = 8,                 -- function(_user, pre_town_id, post_town_id) return post_town_id end
    CParty_DropItem = 9,                -- function(_party, monster_id) return true end
    CParty_GiveUpGame = 10,             -- function(next, _party, _user) next() end
    -- function next(rarity)
    -- item fields: {id, name, grade, rarity, usable_level}
    RandomOption_Regen = 11,            -- function(next, _user, item1, item2) return next(2) end -- PS: return is item_id
    CItem_IsRedeemItem = 12,            -- function(next, item_id) return next() end -- PS: return is bool
    Open_Dungeon = 13,                  -- function(next, dgn_idx) return next(dgn_idx) end -- PS: return is bool
    Reach_GameWord = 15,                -- function(_user) return end
    Leave_GameWord = 16,                -- function(_user) return end
    UseItem1 = 17,                      -- function(_user, item_id) return end
    UseItem2 = 18,                      -- function(_user, item_id) return end

    Packet_Send = 100,                   -- function(next, _user, _buf, bt) return next() end
    Packet_Recv = 101,                   -- function(next, _user, cls, id, src, len) return next(cls, id, src, len) end
    Packet_Update = 102,                 -- function(next, _user, header) return next() end
}

_M.HookType = HookType

local ItemSpace = {
    INVENTORY = 0,
    AVATAR = 1,
    CARGO = 2,
    EQUIPPED = 3,
    TRADE = 4,
    PRIVATE_STORE = 5,
    MAIL = 6,
    CREATURE = 7,
    COMPOUND_AVATAR = 8,
    USE_EMBLEM = 9,
    AVATAR_CONVERT = 10,
    ACCOUNT_CARGO = 12,
}

_M.ItemSpace = ItemSpace

local QuestType = {
    epic = 0,
    training = 1,
    achievement = 2,
    daily = 3,
    normaly_repeat = 4,
    common_unique = 5,
    special = 6,
    title = 7,
    urgent = 8,
}

local AttachType = {
    free = 0,
    trade = 1,
    trade_delete = 2,
    sealing = 3,
    sealing_trade = 4,
    account = 5,
}

_M.QuestType = QuestType
_M.AttachType = AttachType

return _M
