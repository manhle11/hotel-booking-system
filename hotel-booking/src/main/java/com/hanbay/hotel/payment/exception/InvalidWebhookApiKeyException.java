package com.hanbay.hotel.payment.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidWebhookApiKeyException extends DomainException {

    public InvalidWebhookApiKeyException() {
        super("INVALID_WEBHOOK_API_KEY", "API key webhook không hợp lệ", HttpStatus.UNAUTHORIZED);
    }
}
