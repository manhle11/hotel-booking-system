package com.hanbay.hotel.room.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.room.dto.response.PhysicalRoomListItemResponse;

public interface RoomService {

    PageResponse<PhysicalRoomListItemResponse> findAll(int page, int size);
}
