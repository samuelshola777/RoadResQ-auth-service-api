package com.roadresq.auth.platformMemberShip;

import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.utils.ServicePlatformType;
import com.roadresq.auth.utils.exception.InvalidInputException;
import com.roadresq.auth.utils.exception.InvalidOperationException;
import com.roadresq.auth.utils.exception.ResourceExistsException;
import com.roadresq.auth.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformMemberShipService {

    private final PlatformMemberShipRepository platformMemberShipRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.membership-cache.enabled:false}")
    private boolean membershipCacheEnabled;

    @Value("${app.membership-cache.ttl-seconds:30}")
    private long membershipCacheTtlSeconds;

    private volatile long redisRetryAfter;

    public PlatformMemberShip createPlatformMemberShip(String userId, UserType userType, ServicePlatformType servicePlatformType) {
        String validUserId = requireText(userId, "User Identification is required");
        validateTypes(userType, servicePlatformType);
        log.info("Attempting to create platform membership for user: {}, role: {}, platform: {}", validUserId, userType, servicePlatformType);

        if (platformMemberShipRepository.existsByUserIdAndUserTypeAndServicePlatformType(validUserId, userType, servicePlatformType)) {
            log.info("Platform membership already exists for user: {}, role: {}, platform: {}", validUserId, userType, servicePlatformType);
            throw new ResourceExistsException("User account already exists, on this platform");
        }

        PlatformMemberShip memberShip = PlatformMemberShip.builder()
                .userId(validUserId)
                .userType(userType)
                .servicePlatformType(servicePlatformType)
                .isActive(true)
                .build();

        try {
            PlatformMemberShip saved = platformMemberShipRepository.insert(memberShip);
            invalidateAfterCommit(saved);
            log.info("Successfully created platform membership with ID: {}", saved.getId());
            return saved;
        } catch (DuplicateKeyException exception) {
            log.info("Failed to create platform membership. Duplicate key found for user: {}", validUserId);
            throw new ResourceExistsException("Platform membership already exists");
        }
    }

    public PlatformMemberShip getPlatformMemberShipById(String id) {
        String validId = requireText(id, "Membership ID is required");
        log.info("Fetching platform membership by ID: {}", validId);

        return platformMemberShipRepository.findById(validId)
                .orElseThrow(() -> {
                    log.info("Platform membership not found for ID: {}", validId);
                    return new ResourceNotFoundException("Platform membership not found");
                });
    }

    public Page<PlatformMemberShip> getPlatformMemberShips(int page, int size) {
        log.info("Fetching platform memberships for page: {}, size: {}", page, size);

        if (page < 1 || size < 1 || size > 100) {
            log.info("Invalid pagination parameters provided: page={}, size={}", page, size);
            throw new InvalidInputException("Page must be at least 1 and size must be between 1 and 100");
        }

        return platformMemberShipRepository.findAll(PageRequest.of(page - 1, size, Sort.by("id").ascending()));
    }

    public List<PlatformMemberShip> getUserPlatformMemberShips(String userId) {
        String validUserId = requireText(userId, "User ID is required");
        log.info("Fetching all platform memberships for user: {}", validUserId);
        return platformMemberShipRepository.findAllByUserId(validUserId);
    }

    public PlatformMemberShip getPlatformMemberShip(String userId, UserType userType, ServicePlatformType servicePlatformType) {
        validateTypes(userType, servicePlatformType);
        String validUserId = requireText(userId, "User ID is required");
        log.info("Fetching platform membership for user: {}, role: {}, platform: {}", validUserId, userType, servicePlatformType);

        return platformMemberShipRepository.findByUserIdAndUserTypeAndServicePlatformType(validUserId, userType, servicePlatformType)
                .orElseThrow(() -> {
                    log.info("Platform membership not found for user: {}, platform: {}", validUserId, servicePlatformType);
                    return new ResourceNotFoundException("Platform membership not found");
                });
    }

    public PlatformMemberShip updatePlatformMemberShip(String id, boolean isActive) {
        log.info("Attempting to update platform membership ID: {} to active status: {}", id, isActive);
        PlatformMemberShip memberShip = getPlatformMemberShipById(id);
        memberShip.setActive(isActive);

        try {
            PlatformMemberShip saved = platformMemberShipRepository.save(memberShip);
            invalidateAfterCommit(saved);
            log.info("Successfully updated platform membership ID: {}. New status: {}", saved.getId(), saved.isActive());
            return saved;
        } catch (OptimisticLockingFailureException exception) {
            log.info("Optimistic locking failed while updating membership ID: {}", id);
            throw new InvalidOperationException("Membership changed or was deleted. Refresh and try again");
        }
    }

    public void deletePlatformMemberShip(String id) {
        log.info("Attempting to delete platform membership ID: {}", id);
        PlatformMemberShip memberShip = getPlatformMemberShipById(id);

        try {
            platformMemberShipRepository.delete(memberShip);
            invalidateAfterCommit(memberShip);
            log.info("Successfully deleted platform membership ID: {}", id);
        } catch (OptimisticLockingFailureException exception) {
            log.info("Optimistic locking failed while deleting membership ID: {}", id);
            throw new InvalidOperationException("Membership changed or was deleted. Refresh and try again");
        }
    }

    public boolean hasActivePlatformMemberShip(String userId, UserType userType, ServicePlatformType servicePlatformType) {
        String validUserId = requireText(userId, "User ID is required");
        validateTypes(userType, servicePlatformType);

        String key = cacheKey(validUserId, userType, servicePlatformType);
        boolean useCache = canUseRedis() && !TransactionSynchronizationManager.isActualTransactionActive();

        if (useCache) {
            try {
                String cached = stringRedisTemplate.opsForValue().get(key);
                if ("true".equals(cached)) {
                    log.info("Membership cache HIT (active) for user: {}, platform: {}", validUserId, servicePlatformType);
                    return true;
                }
                if ("false".equals(cached)) {
                    log.info("Membership cache HIT (inactive) for user: {}, platform: {}", validUserId, servicePlatformType);
                    return false;
                }
                log.info("Membership cache MISS for user: {}, platform: {}", validUserId, servicePlatformType);
            } catch (RuntimeException exception) {
                redisFailed(exception);
            }
        }

        boolean active = platformMemberShipRepository.existsByUserIdAndUserTypeAndServicePlatformTypeAndIsActiveTrue(validUserId, userType, servicePlatformType);
        log.info("Database check completed for user: {}, platform: {}. Result: {}", validUserId, servicePlatformType, active);

        if (useCache && canUseRedis()) {
            try {
                long seconds = Math.max(1, Math.min(60, membershipCacheTtlSeconds));
                stringRedisTemplate.opsForValue().set(key, Boolean.toString(active), Duration.ofSeconds(seconds));
                log.info("Updated membership cache for user: {}, platform: {} with value: {}", validUserId, servicePlatformType, active);
            } catch (RuntimeException exception) {
                redisFailed(exception);
            }
        }
        return active;
    }

    private boolean canUseRedis() {
        return membershipCacheEnabled && System.currentTimeMillis() >= redisRetryAfter;
    }

    private String cacheKey(String userId, UserType role, ServicePlatformType platform) {
        return "platform-membership:v1:" + userId + ":" + platform.name() + ":" + role.name();
    }

    private void redisFailed(RuntimeException exception) {
        redisRetryAfter = System.currentTimeMillis() + 5000;
        log.info("Membership Redis cache unavailable. Bypassing cache for 5 seconds. Reason: {}", exception.getMessage());
    }

    private void invalidateAfterCommit(PlatformMemberShip membership) {
        if (!membershipCacheEnabled) return;
        String key = cacheKey(membership.getUserId(), membership.getUserType(), membership.getServicePlatformType());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(key);
                }
            });
        } else {
            evict(key);
        }
    }

    private void evict(String key) {
        try {
            stringRedisTemplate.delete(key);
            log.info("Successfully evicted cache key: {}", key);
        } catch (RuntimeException exception) {
            redisFailed(exception);
        }
    }

    private void validateTypes(UserType userType, ServicePlatformType servicePlatformType) {
        if (userType != UserType.CUSTOMER && userType != UserType.VENDOR) {
            log.info("Validation failed: Invalid user type provided: {}", userType);
            throw new InvalidInputException("Choose CUSTOMER or VENDOR");
        }

        if (servicePlatformType == null) {
            log.info("Validation failed: Service platform type is null");
            throw new InvalidInputException("Service platform type is required");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            log.info("Validation failed: {}", message);
            throw new InvalidInputException(message);
        }
        return value.trim();
    }
}