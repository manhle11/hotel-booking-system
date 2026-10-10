package com.hanbay.hotel.room.dto.response;

import com.hanbay.hotel.room.domain.RoomStatus;
import java.util.UUID;

public record PhysicalRoomListItemResponse(UUID roomId, String roomNumber, int floor, RoomStatus status) {}
