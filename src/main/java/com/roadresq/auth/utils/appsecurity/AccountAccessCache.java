package com.roadresq.auth.utils.appsecurity;

import com.mongodb.ReadPreference;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class AccountAccessCache {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAccessCache.class);

    private static final RedisScript<String> LOOKUP = RedisScript.of(
            new ClassPathResource("redis/account-access-lookup.lua"), String.class);

    private static final RedisScript<Long> FILL = RedisScript.of(
            new ClassPathResource("redis/account-access-fill.lua"), Long.class);

    private static final RedisScript<Long> BLOCK = RedisScript.of(
            new ClassPathResource("redis/account-access-block.lua"), Long.class);

    private static final RedisScript<Long> UNBLOCK = RedisScript.of(
            new ClassPathResource("redis/account-access-unblock.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final JwtService jwtService;
    private final MongoDatabaseFactory databaseFactory;
    private final boolean enabled;

    public AccountAccessCache(
            StringRedisTemplate redis,
            JwtService jwtService,
            MongoDatabaseFactory databaseFactory,
            @Value("${app.account-access-cache.enabled:false}") boolean enabled
    ) {
        this.redis = redis;
        this.jwtService = jwtService;
        this.databaseFactory = databaseFactory;
        this.enabled = enabled;
    }

    String lookup(Claims claims, String token) {
        if (!enabled) {
            return "";
        }

        try {
            String result = redis.execute(LOOKUP, keys(claims, token), UUID.randomUUID().toString());
            return result == null ? "" : result;
        } catch (RuntimeException exception) {
            LOG.debug("Redis access lookup unavailable", exception);
            return "";
        }
    }

    void fill(Claims claims, String token, String generation) {
        if (!enabled || generation.isBlank() || "hit".equals(generation)
                || claims.getExpiration() == null
                || claims.getExpiration().getTime() <= System.currentTimeMillis()) {
            return;
        }

        try {
            redis.execute(FILL, keys(claims, token), generation,
                    Long.toString(claims.getExpiration().getTime()));
        } catch (RuntimeException exception) {
            LOG.debug("Redis access cache update unavailable", exception);
        }
    }

    public void warmOnLogin(String token) {
        if (!enabled) {
            return;
        }

        Runnable warm = () -> {
            try {
                Claims claims = jwtService.parse(token);
                if (!"ROAD_RESQ".equals(claims.get("servicePlatformType", String.class))) {
                    return;
                }

                String generation = lookup(claims, token);
                if (!generation.isBlank() && !"hit".equals(generation)
                        && AccountAccessReader.check(
                        databaseFactory.getMongoDatabase().withReadPreference(ReadPreference.primary()),
                        claims, ZoneId.systemDefault()) == AccountAccessReader.Result.ALLOWED) {
                    fill(claims, token, generation);
                }
            } catch (RuntimeException exception) {
                LOG.debug("Could not warm RoadResQ access cache", exception);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    warm.run();
                }
            });
        } else {
            warm.run();
        }
    }

    public <T> T changeAccess(String userId, Supplier<T> change) {
        if (!enabled) {
            return change.get();
        }

        String prefix = prefix(userId);
        String owner = UUID.randomUUID().toString();
        Long blocked;

        try {
            blocked = redis.execute(BLOCK, List.of(prefix + "blocked", prefix + "generation"), owner);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Account access update temporarily unavailable", exception);
        }

        if (!Long.valueOf(1).equals(blocked)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "An account access update is already in progress");
        }

        Runnable release = () -> {
            try {
                redis.execute(UNBLOCK, List.of(prefix + "blocked"), owner);
            } catch (RuntimeException exception) {
                // A leftover block keeps this account on live checks until an operator clears it.
                LOG.error("Could not clear RoadResQ access block for user {}", userId, exception);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_UNKNOWN) {
                        release.run();
                    }
                }
            });
            return change.get();
        }

        try {
            return change.get();
        } finally {
            release.run();
        }
    }

    private static List<String> keys(Claims claims, String token) {
        String prefix = prefix(claims.get("userId", String.class));
        return List.of(prefix + "blocked", prefix + "generation", prefix + "token:" + digest(token));
    }

    private static String prefix(String userId) {
        return "roadresq:access:v1:{" + digest(userId) + "}:";
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}