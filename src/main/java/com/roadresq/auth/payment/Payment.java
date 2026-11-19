package com.roadresq.auth.payment;

import com.roadresq.auth.utils.BaseEntity;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@TypeAlias("com.roadresq.roadresq.payment.Payment")
@Document(collection = "payments")
public class Payment extends BaseEntity {

    @Indexed
    private String rescueRequestId;
    private String userId;
    private PaymentProvider provider;
    private TransactionDirection direction = TransactionDirection.INCOMING;
    private TransactionPurpose purpose = TransactionPurpose.RESCUE_PAYMENT;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;
    private String currency;

    @Indexed(unique = true)
    private String transactionId;
    private String reference;
    private Instant paidAt;
    private PaymentStatus status = PaymentStatus.SUCCESS;
    private boolean requiresReconciliation;
}