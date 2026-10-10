package com.hanbay.hotel.booking.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BookingCancellationNotAllowedException extends DomainException {
    public BookingCancellationNotAllowedException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
