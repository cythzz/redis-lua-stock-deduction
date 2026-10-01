if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
if redis.call('HGET', KEYS[1], 'status') == 'RESERVED' then
  redis.call('HSET', KEYS[1], 'status', 'COMMITTED')
end
return 1
