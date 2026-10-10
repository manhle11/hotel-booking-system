package com.hanbay.hotel.booking.dto.response;

import java.util.UUID;

public record AvailablePhysicalRoomResponse(UUID roomId, String roomNumber, int floor) {}
