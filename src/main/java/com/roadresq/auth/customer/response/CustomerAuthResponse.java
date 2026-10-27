package com.roadresq.auth.customer.response;

public record CustomerAuthResponse(
        String accessToken,
        String refreshToken,
        CustomerResponse customer
) {
}