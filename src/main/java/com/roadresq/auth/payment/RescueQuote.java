package com.roadresq.auth.payment;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

public record RescueQuote(
        String currency,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal total,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount30Percent,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount70Percent,
        boolean temporaryPricing
) {}