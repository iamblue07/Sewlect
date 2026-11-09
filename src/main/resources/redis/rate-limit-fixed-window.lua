local permits = tonumber(ARGV[3])
local count = redis.call('INCRBY', KEYS[1], permits)
if redis.call('PTTL', KEYS[1]) < 0 then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
if count <= tonumber(ARGV[1]) then
    return 1
end
redis.call('DECRBY', KEYS[1], permits)
return 0
