package com.hanbay.hotel.housekeeping.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.housekeeping.dto.response.DirtyRoomResponse;
import java.util.UUID;

public interface HousekeepingService {

    PageResponse<DirtyRoomResponse> getDirtyRooms(int page, int size);

    PageResponse<DirtyRoomResponse> getRoomsRequiringCleaning(int page, int size);

    PageResponse<DirtyRoomResponse> getRoomsRequiringCleaning(int page, int size, String sort);

    DirtyRoomResponse startCleaning(UUID roomId);

    DirtyRoomResponse completeCleaning(UUID roomId);
}
