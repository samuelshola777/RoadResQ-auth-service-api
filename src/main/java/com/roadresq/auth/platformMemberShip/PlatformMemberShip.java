package com.roadresq.auth.platformMemberShip;

import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.utils.ServicePlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "platform_memberships")
@CompoundIndex(
        name = "user_platform_role_unique",
        def = "{'userId': 1, 'servicePlatformType': 1, 'userType': 1}",
        unique = true
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformMemberShip {

    @Id
    private String id;

    private String userId;

    private UserType userType;

    private ServicePlatformType servicePlatformType;

    private boolean isActive;

    @Version
    private Long version;
}