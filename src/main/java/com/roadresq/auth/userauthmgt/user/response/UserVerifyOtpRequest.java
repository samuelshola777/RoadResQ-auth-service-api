package com.roadresq.auth.userauthmgt.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roadresq.auth.userauthmgt.userotp.UserOtpType;

public record UserVerifyOtpRequest(
   @JsonProperty(required = true) String otp,
   @JsonProperty(required = true)  String userEmail,
   @JsonProperty(required = true) UserOtpType userOtpType
) {
}
