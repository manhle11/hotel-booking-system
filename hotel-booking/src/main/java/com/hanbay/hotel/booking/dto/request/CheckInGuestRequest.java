package com.hanbay.hotel.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CheckInGuestRequest(
        @NotNull(message = "Room ID không được để trống") UUID roomId,
        @Size(max = 500, message = "Lý do nhận phòng sớm không được vượt quá 500 ký tự") String earlyArrivalReason) {
    public CheckInGuestRequest(UUID roomId) {
        this(roomId, null);
    }
}
