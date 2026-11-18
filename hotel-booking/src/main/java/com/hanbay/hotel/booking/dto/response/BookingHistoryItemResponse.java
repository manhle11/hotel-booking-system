package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingHistoryItemResponse(
        UUID bookingId,
        String bookingCode,
        BookingHistoryRoomTypeResponse roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int adultCount,
        int childCount,
        long totalNights,
        BigDecimal totalAmount,
        BigDecimal outstandingAmount,
        String currency,
        BookingStatus bookingStatus,
        UUID paymentId,
        PaymentStatus paymentStatus,
        PaymentRefundStatus refundStatus,
        BigDecimal refundAmount,
        Instant createdAt,
        boolean staffCancellation,
        boolean rescheduled) {
    public BookingHistoryItemResponse(
            UUID bookingId,
            String bookingCode,
            BookingHistoryRoomTypeResponse roomType,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int adultCount,
            int childCount,
            long totalNights,
            BigDecimal totalAmount,
            BigDecimal outstandingAmount,
            String currency,
            BookingStatus bookingStatus,
            UUID paymentId,
            PaymentStatus paymentStatus,
            PaymentRefundStatus refundStatus,
            BigDecimal refundAmount,
            Instant createdAt) {
        this(
                bookingId,
                bookingCode,
                roomType,
                checkInDate,
                checkOutDate,
                adultCount,
                childCount,
                totalNights,
                totalAmount,
                outstandingAmount,
                currency,
                bookingStatus,
                paymentId,
                paymentStatus,
                refundStatus,
                refundAmount,
                createdAt,
                false,
                false);
    }
}
