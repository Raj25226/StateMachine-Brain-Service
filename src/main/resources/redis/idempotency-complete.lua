if redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
    or redis.call('HGET', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'response', ARGV[2], 'httpStatus', ARGV[3], 'status', 'COMPLETED')
redis.call('HDEL', KEYS[1], 'owner')
return 1
