package com.hanbay.hotel.roommap;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class FloorPlanConflictException extends DomainException {
    public FloorPlanConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
