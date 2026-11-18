package com.hanbay.hotel.booking.dto.response;

public record CancellationEligibilityResponse(
        boolean canCancel,
        String reasonCode,
        String message,
        int refundPercent,
        long refundAmount,
        String currency,
        String policyDescription,
        long feeAmount,
        long outstandingAmount) {
    public CancellationEligibilityResponse(
            boolean canCancel,
            String reasonCode,
            String message,
            int refundPercent,
            long refundAmount,
            String currency,
            String policyDescription) {
        this(canCancel, reasonCode, message, refundPercent, refundAmount, currency, policyDescription, 0, 0);
    }
}
