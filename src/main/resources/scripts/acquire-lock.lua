-- KEYS[1]: lock key; ARGV[1]: owner token; ARGV[2]: lease time in milliseconds
if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
  return 1
end
return 0
