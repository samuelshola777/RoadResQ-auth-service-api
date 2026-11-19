package com.roadresq.auth.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadresq.auth.utils.exception.InvalidInputException;
import com.roadresq.auth.utils.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/utilities/public/payment-webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final ObjectMapper mapper;
    private final RescuePaymentService paymentService;

    @Value("${paystack.secret.key:}")
    private String paystackKey;
    @Value("${paystack.webhook.signing-secret:}")
    private String paystackSigningSecret;
    @Value("${flutterwave.webhook.secret:}")
    private String flutterwaveWebhookSecret;

    @PostMapping("/paystack")
    public ResponseEntity<Void> paystack(@RequestBody byte[] body,
                                         @RequestHeader(value = "x-paystack-signature", required = false) String legacy,
                                         @RequestHeader(value = "Paystack-Signature", required = false) String signed) {
        if (paystackKey.isBlank() && paystackSigningSecret.isBlank()) {
            throw new UnauthorizedException("Paystack webhook is not configured");
        }
        PaystackSignature.verify(body, legacy, signed, paystackKey, paystackSigningSecret);
        JsonNode event = parse(body);
        if ("charge.success".equals(event.path("event").asText())) {
            paymentService.handleChargeEvent(reference(event, "reference"), PaymentProvider.PAYSTACK);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/flutterwave")
    public ResponseEntity<Void> flutterwave(@RequestBody byte[] body,
                                            @RequestHeader(value = "verif-hash", required = false) String hash) {
        if (hash == null || flutterwaveWebhookSecret.isBlank() || !MessageDigest.isEqual(
                flutterwaveWebhookSecret.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid Flutterwave webhook secret");
        }
        JsonNode event = parse(body);
        if ("charge.completed".equals(event.path("event").asText())
                && "successful".equals(event.path("data").path("status").asText())) {
            paymentService.handleChargeEvent(reference(event, "tx_ref"), PaymentProvider.FLUTTERWAVE);
        }
        return ResponseEntity.ok().build();
    }

    private JsonNode parse(byte[] body) {
        try {
            JsonNode event = mapper.readTree(body);
            if (event != null && event.isObject()) return event;
        } catch (IOException exception) {
            throw new InvalidInputException("Malformed webhook payload");
        }
        throw new InvalidInputException("Missing webhook payload");
    }

    private String reference(JsonNode event, String field) {
        String reference = event.path("data").path(field).asText("");
        if (reference.isBlank()) throw new InvalidInputException("Missing payment reference");
        return reference;
    }
}