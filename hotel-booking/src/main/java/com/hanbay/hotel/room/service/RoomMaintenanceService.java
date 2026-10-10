package com.hanbay.hotel.room.service;

import com.hanbay.hotel.room.domain.RoomMaintenanceAction;
import com.hanbay.hotel.room.dto.response.RoomMaintenanceResponse;

public interface RoomMaintenanceService {
    RoomMaintenanceResponse changeMaintenanceStatus(String roomNumber, RoomMaintenanceAction action);
}
