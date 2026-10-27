package com.roadresq.auth.userauthmgt.user.request;

public record UserActivateRequest(

        String otp,
        String userId
) {
}
