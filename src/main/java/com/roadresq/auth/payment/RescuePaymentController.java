package com.roadresq.auth.payment;

import com.roadresq.auth.payment.request.RescuePaymentRequest;
import com.roadresq.auth.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rescue-requests/private/{requestId}/payment")
@RequiredArgsConstructor
public class RescuePaymentController {

    private final RescuePaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<Transaction>> initializePayment(
            @PathVariable String requestId, @Valid @RequestBody RescuePaymentRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment transaction retrieved",
                paymentService.initializePayment(requestId, request.provider())));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Transaction>> verifyPayment(@PathVariable String requestId,
                                                                  @RequestParam(required = false) String reference) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment status retrieved",
                paymentService.verifyPayment(requestId, reference)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Payment>> getPayment(@PathVariable String requestId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment retrieved",
                paymentService.getPayment(requestId)));
    }
}