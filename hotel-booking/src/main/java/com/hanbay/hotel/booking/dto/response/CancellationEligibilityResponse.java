package com.hanbay.hotel.booking.dto.response;

public record CancellationEligibilityResponse(
        boolean canCancel,
        String reasonCode,
        String message,
        int refundPercent,
        long refundAmount,
        String currency,
        String policyDescription) {}
