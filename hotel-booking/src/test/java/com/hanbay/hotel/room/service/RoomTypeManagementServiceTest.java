package com.hanbay.hotel.room.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.common.query.DashboardTableQuery;
import com.hanbay.hotel.media.ImageStorageService;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.request.*;
import com.hanbay.hotel.room.exception.*;
import com.hanbay.hotel.room.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomTypeManagementServiceTest {
    private final RoomTypeRepository types = mock(RoomTypeRepository.class);
    private final RoomCategoryRepository categories = mock(RoomCategoryRepository.class);
    private final RoomTypeManagementService service = new RoomTypeManagementService(
            types,
            mock(AmenityRepository.class),
            mock(ImageStorageService.class),
            mock(DashboardTableQuery.class),
            categories,
            new com.hanbay.hotel.booking.policy.HotelStayPolicy(
                    java.time.Clock.systemUTC(), new com.hanbay.hotel.booking.config.HotelStayProperties()));
    private final UUID id = UUID.randomUUID();
    private final RoomCategory category = RoomCategory.builder()
            .id(UUID.randomUUID())
            .code("FAMILY")
            .name("Family")
            .build();
    private RoomType type;

    @BeforeEach
    void setUp() {
        type = RoomType.builder()
                .id(id)
                .code("TEST")
                .name("Test")
                .maxGuests(3)
                .capacityAdults(2)
                .capacityChildren(1)
                .bedType("Thông tin chưa xác minh")
                .build();
        when(types.findByIdForUpdate(id)).thenReturn(Optional.of(type));
        when(types.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void legacyUpdatePreservesUnknownBedsAndCapacityWithoutGuessing() {
        var result = service.save(id, request(null, null, null));
        assertThat(result.beds()).isEmpty();
        assertThat(result.bedConfigurationStatus()).isEqualTo(BedConfigurationStatus.UNCONFIGURED);
        assertThat(result.bedType()).isEqualTo("Thông tin chưa xác minh");
        assertThat(result.category()).isNull();
        assertThat(result.maxGuests()).isEqualTo(3);
        assertThat(result.capacityAdults()).isEqualTo(2);
        assertThat(result.capacityChildren()).isEqualTo(1);
        verify(types, never()).hasBookingHistory(any());
    }

    @Test
    void createsFixedMixedBedsWithoutDerivingCapacity() {
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        var result = service.save(
                null, request(category.getId(), List.of(bed(BedType.SINGLE, 3), bed(BedType.KING, 2)), null));
        assertThat(result.beds()).extracting("bedType").containsExactly(BedType.KING, BedType.SINGLE);
        assertThat(result.bedType()).isEqualTo("2 KING + 3 SINGLE");
        assertThat(result.category().id()).isEqualTo(category.getId());
        assertThat(result.bedConfigurationStatus()).isEqualTo(BedConfigurationStatus.CONFIGURED);
        assertThat(result.maxGuests()).isEqualTo(3);
        assertThat(result.capacityAdults()).isEqualTo(2);
        assertThat(result.capacityChildren()).isEqualTo(1);
    }

    @Test
    void newTypesRequireExplicitCategoryAndBeds() {
        assertThatThrownBy(() -> service.save(null, request(null, null, "King")))
                .isInstanceOf(RoomValidationException.class);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        assertThatThrownBy(() -> service.save(null, request(category.getId(), null, "King")))
                .isInstanceOf(RoomValidationException.class);
        assertThatThrownBy(() -> service.save(null, request(null, List.of(bed(BedType.KING, 1)), null)))
                .isInstanceOf(RoomValidationException.class);
        verify(types, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingCategory() {
        assertThatThrownBy(() -> service.save(id, request(UUID.randomUUID(), null, null)))
                .isInstanceOf(RoomValidationException.class);
    }

    @Test
    void rejectsDuplicateBedsRatherThanSummingThem() {
        assertThatThrownBy(() ->
                        service.save(id, request(null, List.of(bed(BedType.KING, 1), bed(BedType.KING, 2)), null)))
                .isInstanceOf(RoomValidationException.class)
                .hasMessageContaining("một lần");
        verify(types, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEmptyNullAndNonPositiveBedEntries() {
        for (var beds : List.of(
                List.<RoomTypeBedRequest>of(),
                Arrays.asList((RoomTypeBedRequest) null),
                List.of(bed(null, 1)),
                List.of(bed(BedType.SINGLE, 0)),
                List.of(bed(BedType.SINGLE, -1)))) {
            assertThatThrownBy(() -> service.save(id, request(null, beds, null)))
                    .isInstanceOf(RoomValidationException.class);
        }
        verify(types, never()).saveAndFlush(any());
    }

    @Test
    void cannotInitializeUnknownBedsAfterAnyBooking() {
        when(types.hasBookingHistory(id)).thenReturn(true);
        assertThatThrownBy(() -> service.save(id, request(null, List.of(bed(BedType.KING, 1)), null)))
                .isInstanceOf(RoomTypeBedConfigurationConflictException.class);
        assertThat(type.getBeds()).isEmpty();
        verify(types, never()).saveAndFlush(any());
    }

    @Test
    void cannotChangeBedTypeQuantityOrRemoveBedsAfterAnyBooking() {
        type.replaceBeds(Map.of(BedType.KING, 1, BedType.SINGLE, 2));
        when(types.hasBookingHistory(id)).thenReturn(true);
        for (var beds : List.of(
                List.of(bed(BedType.QUEEN, 1)),
                List.of(bed(BedType.KING, 2), bed(BedType.SINGLE, 2)),
                List.of(bed(BedType.KING, 1)))) {
            assertThatThrownBy(() -> service.save(id, request(null, beds, null)))
                    .isInstanceOf(RoomTypeBedConfigurationConflictException.class);
        }
        assertThat(type.bedConfiguration())
                .containsExactlyInAnyOrderEntriesOf(Map.of(BedType.KING, 1, BedType.SINGLE, 2));
    }

    @Test
    void legacyTextCannotBypassHistoryProtection() {
        when(types.hasBookingHistory(id)).thenReturn(true);
        assertThatThrownBy(() -> service.save(id, request(null, null, "2 Queen")))
                .isInstanceOf(RoomTypeBedConfigurationConflictException.class);
        assertThat(type.getBedType()).isEqualTo("Thông tin chưa xác minh");
    }

    @Test
    void legacyTextCannotOverrideStructuredBeds() {
        type.replaceBeds(Map.of(BedType.KING, 1));
        assertThatThrownBy(() -> service.save(id, request(null, null, "2 Queen")))
                .isInstanceOf(RoomValidationException.class);
        assertThat(type.getBedType()).isEqualTo("1 KING");
    }

    @Test
    void identicalReorderedConfigurationIsAllowedAndReusesRows() {
        type.replaceBeds(Map.of(BedType.KING, 1, BedType.SINGLE, 2));
        var original = List.copyOf(type.getBeds());
        service.save(id, request(null, List.of(bed(BedType.SINGLE, 2), bed(BedType.KING, 1)), "1 KING + 2 SINGLE"));
        assertThat(type.getBeds()).containsExactlyInAnyOrderElementsOf(original);
        verify(types, never()).hasBookingHistory(any());
    }

    @Test
    void omittedBedsPreserveConfiguredType() {
        type.replaceBeds(Map.of(BedType.DOUBLE, 2));
        var result = service.save(id, request(null, null, null));
        assertThat(result.bedType()).isEqualTo("2 DOUBLE");
        assertThat(type.bedConfiguration()).containsEntry(BedType.DOUBLE, 2);
    }

    @Test
    void unbookedTypeCanChangeBedsAndRetainsCapacityPolicy() {
        type.replaceBeds(Map.of(BedType.SINGLE, 2));
        service.save(id, request(null, List.of(bed(BedType.SINGLE, 1), bed(BedType.QUEEN, 1)), null));
        assertThat(type.bedConfiguration())
                .containsExactlyInAnyOrderEntriesOf(Map.of(BedType.SINGLE, 1, BedType.QUEEN, 1));
        assertThat(type.canAccommodate(2, 1)).isTrue();
        assertThat(type.canAccommodate(3, 0)).isFalse();
        assertThat(type.canAccommodate(2, 2)).isFalse();
    }

    @Test
    void deleteAlwaysDeactivatesWithoutDeletingRelations() {
        type.replaceBeds(Map.of(BedType.KING, 1));
        service.delete(id);
        service.delete(id);
        assertThat(type.getStatus()).isEqualTo(RoomTypeStatus.INACTIVE);
        assertThat(type.getBeds()).hasSize(1);
        verify(types, never()).delete(any());
    }

    private RoomTypeBedRequest bed(BedType bedType, int quantity) {
        return new RoomTypeBedRequest(bedType, quantity);
    }

    private RoomTypeWriteRequest request(UUID categoryId, List<RoomTypeBedRequest> beds, String legacyBedType) {
        return new RoomTypeWriteRequest(
                "TEST",
                "Test",
                null,
                null,
                1000000,
                3,
                2,
                1,
                legacyBedType,
                null,
                RoomTypeStatus.ACTIVE,
                List.of(),
                Set.of(),
                categoryId,
                beds,
                null,
                null,
                null);
    }
}
