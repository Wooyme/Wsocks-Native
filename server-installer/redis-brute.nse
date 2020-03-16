local brute = require "brute"
local creds = require "creds"
local redis = require "redis"
local shortport = require "shortport"
local stdnse = require "stdnse"

description = [[
Performs brute force passwords auditing against a Redis key-value store.
]]

---
-- @usage
-- nmap -p 6379 <ip> --script redis-brute
--
-- @output
-- PORT     STATE SERVICE
-- 6379/tcp open  unknown
-- | redis-brute:
-- |   Accounts
-- |     toledo - Valid credentials
-- |   Statistics
-- |_    Performed 5000 guesses in 3 seconds, average tps: 1666
--
--

author = "Wooyme"
license = "Same as Nmap--See https://nmap.org/book/man-legal.html"
categories = {"intrusive", "brute"}


portrule = shortport.port_or_service(6379, "redis")

local function checkRedis(host, port)

  local helper = redis.Helper:new(host, port)
  local status = helper:connect()
  if( not(status) ) then
    return false, "Failed to connect to server"
  end

  local status, response = helper:reqCmd("SET", "thisisaredistest","thisisaredistest")
  if ( not(status) ) then
    return false, "Failed to request SET command"
  end

  if ( redis.Response.Type.ERROR == response.type ) then
    if ( "-ERR operation not permitted" == response.data ) or
        ( "-NOAUTH Authentication required." == response.data) then
      return false, "Need Authentication"
    end
  end
  return true, host
end

action = function(host, port)
  local status, msg =  checkRedis(host, port)
  return msg
end
