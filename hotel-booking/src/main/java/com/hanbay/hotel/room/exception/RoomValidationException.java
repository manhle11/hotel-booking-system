package com.hanbay.hotel.room.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RoomValidationException extends DomainException {
    public RoomValidationException(String message) {
        super("ROOM_VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
    }
}
