if redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
    or redis.call('HGET', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'status', 'RETRYABLE')
redis.call('HDEL', KEYS[1], 'owner')
return 1
