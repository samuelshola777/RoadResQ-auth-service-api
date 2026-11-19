package com.roadresq.auth.payment;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.Optional;

// This view exposes checkout fields only. Never replace an entire rescue document with it.
public interface RescueCheckoutRepository extends Repository<RescueCheckout, String> {

    Optional<RescueCheckout> findByIdAndCustomerId(String id, String customerId);

    Optional<RescueCheckout> findByIdAndVerifiedPaymentReference(String id, String reference);

    @Query("{ '_id': ?0, 'status': 'AWAITING_PAYMENT', 'paymentDueAt': { '$gt': ?1 } }")
    @Update("""
            { '$set': { 'status': 'ASSIGNED', 'assignedVendorId': ?2,
                        'verifiedPaymentReference': ?3, 'updatedAt': ?1 }, '$unset': { 'paymentDueAt': '' } }
            """)
    long assignAfterPayment(String requestId, Instant now, String vendorId, String verifiedReference);
}