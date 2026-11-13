local fingerprint = redis.call('HGET', KEYS[1], 'fingerprint')
if not fingerprint then
    redis.call('HSET', KEYS[1], 'fingerprint', ARGV[1], 'status', 'PROCESSING', 'owner', ARGV[2])
    -- Active/uncertain execution must never lose its reservation to TTL expiry.
    redis.call('PERSIST', KEYS[1])
    return {'CLAIMED'}
end
if fingerprint ~= ARGV[1] then return {'CONFLICT'} end
local status = redis.call('HGET', KEYS[1], 'status')
if status == 'COMPLETED' then
    return {'REPLAY', redis.call('HGET', KEYS[1], 'response'), redis.call('HGET', KEYS[1], 'httpStatus')}
end
if status == 'RETRYABLE' then
    redis.call('HSET', KEYS[1], 'status', 'PROCESSING', 'owner', ARGV[2])
    -- Active/uncertain execution must never lose its reservation to TTL expiry.
    redis.call('PERSIST', KEYS[1])
    return {'CLAIMED'}
end
return {'IN_PROGRESS'}
