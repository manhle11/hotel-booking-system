package com.hanbay.hotel.assignment;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AssignmentException extends DomainException {
    public AssignmentException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }

    public static AssignmentException stale() {
        return new AssignmentException("ASSIGNMENT_STALE", "Lịch đã cập nhật, vui lòng lấy gợi ý mới.");
    }
}
