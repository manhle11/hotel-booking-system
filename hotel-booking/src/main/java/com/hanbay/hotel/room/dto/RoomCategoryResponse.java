package com.hanbay.hotel.room.dto;

import com.hanbay.hotel.room.domain.RoomCategory;
import java.util.UUID;

public record RoomCategoryResponse(UUID id, String code, String name) {
    public static RoomCategoryResponse from(RoomCategory category) {
        return category == null
                ? null
                : new RoomCategoryResponse(category.getId(), category.getCode(), category.getName());
    }
}
