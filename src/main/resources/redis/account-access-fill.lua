if redis.call('EXISTS', KEYS[1]) == 0 and redis.call('GET', KEYS[2]) == ARGV[1] then
  redis.call('SET', KEYS[3], ARGV[1], 'PXAT', ARGV[2])
  return 1
end
return 0