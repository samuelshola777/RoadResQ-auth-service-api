package com.roadresq.auth.vendor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record VendorUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Enter 7–15 digits with an optional +") String phoneNumber,
        @Size(max = 500) String profilePictureUrl,
        @Size(max = 150) String businessName,
        @Size(max = 1000) String equipment,
        @Size(max = 50) Set<@NotBlank @Size(max = 100) String> coverageAreas
) {
}