-- Outbound reporting disabled in the dof-easy deployment overlay.
local M = {}
function M.create(host, port)
    return { send = function(...) end, close = function(...) end }
end
return M
