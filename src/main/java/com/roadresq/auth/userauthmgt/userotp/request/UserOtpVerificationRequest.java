package com.roadresq.auth.userauthmgt.userotp.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roadresq.auth.userauthmgt.userotp.UserOtpType;
import lombok.Data;

@Data
public class UserOtpVerificationRequest {

    @JsonProperty(required = true) private String otp;
    @JsonProperty(required = true) private String userId;
    @JsonProperty(required = true) private UserOtpType userOtpType;
    @JsonProperty(defaultValue = "false")private boolean destroyOtp;
}
