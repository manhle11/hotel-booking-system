package com.hanbay.hotel.room.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RoomTypeBedConfigurationConflictException extends DomainException {
    public RoomTypeBedConfigurationConflictException() {
        super(
                "ROOM_TYPE_BEDS_LOCKED",
                "Loại phòng đã có đặt phòng. Cấu hình giường được giữ nguyên để bảo toàn lịch sử; vui lòng tạo loại phòng mới.",
                HttpStatus.CONFLICT);
    }
}
