-- Release is idempotent: stock is restored only once.
if redis.call('EXISTS', KEYS[2]) == 0 then
  return 0
end
if redis.call('HGET', KEYS[2], 'status') == 'RELEASED' then
  return 0
end
local quantity = tonumber(redis.call('HGET', KEYS[2], 'quantity'))
redis.call('INCRBY', KEYS[1], quantity)
redis.call('HSET', KEYS[2], 'status', 'RELEASED')
return 1
