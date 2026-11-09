package com.hanbay.hotel.room.dto;

import com.hanbay.hotel.room.domain.*;
import java.math.BigDecimal;
import java.util.*;

public record RoomTypeManagementResponse(
        UUID id,
        String code,
        String name,
        String description,
        String shortDescription,
        long basePrice,
        int maxGuests,
        int capacityAdults,
        int capacityChildren,
        String bedType,
        BigDecimal areaM2,
        RoomTypeStatus status,
        List<String> images,
        Set<UUID> amenityIds,
        RoomCategoryResponse category,
        List<RoomTypeBedResponse> beds,
        BedConfigurationStatus bedConfigurationStatus,
        RoomViewType viewType,
        Boolean hasBalcony,
        Boolean highFloor) {
    public static RoomTypeManagementResponse from(RoomType r) {
        return new RoomTypeManagementResponse(
                r.getId(),
                r.getCode(),
                r.getName(),
                r.getDescription(),
                r.getShortDescription(),
                r.getBasePrice(),
                r.getMaxGuests(),
                r.getCapacityAdults(),
                r.getCapacityChildren(),
                r.getBedType(),
                r.getAreaM2(),
                r.getStatus(),
                r.getRoomTypeImages().stream()
                        .sorted(Comparator.comparingInt(RoomTypeImage::getSortOrder))
                        .map(RoomTypeImage::getImageUrl)
                        .toList(),
                r.getAmenities().stream().map(Amenity::getId).collect(java.util.stream.Collectors.toSet()),
                RoomCategoryResponse.from(r.getCategory()),
                RoomTypeBedResponse.from(r.getBeds()),
                r.getBedConfigurationStatus(),
                r.getViewType(),
                r.getHasBalcony(),
                r.getHighFloor());
    }
}
