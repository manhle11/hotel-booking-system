package com.hanbay.hotel.payment.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID paymentId,
        UUID bookingId,
        String bookingCode,
        String transactionRef,
        PaymentStatus paymentStatus,
        BookingStatus bookingStatus,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int adultCount,
        int childCount,
        long totalNights,
        long amount,
        long paidAmount,
        String currency,
        PaymentRefundStatus refundStatus,
        BigDecimal refundAmount,
        String refundReason,
        String paymentUrl,
        String bankCode,
        String accountNumber,
        String accountName,
        String transferContent,
        Instant expiredAt,
        Instant paidAt,
        long remainingAmount,
        boolean policyFeePayment) {}
