package com.roadresq.auth.payment;

import lombok.RequiredArgsConstructor;
import com.roadresq.auth.notification.NotificationPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;
    private final RescueCheckoutRepository rescueCheckouts;
    private final RescueVendorReservation reservation;

    private final NotificationPublisher notifications;

    @Transactional
    public Transaction recordVerification(String transactionId, VerifiedCharge charge) {
        Transaction tx = transactionRepository.findById(transactionId).orElseThrow();
        if (tx.getStatus() == TransactionStatus.SUCCESS) return tx;

        tx.setUpdatedAt(LocalDateTime.now());
        tx.setProviderStatus(charge.status());
        if (!charge.successful()) {
            if ("failed".equals(charge.status())) {
                tx.setStatus(TransactionStatus.FAILED);
                tx.setActiveCheckoutFor(null);
                tx.setPaymentUrl(null);
                tx.setNextVerificationAt(null);
            }
            return transactionRepository.save(tx);
        }

        Payment payment = new Payment();
        payment.setTransactionId(tx.getId());
        payment.setRescueRequestId(tx.getRescueRequestId());
        payment.setUserId(tx.getUserId());
        payment.setProvider(tx.getProvider());
        payment.setDirection(tx.getDirection());
        payment.setPurpose(tx.getPurpose());
        payment.setAmount(tx.getAmount());
        payment.setCurrency(tx.getCurrency());
        payment.setReference(tx.getReference());
        payment.setPaidAt(charge.paidAt());
        String requestId = tx.getRescueRequestId();
        String vendorId = reservation.vendorFor(requestId);
        boolean assigned = vendorId != null
                && rescueCheckouts.assignAfterPayment(requestId, Instant.now(), vendorId, tx.getReference()) == 1;
        if (!assigned && rescueCheckouts.findByIdAndVerifiedPaymentReference(requestId, tx.getReference()).isEmpty()) {
            // The charge succeeded after cancellation or expiry. Keep the receipt for reconciliation.
            payment.setRequiresReconciliation(true);
        }
        paymentRepository.insert(payment);
        String eventKey = "paid:" + tx.getReference();
        String paymentEvent = payment.isRequiresReconciliation() ? "PaymentUnderReview" : "PaymentConfirmed";
        notifications.record(eventKey, paymentEvent, requestId, "CUSTOMER", "userId", tx.getUserId(), null);
        if (assigned) {
            notifications.record(eventKey, "VendorAssigned", requestId, "VENDOR", "profileId", vendorId, null);
        }
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setCompletedAt(charge.paidAt());
        tx.setPaymentUrl(null);
        tx.setInitializationLeaseUntil(null);
        tx.setNextVerificationAt(null);
        return transactionRepository.save(tx);
    }
}