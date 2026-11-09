package com.hanbay.hotel.room.dto;

import com.hanbay.hotel.room.domain.BedConfigurationStatus;
import com.hanbay.hotel.room.domain.RoomViewType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoomTypeCatalogItemResponse(
        UUID id,
        String name,
        String description,
        int capacityAdults,
        int capacityChildren,
        BigDecimal basePrice,
        String thumbnailUrl,
        String bedType,
        RoomCategoryResponse category,
        List<RoomTypeBedResponse> beds,
        BedConfigurationStatus bedConfigurationStatus,
        RoomViewType viewType,
        Boolean hasBalcony,
        Boolean highFloor) {}
