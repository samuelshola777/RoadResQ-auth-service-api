package com.roadresq.auth.admin.response;




import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.utils.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminResponse(
        String adminId,
        String userId,

        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        Gender gender,
        String age,
        UserType userType,
        boolean active,
        boolean userActive,
        boolean userVerified,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {
}