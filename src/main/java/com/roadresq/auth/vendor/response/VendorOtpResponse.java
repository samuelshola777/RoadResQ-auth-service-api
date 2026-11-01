package com.roadresq.auth.vendor.response;

public record VendorOtpResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}