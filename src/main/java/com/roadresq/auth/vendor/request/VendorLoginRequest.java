package com.roadresq.auth.vendor.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorLoginRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String emailAddress,

        @NotBlank
        @Size(max = 100)
        String password
) {
}