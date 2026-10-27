package com.roadresq.auth.admin.response;

public record AdminAuthResponse(
        String accessToken,
        String refreshToken,
        AdminResponse admin
) {
}