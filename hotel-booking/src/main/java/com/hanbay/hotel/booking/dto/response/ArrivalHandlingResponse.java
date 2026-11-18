package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArrivalHandlingResponse(
        UUID bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        String policyVersion,
        boolean legacyPolicy,
        boolean canMarkNoShow,
        String blockReason,
        Instant cutoff,
        Instant departureDeadline,
        Instant serverNow,
        String zone,
        long originalTotalAmount,
        long paidAmount,
        long standardFee,
        long feeAmount,
        long refundAmount,
        long outstandingAmount,
        Instant holdUntil,
        Instant holdRequestedUntil,
        Instant noShowAt,
        String previewToken,
        List<Event> history,
        List<Refund> refunds,
        boolean staffCancellation,
        int stayRevision,
        String orderCode,
        int roomCount,
        long receivedAmount,
        long availableAmount,
        ArrivalProgress progress,
        boolean settlementReviewRequired,
        long policyFeeTotal,
        Instant balanceDueAt,
        List<OrderRoom> orderRooms) {
    public record OrderRoom(
            String bookingCode,
            Integer orderRoomNumber,
            String roomTypeName,
            BookingStatus bookingStatus,
            Instant expectedAt,
            Instant receptionDeadline,
            Instant departureDeadline,
            Instant holdUntil,
            Instant holdRequestedUntil,
            boolean canManageArrival) {}

    public record Event(Instant at, String actor, String note) {}

    public record Refund(UUID refundId, long amount, PaymentRefundStatus status, boolean informationRequired) {}
}
