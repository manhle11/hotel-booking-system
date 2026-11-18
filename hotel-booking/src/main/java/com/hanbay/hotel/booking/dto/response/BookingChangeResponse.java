package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingChangeRequest;
import java.time.*;
import java.util.*;

public record BookingChangeResponse(
        UUID id,
        String bookingCode,
        String orderCode,
        BookingChangeRequest.Type type,
        BookingChangeRequest.Status status,
        String reason,
        String requestedBy,
        Instant requestedAt,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String decidedBy,
        Instant decidedAt,
        String decisionNote,
        Map<String, Object> snapshot,
        Instant consentAt) {
    public record Quote(
            BookingChangeResponse request,
            boolean canApprove,
            String blockReason,
            boolean requiresFeeDecision,
            String policyDescription,
            String quoteToken,
            long oldTotalAmount,
            long newTotalAmount,
            long availableAmount,
            Long feeAmount,
            Long refundAmount,
            long additionalAmount,
            List<Room> rooms,
            String zone,
            Instant serverNow,
            boolean pricingAvailable) {}

    public record Room(
            String bookingCode,
            String roomTypeName,
            LocalDate oldCheckInDate,
            LocalDate oldCheckOutDate,
            LocalDate newCheckInDate,
            LocalDate newCheckOutDate,
            long oldTotalAmount,
            long newTotalAmount,
            long availableAmount,
            long standardFee,
            List<Night> nights,
            Long originalFirstNightAmount,
            long previousFee,
            int children,
            List<String> services,
            FeeTiming feeTiming) {}

    public record FeeTiming(Instant checkInAt, Instant freeUntil, Instant halfFeeUntil, int ratePercent) {}

    public record Night(LocalDate stayDate, long price) {}
}
