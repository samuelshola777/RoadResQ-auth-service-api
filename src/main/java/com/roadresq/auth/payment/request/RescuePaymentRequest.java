package com.roadresq.auth.payment.request;

import com.roadresq.auth.payment.PaymentProvider;
import jakarta.validation.constraints.NotNull;

public record RescuePaymentRequest(@NotNull PaymentProvider provider) {}