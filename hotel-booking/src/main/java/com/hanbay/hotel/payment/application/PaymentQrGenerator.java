package com.hanbay.hotel.payment.application;

public interface PaymentQrGenerator {
    PaymentQrData generate(long amount, String paymentCode);
}
