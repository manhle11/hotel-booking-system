package com.hanbay.hotel.room.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RoomTypeCapacityConflictException extends DomainException {
    public RoomTypeCapacityConflictException() {
        super(
                "ROOM_TYPE_CAPACITY_CONFLICT",
                "Không thể giảm sức chứa vì có đặt phòng đang lưu trú hoặc sắp tới vượt giới hạn mới.",
                HttpStatus.CONFLICT);
    }
}
