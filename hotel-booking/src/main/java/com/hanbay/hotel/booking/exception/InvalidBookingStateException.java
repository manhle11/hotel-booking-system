package com.hanbay.hotel.booking.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidBookingStateException extends DomainException {
    public InvalidBookingStateException(String message) {
        super("BOOKING_STATE_INVALID", message, HttpStatus.CONFLICT);
    }
}
