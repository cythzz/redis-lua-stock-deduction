-- Atomically validate and deduct inventory to prevent overselling.
local value = redis.call('GET', KEYS[1])
if not value then
  return -2
end
local stock = tonumber(value)
local quantity = tonumber(ARGV[1])
if stock < quantity then
  return -1
end
return redis.call('DECRBY', KEYS[1], quantity)
