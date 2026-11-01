package com.roadresq.auth.vendor.response;

public record VendorForgotPasswordResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}