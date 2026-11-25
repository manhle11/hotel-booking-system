package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;

import com.hanbay.hotel.booking.domain.BookingNight;
import com.hanbay.hotel.pricing.dto.result.RoomPricingResult;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.AmenityDto;
import com.hanbay.hotel.room.mapper.RoomTypeMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.mapstruct.factory.Mappers;

final class RecommendationFixtures {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T08:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    static final Criteria CRITERIA = Criteria.builder()
            .checkIn(LocalDate.of(2026, 12, 1))
            .checkOut(LocalDate.of(2026, 12, 3))
            .adults(2)
            .children(1)
            .childAges(List.of(5))
            .build();
    static final UUID SUPERIOR = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID DELUXE = UUID.fromString("22222222-2222-4222-8222-222222222222");

    static RecommendedRoom room(UUID id, String name, int area, long price) {
        var type = RoomType.builder()
                .id(id)
                .code(name.replace(' ', '_'))
                .name(name)
                .maxGuests(3)
                .capacityAdults(2)
                .capacityChildren(1)
                .basePrice(price)
                .areaM2(BigDecimal.valueOf(area))
                .viewType(RoomViewType.CITY)
                .build();
        type.replaceBeds(Map.of(BedType.SINGLE, 2));
        var nights = List.of(
                BookingNight.builder()
                        .stayDate(CRITERIA.checkIn())
                        .pricePerNight(price)
                        .build(),
                BookingNight.builder()
                        .stayDate(CRITERIA.checkIn().plusDays(1))
                        .pricePerNight(price)
                        .build());
        var response = Mappers.getMapper(RoomTypeMapper.class)
                .toAvailableResponse(type, 2, 1, new RoomPricingResult(nights, price * 2, 0, price * 2));
        return new RecommendedRoom(
                response,
                List.of(new AmenityDto(UUID.randomUUID(), "Wi-Fi tốc độ cao", "wifi")),
                null,
                List.of(),
                List.of("Đủ sức chứa cho gia đình đã khai báo."),
                List.of());
    }

    static Result result(Criteria criteria, RecommendedRoom... rooms) {
        return new Result(
                Status.RECOMMENDED,
                criteria,
                CLOCK.instant(),
                "VND",
                "ROOM_ONLY",
                "v1",
                List.of(rooms),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    static Result pair() {
        return result(CRITERIA, room(SUPERIOR, "Superior Twin", 30, 1700000), room(DELUXE, "Deluxe Twin", 36, 2500000));
    }
}
