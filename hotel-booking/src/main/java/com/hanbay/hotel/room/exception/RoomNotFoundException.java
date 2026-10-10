package com.hanbay.hotel.room.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RoomNotFoundException extends DomainException {

    public RoomNotFoundException(String message) {
        super("ROOM_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
