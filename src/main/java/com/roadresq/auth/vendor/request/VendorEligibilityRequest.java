package com.roadresq.auth.vendor.request;

import com.roadresq.auth.utils.Eligibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VendorEligibilityRequest(
        @NotNull Eligibility eligibility,
        @Size(max = 1000) String reason
) {
}