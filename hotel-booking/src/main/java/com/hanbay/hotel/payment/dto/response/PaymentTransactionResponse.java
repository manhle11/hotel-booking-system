package com.hanbay.hotel.payment.dto.response;

import com.hanbay.hotel.payment.domain.PaymentTransactionStatus;
import com.hanbay.hotel.payment.domain.PaymentTransactionType;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID transactionId,
        String providerTransactionId,
        String transactionRef,
        PaymentTransactionType type,
        PaymentTransactionStatus status,
        long amount,
        String currency,
        String providerResponseCode,
        String providerMessage,
        Instant occurredAt,
        Instant receivedAt) {}
