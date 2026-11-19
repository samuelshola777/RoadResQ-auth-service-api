package com.roadresq.auth.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.roadresq.auth.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@TypeAlias("com.roadresq.roadresq.payment.Transaction")
@Document(collection = "transactions")
public class Transaction extends BaseEntity {

    @Indexed
    private String rescueRequestId;

    @JsonIgnore
    @Indexed(unique = true, partialFilter = "{'activeCheckoutFor': {'$type': 'string'}}")
    private String activeCheckoutFor;
    private String userId;
    private PaymentProvider provider;
    private TransactionDirection direction = TransactionDirection.INCOMING;
    private TransactionPurpose purpose = TransactionPurpose.RESCUE_PAYMENT;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;
    private String currency;

    @Indexed(unique = true)
    private String reference;
    private TransactionStatus status = TransactionStatus.PENDING;
    private String paymentUrl;
    private String providerStatus;
    private Instant completedAt;
    @JsonIgnore
    @Indexed
    private Instant nextVerificationAt;
    @JsonIgnore
    private Instant verificationStartedAt;
    @JsonIgnore
    private Instant initializationLeaseUntil;
}