if redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
    or redis.call('HGET', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'response', ARGV[2], 'httpStatus', ARGV[3], 'status', 'COMPLETED')
redis.call('HDEL', KEYS[1], 'owner')
-- Expiry is applied only after the owner has finalized or safely released the claim.
local ttl = tonumber(ARGV[4])
if ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
else
    redis.call('PERSIST', KEYS[1])
end
return 1
