package com.roadresq.auth.vendor.request;

import com.roadresq.auth.notification.NotificationDeviceRequest;
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
        String password,
        String installationId,
        NotificationDeviceRequest device
) {
        public VendorLoginRequest(String emailAddress, String password) {
                this(emailAddress, password, null, null);
        }
}