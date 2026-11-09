package com.hanbay.hotel.room.mapper;

import static org.assertj.core.api.Assertions.*;

import com.hanbay.hotel.pricing.dto.result.RoomPricingResult;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RoomTypeBedMappingTest {
    private final RoomTypeMapper mapper = Mappers.getMapper(RoomTypeMapper.class);
    private final RoomPricingResult pricing = new RoomPricingResult(List.of(), 0, 0, 0);

    @Test
    void allRoomApisReturnSameOrderedBedsCategoryAndStatus() {
        RoomType type = type();
        type.assignCategory(RoomCategory.builder()
                .id(UUID.randomUUID())
                .code("FAMILY")
                .name("Family")
                .build());
        type.replaceBeds(Map.of(BedType.SINGLE, 2, BedType.KING, 1));
        var management = RoomTypeManagementResponse.from(type);
        var catalog = mapper.toCatalogResponse(type);
        var detail = mapper.toDetailResponse(type);
        var available = mapper.toAvailableResponse(type, 3, 2, pricing);
        assertThat(management.beds())
                .containsExactly(new RoomTypeBedResponse(BedType.KING, 1), new RoomTypeBedResponse(BedType.SINGLE, 2));
        assertThat(catalog.beds()).isEqualTo(management.beds());
        assertThat(detail.beds()).isEqualTo(management.beds());
        assertThat(available.beds()).isEqualTo(management.beds());
        assertThat(List.of(catalog.category(), detail.category(), available.category()))
                .containsOnly(management.category());
        assertThat(List.of(
                        management.bedConfigurationStatus(),
                        catalog.bedConfigurationStatus(),
                        detail.bedConfigurationStatus(),
                        available.bedConfigurationStatus()))
                .containsOnly(BedConfigurationStatus.CONFIGURED);
        assertThat(List.of(management.bedType(), catalog.bedType(), detail.bedType(), available.bedType()))
                .containsOnly("1 KING + 2 SINGLE");
    }

    @Test
    void exposesFeaturesSeparatelyAndDoesNotGuessOmittedValues() {
        RoomType type = type();
        assertThat(mapper.toDetailResponse(type).viewType()).isNull();
        assertThat(mapper.toDetailResponse(type).hasBalcony()).isNull();
        type.updateFeatures(RoomViewType.SEA, true, true);
        assertThat(mapper.toDetailResponse(type).viewType()).isEqualTo(RoomViewType.SEA);
        assertThat(mapper.toCatalogResponse(type).highFloor()).isTrue();
        assertThat(mapper.toAvailableResponse(type, 1, 1, pricing).hasBalcony()).isTrue();
        type.updateFeatures(null, false, null);
        var response = RoomTypeManagementResponse.from(type);
        assertThat(response.viewType()).isEqualTo(RoomViewType.SEA);
        assertThat(response.hasBalcony()).isFalse();
        assertThat(response.highFloor()).isTrue();
        assertThat(response.capacityAdults()).isEqualTo(2);
        assertThat(response.beds()).isEmpty();
    }

    @Test
    void nullLegacyBedTextRemainsNullAcrossApis() {
        RoomType type = RoomType.builder().capacityAdults(2).maxGuests(2).build();
        assertThat(mapper.toCatalogResponse(type).bedType()).isNull();
        assertThat(mapper.toDetailResponse(type).bedType()).isNull();
        assertThat(mapper.toAvailableResponse(type, 0, 0, pricing).bedType()).isNull();
        assertThat(RoomTypeManagementResponse.from(type).beds()).isEmpty();
    }

    @Test
    void legacyTypesRemainReadableAndDoNotGuessFromTextOrCapacity() {
        RoomType type = type();
        var management = RoomTypeManagementResponse.from(type);
        var catalog = mapper.toCatalogResponse(type);
        var detail = mapper.toDetailResponse(type);
        var available = mapper.toAvailableResponse(type, 1, 1, pricing);
        assertThat(List.of(management.beds(), catalog.beds(), detail.beds(), available.beds()))
                .allSatisfy(beds -> assertThat(beds).isEmpty());
        assertThat(Arrays.asList(management.category(), catalog.category(), detail.category(), available.category()))
                .containsOnlyNulls();
        assertThat(List.of(
                        management.bedConfigurationStatus(),
                        catalog.bedConfigurationStatus(),
                        detail.bedConfigurationStatus(),
                        available.bedConfigurationStatus()))
                .containsOnly(BedConfigurationStatus.UNCONFIGURED);
        assertThat(List.of(management.bedType(), catalog.bedType(), detail.bedType(), available.bedType()))
                .containsOnly("King hoặc Twin");
    }

    private RoomType type() {
        return RoomType.builder()
                .id(UUID.randomUUID())
                .code("LEGACY")
                .name("Legacy")
                .capacityAdults(2)
                .capacityChildren(1)
                .maxGuests(3)
                .bedType("King hoặc Twin")
                .build();
    }
}
