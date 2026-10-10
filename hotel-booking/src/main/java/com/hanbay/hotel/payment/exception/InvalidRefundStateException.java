package com.hanbay.hotel.payment.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidRefundStateException extends DomainException {
    public InvalidRefundStateException(String message) {
        super("REFUND_STATE_INVALID", message, HttpStatus.CONFLICT);
    }
}
