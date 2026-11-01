package com.roadresq.auth.vendor.response;

import com.roadresq.auth.vendor.Vendor;
import com.roadresq.auth.userauthmgt.user.User;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;

@Builder
public record VendorResponse(
        String vendorId,
        String userId,
        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        String profilePictureUrl,
        String businessName,
        String equipment,
        Set<String> coverageAreas,
        String eligibility,
        String reviewReason,
        boolean active,
        boolean verified,
        boolean onboardingComplete,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VendorResponse from(Vendor vendor, User user) {
        return VendorResponse.builder()
                .vendorId(vendor.getId()).userId(user.getId())
                .firstName(user.getFirstName()).lastName(user.getLastName())
                .emailAddress(user.getEmailAddress()).phoneNumber(user.getPhoneNumber())
                .profilePictureUrl(user.getProfilePictureUrl())
                .eligibility(vendor.getEligibility() == null ? "PENDING" : vendor.getEligibility().name())
                .reviewReason(vendor.getReviewReason())
                .coverageAreas(Set.of())
                .onboardingComplete(user.getFirstName() != null && !user.getFirstName().isBlank()
                        && user.getLastName() != null && !user.getLastName().isBlank()
                        && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank())
                .active(vendor.isActive() && user.isActive()).verified(user.isVerified())
                .submittedAt(vendor.getSubmittedAt()).reviewedAt(vendor.getReviewedAt())
                .lastLoginAt(vendor.getLastLoginAt()).createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt()).build();
    }
}