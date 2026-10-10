package com.hanbay.hotel.payment.service;

import com.hanbay.hotel.payment.dto.request.SePayWebhookRequest;

public interface PaymentWebhookService {

    void handleSePayWebhook(String authorization, SePayWebhookRequest request);
}
