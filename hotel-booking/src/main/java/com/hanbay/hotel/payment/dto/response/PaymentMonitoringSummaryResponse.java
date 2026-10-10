package com.hanbay.hotel.payment.dto.response;

public record PaymentMonitoringSummaryResponse(
        long totalPayments, long successfulPayments, long totalWebhookEvents, long failedWebhookEvents) {}
