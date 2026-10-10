package com.hanbay.hotel.payment.service;

import com.hanbay.hotel.payment.dto.result.SePayQrInfo;

public interface SePayService {
    SePayQrInfo generateQr(long amount, String paymentCode);

    boolean verifyApiKey(String authorizationHeader);

    String sha256(String value);
}
