package com.roadresq.auth.vendor.response;

public record VendorAuthResponse(
        String accessToken,
        String refreshToken,
        VendorResponse vendor
) {
}