package com.roadresq.auth.customer.response;

public record CustomerOtpResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}