package com.roadresq.auth.admin.request;


import com.roadresq.auth.utils.Gender;

public record AdminCreateRequest(
        String firstName,
        String lastName,
        String emailAddress,
        String password,
        String phoneNumber,
        String profilePictureUrl,
        String profileColourCode,
        Gender gender
) {
}