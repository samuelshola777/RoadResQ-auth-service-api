package com.roadresq.auth.admin.request;





import com.roadresq.auth.utils.Gender;

import java.time.LocalDate;

public record AdminUpdateRequest(
        String firstName,
        String lastName,
        String phoneNumber,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        Gender gender
) {
}