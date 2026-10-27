package com.roadresq.auth.admin.request;

public record AdminLoginRequest(
        String emailAddress,
        String password
) {
}