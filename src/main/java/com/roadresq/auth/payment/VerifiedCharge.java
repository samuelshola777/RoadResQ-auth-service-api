package com.roadresq.auth.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.roadresq.auth.utils.exception.ErrorProcessingRequestException;

import java.math.BigDecimal;
import java.time.Instant;

public record VerifiedCharge(String status, boolean successful, Instant paidAt) {

    public static VerifiedCharge from(PaymentProvider provider, String expectedReference, BigDecimal expectedAmount,
                                      String expectedCurrency, JsonNode data) {
        boolean paystack = provider == PaymentProvider.PAYSTACK;
        String reference = data.path(paystack ? "reference" : "tx_ref").asText("");
        String status = data.path("status").asText("");
        if (!expectedReference.equals(reference) || status.isBlank()) {
            throw new ErrorProcessingRequestException("Payment verification reference or status is invalid");
        }
        if (!(paystack ? "success" : "successful").equals(status)) {
            return new VerifiedCharge(status, false, null);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(data.path("amount").asText(""));
        } catch (NumberFormatException exception) {
            throw new ErrorProcessingRequestException("Payment verification amount is missing or invalid");
        }
        if (paystack) amount = amount.movePointLeft(2);
        if (!expectedCurrency.equals(data.path("currency").asText()) || expectedAmount.compareTo(amount) != 0) {
            throw new ErrorProcessingRequestException("Verified amount or currency differs from checkout");
        }
        String paidAt = data.path("paid_at").asText("");
        return new VerifiedCharge(status, true, paidAt.isBlank() ? Instant.now() : Instant.parse(paidAt));
    }
}