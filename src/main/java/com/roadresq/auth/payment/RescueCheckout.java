package com.roadresq.auth.payment;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("rescue_requests")
public record RescueCheckout(
        @Id String id,
        String customerId,
        String status,
        Instant paymentDueAt,
        RescueQuote quote,
        String assignedVendorId,
        String verifiedPaymentReference
) {}