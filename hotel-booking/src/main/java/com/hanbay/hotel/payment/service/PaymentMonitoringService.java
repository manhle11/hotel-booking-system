package com.hanbay.hotel.payment.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.domain.WebhookProcessingStatus;
import com.hanbay.hotel.payment.dto.response.MonitoredPaymentResponse;
import com.hanbay.hotel.payment.dto.response.MonitoredWebhookEventResponse;
import com.hanbay.hotel.payment.dto.response.PaymentMonitoringSummaryResponse;
import com.hanbay.hotel.payment.dto.response.WebhookEventDetailResponse;
import java.util.UUID;

public interface PaymentMonitoringService {

    PaymentMonitoringSummaryResponse getSummary();

    PageResponse<MonitoredPaymentResponse> getPayments(int page, int size, String keyword, PaymentStatus status);

    PageResponse<MonitoredWebhookEventResponse> getWebhookEvents(
            int page, int size, String keyword, WebhookProcessingStatus status);

    WebhookEventDetailResponse getWebhookEvent(UUID eventId);
}
