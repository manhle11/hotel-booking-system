package com.hanbay.hotel.room.dto;

import com.hanbay.hotel.room.domain.BedType;
import com.hanbay.hotel.room.domain.RoomTypeBed;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public record RoomTypeBedResponse(BedType bedType, int quantity) {
    public static List<RoomTypeBedResponse> from(Collection<RoomTypeBed> beds) {
        return beds.stream()
                .sorted(Comparator.comparing(bed -> bed.getBedType().name()))
                .map(bed -> new RoomTypeBedResponse(bed.getBedType(), bed.getQuantity()))
                .toList();
    }
}
