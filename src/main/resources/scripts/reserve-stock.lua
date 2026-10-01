-- KEYS[1] stock key, KEYS[2] idempotency/reservation hash.
-- ARGV[1] order id, ARGV[2] quantity, ARGV[3] reservation TTL in milliseconds.
if redis.call('EXISTS', KEYS[2]) == 1 then
  return -4
end

local current = redis.call('GET', KEYS[1])
if not current then
  return -2
end

local stock = tonumber(current)
local quantity = tonumber(ARGV[2])
if stock < quantity then
  return -1
end

local remaining = redis.call('DECRBY', KEYS[1], quantity)
redis.call('HSET', KEYS[2],
  'order_id', ARGV[1],
  'quantity', ARGV[2],
  'status', 'RESERVED')
redis.call('PEXPIRE', KEYS[2], ARGV[3])
return remaining
