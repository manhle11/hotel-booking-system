package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.room.domain.RoomStatus;
import java.util.UUID;

public record AssignedRoomResponse(UUID roomId, String roomNumber, int floor, RoomStatus status) {}
