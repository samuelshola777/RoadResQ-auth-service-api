package com.roadresq.auth.vendor.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorCreateRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String emailAddress,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName
) {
}