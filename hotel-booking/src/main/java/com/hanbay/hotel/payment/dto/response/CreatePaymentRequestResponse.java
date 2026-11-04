package com.hanbay.hotel.payment.dto.response;

import com.hanbay.hotel.payment.domain.Payment;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.dto.result.SePayQrInfo;
import java.time.Instant;
import java.util.UUID;

public record CreatePaymentRequestResponse(
        UUID paymentId,
        UUID bookingId,
        String bookingCode,
        String transactionRef,
        long amount,
        String currency,
        PaymentStatus status,
        String paymentUrl,
        Instant expiredAt,
        String bankCode,
        String accountNumber,
        String accountName,
        String transferContent) {

    public static CreatePaymentRequestResponse from(Payment payment, SePayQrInfo qrInfo) {
        return new CreatePaymentRequestResponse(
                payment.getId(),
                payment.getOrder() == null
                        ? payment.getBooking().getId()
                        : payment.getOrder().getId(),
                payment.referenceCode(),
                payment.getPaymentCode(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                qrInfo.qrUrl(),
                payment.getExpiresAt(),
                qrInfo.bankCode(),
                qrInfo.accountNumber(),
                qrInfo.accountName(),
                qrInfo.transferContent());
    }
}
