if redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
    or redis.call('HGET', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'status', 'RETRYABLE')
redis.call('HDEL', KEYS[1], 'owner')
-- Expiry is applied only after the owner has finalized or safely released the claim.
local ttl = tonumber(ARGV[2])
if ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
else
    redis.call('PERSIST', KEYS[1])
end
return 1
