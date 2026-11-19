package com.hanbay.hotel.booking.policy;

import com.hanbay.hotel.booking.dto.response.RefundStatus;

public record CancellationDecision(
        boolean allowed,
        String denialCode,
        String message,
        int refundPercent,
        long refundAmount,
        long feeAmount,
        RefundStatus refundStatus) {

    public static CancellationDecision denied(String code, String message) {
        return new CancellationDecision(false, code, message, 0, 0, 0, RefundStatus.NOT_REQUIRED);
    }

    public static CancellationDecision allowed(
            int refundPercent, long refundAmount, long feeAmount, RefundStatus refundStatus) {
        return new CancellationDecision(true, null, null, refundPercent, refundAmount, feeAmount, refundStatus);
    }
}
