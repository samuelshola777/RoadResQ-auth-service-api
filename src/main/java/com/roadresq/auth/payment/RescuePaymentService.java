package com.roadresq.auth.payment;

import io.jsonwebtoken.Claims;
import com.roadresq.auth.utils.exception.UnauthorizedException;
import com.roadresq.auth.utils.exception.ErrorProcessingRequestException;
import com.roadresq.auth.utils.exception.ResourceExistsException;
import com.roadresq.auth.utils.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RescuePaymentService {

    private final RescueCheckoutRepository rescueCheckouts;
    private final RescueVendorReservation reservation;
    private final TransactionRepository transactions;
    private final PaymentRepository payments;
    private final PaymentGatewayService gateway;
    private final PaymentSettlementService settlement;
    private final MongoTemplate mongo;
    private final HttpServletRequest httpRequest;

    @Value("${payment.checkout.portion:}")
    private String checkoutPortion;

    public Transaction initializePayment(String requestId, PaymentProvider provider) {
        RescueCheckout request = getCustomerCheckout(requestId);
        Transaction paid = transactions.findFirstByRescueRequestIdAndStatusOrderByCompletedAtAsc(
                requestId, TransactionStatus.SUCCESS).orElse(null);
        if (paid != null) return paid;
        Transaction tx = transactions.findByActiveCheckoutFor(requestId).orElse(null);
        requireActiveHold(request);
        gateway.checkConfiguration(provider);
        if (tx == null) {
            tx = new Transaction();
            // One payable checkout per approved rescue quote, across both providers.
            tx.setId(UUID.randomUUID().toString());
            tx.setActiveCheckoutFor(requestId);
            tx.setRescueRequestId(requestId);
            tx.setUserId((String) httpRequest.getAttribute("userId"));
            tx.setReference("RR-" + UUID.randomUUID());
            tx.setAmount(checkoutAmount(request.quote()));
            tx.setCurrency(request.quote().currency());
            tx.setProvider(provider);
            tx.setVerificationStartedAt(Instant.now());
            tx.setNextVerificationAt(Instant.now().plusSeconds(60));
            try {
                tx = transactions.insert(tx);
            } catch (DuplicateKeyException concurrentCheckout) {
                tx = transactions.findByActiveCheckoutFor(requestId).orElseThrow(() -> concurrentCheckout);
            }
        }
        if (tx.getProvider() != provider) {
            throw new ResourceExistsException("A checkout already exists with " + tx.getProvider());
        }
        if (tx.getPaymentUrl() != null || tx.getStatus() != TransactionStatus.PENDING) return tx;

        Instant now = Instant.now();
        Instant lease = Instant.ofEpochMilli(now.plusSeconds(45).toEpochMilli());
        Criteria pending = Criteria.where("id").is(tx.getId()).and("status").is(TransactionStatus.PENDING)
                .and("paymentUrl").is(null);
        Query available = Query.query(pending.orOperator(Criteria.where("initializationLeaseUntil").is(null),
                Criteria.where("initializationLeaseUntil").lte(now)));
        if (mongo.updateFirst(available, new Update().set("initializationLeaseUntil", lease), Transaction.class)
                .getModifiedCount() == 0) return transactions.findById(tx.getId()).orElseThrow();

        String url = gateway.initializePayment(tx, (String) httpRequest.getAttribute("email"));
        Query owner = Query.query(Criteria.where("id").is(tx.getId()).and("status").is(TransactionStatus.PENDING)
                .and("initializationLeaseUntil").is(lease));
        mongo.updateFirst(owner, new Update().set("paymentUrl", url).unset("initializationLeaseUntil")
                .set("updatedAt", LocalDateTime.now()), Transaction.class);
        return transactions.findById(tx.getId()).orElseThrow();
    }

    private BigDecimal checkoutAmount(RescueQuote quote) {
        if (quote == null || !"NGN".equals(quote.currency())) {
            throw new ResourceExistsException("An approved NGN quote is required");
        }
        BigDecimal amount = switch (checkoutPortion) {
            case "TOTAL" -> quote.total();
            case "30_PERCENT" -> quote.amount30Percent();
            case "70_PERCENT" -> quote.amount70Percent();
            default -> throw new ErrorProcessingRequestException("Rescue checkout amount has not been configured");
        };
        if (amount == null || amount.signum() <= 0) {
            throw new ResourceExistsException("The approved quote has no payable amount");
        }
        return amount;
    }

    private RescueCheckout getCustomerCheckout(String requestId) {
        Object value = httpRequest.getAttribute("validatedClaims");
        if (!(value instanceof Claims claims) || !"CUSTOMER".equals(claims.get("userType", String.class))
                || !"ROAD_RESQ".equals(claims.get("servicePlatformType", String.class))) {
            throw new UnauthorizedException("A RoadResQ customer is required");
        }
        String customerId = claims.get("customerId", String.class);
        if (customerId == null || customerId.isBlank()) {
            throw new UnauthorizedException("A valid customer account is required");
        }
        return rescueCheckouts.findByIdAndCustomerId(requestId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rescue request not found"));
    }

    private void requireActiveHold(RescueCheckout request) {
        if (!"AWAITING_PAYMENT".equals(request.status()) || request.paymentDueAt() == null
                || !request.paymentDueAt().isAfter(Instant.now()) || reservation.vendorFor(request.id()) == null) {
            throw new ResourceExistsException("Checkout requires an active vendor reservation");
        }
    }

    public Transaction verifyPayment(String requestId, String reference) {
        getCustomerCheckout(requestId);
        var attempt = reference == null
                ? transactions.findFirstByRescueRequestIdOrderByCreatedAtDescIdDesc(requestId)
                : transactions.findByRescueRequestIdAndReference(requestId, reference);
        Transaction tx = attempt.orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));
        return verify(tx);
    }

    public Payment getPayment(String requestId) {
        getCustomerCheckout(requestId);
        return payments.findFirstByRescueRequestIdOrderByRequiresReconciliationAscPaidAtDesc(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Confirmed payment not found"));
    }

    public void handleChargeEvent(String reference, PaymentProvider provider) {
        transactions.findByReferenceAndProvider(reference, provider).ifPresent(tx -> {
            if (verify(tx).getStatus() != TransactionStatus.SUCCESS) {
                throw new ErrorProcessingRequestException("Provider has not confirmed the charge yet");
            }
        });
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcilePending() {
        for (int count = 0; count < 20; count++) {
            Instant now = Instant.now();
            Query due = Query.query(Criteria.where("status").is(TransactionStatus.PENDING)
                    .and("nextVerificationAt").lte(now).ne(null)).with(Sort.by("nextVerificationAt"));
            // Claim one attempt across all workers, including after a restart.
            Transaction tx = mongo.findAndModify(due,
                    new Update().set("nextVerificationAt", now.plusSeconds(300)),
                    FindAndModifyOptions.options().returnNew(true), Transaction.class);
            if (tx == null) return;
            try {
                verify(tx);
            } catch (RuntimeException exception) {
                log.warn("Payment verification pending for reference {}, reason {}",
                        tx.getReference(), exception.getClass().getSimpleName());
            }
            if (tx.getVerificationStartedAt().plusSeconds(172800).isBefore(now)) {
                mongo.updateFirst(Query.query(Criteria.where("id").is(tx.getId())
                                .and("status").is(TransactionStatus.PENDING)),
                        new Update().unset("nextVerificationAt"), Transaction.class);
            }
        }
    }

    private Transaction verify(Transaction tx) {
        if (tx.getStatus() == TransactionStatus.SUCCESS) return tx;
        VerifiedCharge charge = VerifiedCharge.from(tx.getProvider(), tx.getReference(), tx.getAmount(),
                tx.getCurrency(), gateway.verifyPayment(tx));
        Transaction saved = settlement.recordVerification(tx.getId(), charge);
        if (saved.getStatus() == TransactionStatus.SUCCESS) {
            try {
                rescueCheckouts.findByIdAndVerifiedPaymentReference(saved.getRescueRequestId(), saved.getReference())
                        .ifPresent(request -> reservation.release(request.id(), request.assignedVendorId()));
            } catch (DataAccessException exception) {
                log.warn("Redis hold will expire after confirmed payment for request {}", saved.getRescueRequestId());
            }
        }
        return saved;
    }
}