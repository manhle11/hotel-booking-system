package com.hanbay.hotel.booking.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BookingValidationException extends DomainException {
    public BookingValidationException(String message) {
        super("BOOKING_VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
    }
}
