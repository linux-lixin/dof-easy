local _M = {}
local dp = _DP
local log = dp.log
local msgpack = require("msgpack")

--[[
usage:

local req = {
        ["type"] = "op_type",
        ["param"] = "hello, world !!!"
    }
    local callback = function(_resp)
        local resp = msgpack.unpack(_resp)

        log.info("resp! code: " .. resp.code .. ", desc: " .. resp.desc)
    end

    dp.work.post("hello", msgpack.pack(req), callback)

    dp.work.post("test")
]]

local test = function()
    log.info("work.test")
end

local hello = function(_req)
    local req = msgpack.unpack(_req)
    local resp = {}

    resp.code = 0
    resp.desc = req.param;

    return msgpack.pack(resp)
end

_M.test = test
_M.hello = hello

return _M
