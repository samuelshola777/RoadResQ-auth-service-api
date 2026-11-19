if redis.call('EXISTS', KEYS[1]) == 1 then return '' end
local generation = redis.call('GET', KEYS[2])
if not generation then
  generation = ARGV[1]
  redis.call('SET', KEYS[2], generation, 'EX', 172800)
end
if redis.call('GET', KEYS[3]) == generation then return 'hit' end
return generation