package com.roadresq.auth.customer.response;

public record CustomerForgotPasswordResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}