package com.roadresq.auth.vendor.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VendorForgotPasswordRequest(

        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String emailAddress
) {
}