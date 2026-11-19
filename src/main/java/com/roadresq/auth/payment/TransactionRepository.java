package com.roadresq.auth.payment;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Optional<Transaction> findByActiveCheckoutFor(String rescueRequestId);
    Optional<Transaction> findFirstByRescueRequestIdAndStatusOrderByCompletedAtAsc(String requestId, TransactionStatus status);
    Optional<Transaction> findFirstByRescueRequestIdOrderByCreatedAtDescIdDesc(String requestId);
    Optional<Transaction> findByRescueRequestIdAndReference(String requestId, String reference);
    Optional<Transaction> findByReferenceAndProvider(String reference, PaymentProvider provider);
}