package com.hanbay.hotel.room.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.room.dto.RoomTypeCatalogItemResponse;
import com.hanbay.hotel.room.dto.RoomTypeDetailResponse;
import java.util.UUID;

public interface RoomTypeService {

    PageResponse<RoomTypeCatalogItemResponse> getCatalog(
            int page, int size, String keyword, String sortBy, String direction);

    RoomTypeDetailResponse getDetail(UUID id);

    RoomTypeDetailResponse getDetailByName(String name);
}
