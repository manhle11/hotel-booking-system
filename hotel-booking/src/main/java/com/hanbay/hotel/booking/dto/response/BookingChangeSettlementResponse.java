package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BookingChangeSettlementResponse(
        UUID requestId,
        String bookingCode,
        String orderCode,
        int roomCount,
        Instant appliedAt,
        String stayStatus,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long obligationAmount,
        long receivedAmount,
        long availableAmount,
        long outstandingAmount,
        String collectionStatus,
        Instant balanceDueAt,
        boolean overdue,
        long refundedAmount,
        long pendingRefundAmount,
        long unrequestedRefundAmount,
        String refundStatus,
        boolean needsReview,
        Long additionalAtApproval,
        Long refundAtApproval,
        List<Room> rooms,
        List<Refund> refunds,
        String zone,
        Instant checkedAt) {
    public record Room(
            String bookingCode,
            String roomTypeName,
            BookingStatus bookingStatus,
            long obligationAmount,
            long availableAmount,
            long outstandingAmount,
            Instant balanceDueAt,
            boolean needsReview) {}

    public record Refund(
            UUID refundId,
            String bookingCode,
            String paymentCode,
            long amount,
            PaymentRefundStatus status,
            Instant requestedAt,
            Instant refundedAt,
            String bankReference,
            boolean destinationProvided) {}
}
