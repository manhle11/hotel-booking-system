package com.hanbay.hotel.payment.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PaymentBookingNotPayableException extends DomainException {
    public PaymentBookingNotPayableException(String message) {
        super("PAYMENT_BOOKING_NOT_PAYABLE", message, HttpStatus.CONFLICT);
    }
}
