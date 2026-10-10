package com.hanbay.hotel.payment.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PaymentAlreadyCompletedException extends DomainException {
    public PaymentAlreadyCompletedException(String message) {
        super("PAYMENT_ALREADY_COMPLETED", message, HttpStatus.CONFLICT);
    }
}
