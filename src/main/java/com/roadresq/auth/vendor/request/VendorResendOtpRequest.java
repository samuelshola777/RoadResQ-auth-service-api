package com.roadresq.auth.vendor.request;



import com.roadresq.auth.userauthmgt.userotp.UserOtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VendorResendOtpRequest(

        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String emailAddress,
        @NotNull UserOtpType userOtpType
) {
}