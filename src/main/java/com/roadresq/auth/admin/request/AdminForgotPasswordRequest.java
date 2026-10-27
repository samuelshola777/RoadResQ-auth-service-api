package com.roadresq.auth.admin.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminForgotPasswordRequest(
        @NotBlank @Email String emailAddress
) {
}