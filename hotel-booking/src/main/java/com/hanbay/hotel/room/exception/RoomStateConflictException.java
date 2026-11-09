package com.hanbay.hotel.room.exception;

import com.hanbay.hotel.common.exception.DomainException;
import com.hanbay.hotel.room.domain.RoomStatus;
import org.springframework.http.HttpStatus;

public class RoomStateConflictException extends DomainException {

    private RoomStateConflictException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }

    public static RoomStateConflictException bookingHistory() {
        return new RoomStateConflictException(
                "ROOM_HAS_BOOKING_HISTORY",
                "Phòng đã có lịch sử lưu trú. Không thể xóa để bảo toàn thông tin phòng trên các đơn cũ.");
    }

    public static RoomStateConflictException activeMaintenanceRequest() {
        return new RoomStateConflictException(
                "ROOM_HAS_ACTIVE_MAINTENANCE_REQUEST",
                "Phòng có yêu cầu bảo trì chưa hoàn tất. Vui lòng xử lý tại màn hình quản lý bảo trì");
    }

    public static RoomStateConflictException notAvailable() {
        return new RoomStateConflictException("ROOM_NOT_AVAILABLE", "Phòng đã chọn không còn trống");
    }

    public static RoomStateConflictException notOccupied() {
        return new RoomStateConflictException("ROOM_NOT_OCCUPIED", "Phòng không còn ở trạng thái đang sử dụng");
    }

    public static RoomStateConflictException cannotStartCleaning(RoomStatus status) {
        return new RoomStateConflictException(
                "ROOM_CLEANING_START_CONFLICT", "Không thể bắt đầu dọn khi phòng đang ở trạng thái " + status);
    }

    public static RoomStateConflictException cannotCompleteCleaning(RoomStatus status) {
        return new RoomStateConflictException(
                "ROOM_CLEANING_COMPLETE_CONFLICT", "Không thể hoàn tất dọn khi phòng đang ở trạng thái " + status);
    }

    public static RoomStateConflictException cannotStartMaintenance(RoomStatus status) {
        return new RoomStateConflictException(
                "ROOM_MAINTENANCE_START_CONFLICT", "Không thể bắt đầu bảo trì khi phòng đang ở trạng thái " + status);
    }

    public static RoomStateConflictException notUnderMaintenance(RoomStatus status) {
        return new RoomStateConflictException(
                "ROOM_MAINTENANCE_END_CONFLICT", "Không thể kết thúc bảo trì khi phòng đang ở trạng thái " + status);
    }
}
