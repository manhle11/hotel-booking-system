package com.hanbay.hotel.payment.service;

import com.hanbay.hotel.payment.dto.request.CreatePaymentRequest;
import com.hanbay.hotel.payment.dto.response.CreatePaymentRequestResponse;
import com.hanbay.hotel.payment.dto.response.PaymentStatusResponse;
import java.util.UUID;

public interface PaymentService {
    CreatePaymentRequestResponse createPaymentRequest(CreatePaymentRequest request, String bookingAccessToken);

    PaymentStatusResponse getPaymentStatus(UUID paymentId, String bookingAccessToken);
}
