package com.roadresq.auth.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RescueVendorReservation {
    private static final String PREFIX = "roadresq:{dispatch}:";
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('GET', KEYS[2]) == ARGV[2] then
                return redis.call('DEL', KEYS[1], KEYS[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public String vendorFor(String requestId) {
        return redis.opsForValue().get(PREFIX + "request:" + requestId);
    }

    public void release(String requestId, String vendorId) {
        redis.execute(RELEASE, List.of(PREFIX + "request:" + requestId, PREFIX + "vendor:" + vendorId),
                vendorId, requestId);
    }
}