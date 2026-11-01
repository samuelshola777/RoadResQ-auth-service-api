package com.roadresq.auth.platformMemberShip;

import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.utils.ServicePlatformType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformMemberShipRepository
        extends MongoRepository<PlatformMemberShip, String> {

    List<PlatformMemberShip> findAllByUserId(String userId);

    Optional<PlatformMemberShip> findByUserIdAndUserTypeAndServicePlatformType(
            String userId,
            UserType userType,
            ServicePlatformType servicePlatformType
    );

    boolean existsByUserIdAndUserTypeAndServicePlatformType(
            String userId,
            UserType userType,
            ServicePlatformType servicePlatformType
    );

    boolean existsByUserIdAndUserTypeAndServicePlatformTypeAndIsActiveTrue(
            String userId,
            UserType userType,
            ServicePlatformType servicePlatformType
    );
}