package com.hanbay.hotel.room.dto.response;

import com.hanbay.hotel.room.domain.RoomStatus;
import java.util.UUID;

public record RoomMaintenanceResponse(UUID roomId, String roomNumber, RoomStatus status) {}
