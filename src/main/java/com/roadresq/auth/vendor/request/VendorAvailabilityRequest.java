package com.roadresq.auth.vendor.request;

import jakarta.validation.constraints.NotNull;

public record VendorAvailabilityRequest(@NotNull Boolean available) {
}