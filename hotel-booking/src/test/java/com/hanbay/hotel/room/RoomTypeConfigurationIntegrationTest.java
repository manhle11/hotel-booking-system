package com.hanbay.hotel.room;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.*;
import com.hanbay.hotel.room.dto.request.*;
import com.hanbay.hotel.room.exception.RoomTypeBedConfigurationConflictException;
import com.hanbay.hotel.room.repository.*;
import com.hanbay.hotel.room.service.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RoomTypeConfigurationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private RoomTypeManagementService service;

    @Autowired
    private RoomTypeService publicTypes;

    @Autowired
    private RoomTypeRepository types;

    @Autowired
    private RoomCategoryRepository categories;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private RoomAvailabilityCalculator availability;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void updatesQuantityAndReplacesBedKindsWithoutDuplicateRows() {
        UUID id = createType();
        var first = types.findById(id).orElseThrow();
        UUID originalKingRow = first.getBeds().stream()
                .filter(b -> b.getBedType() == BedType.KING)
                .findFirst()
                .orElseThrow()
                .getId();
        service.save(
                id,
                request(
                        null,
                        List.of(new RoomTypeBedRequest(BedType.KING, 2), new RoomTypeBedRequest(BedType.QUEEN, 1))));
        entityManager.clear();
        var updated = types.findById(id).orElseThrow();
        assertThat(updated.bedConfiguration())
                .containsExactlyInAnyOrderEntriesOf(Map.of(BedType.KING, 2, BedType.QUEEN, 1));
        assertThat(updated.getBeds().stream()
                        .filter(b -> b.getBedType() == BedType.KING)
                        .findFirst()
                        .orElseThrow()
                        .getId())
                .isEqualTo(originalKingRow);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_type_beds WHERE room_type_id = ?", Integer.class, id))
                .isEqualTo(2);
        assertConsistentApis(id);
    }

    @Test
    void bedsAndSharedCategoriesCannotMultiplyOrCreatePhysicalInventory() {
        UUID first = createType(), second = createType(), empty = createType();
        physicalRoom(first, RoomStatus.AVAILABLE);
        physicalRoom(first, RoomStatus.DIRTY);
        physicalRoom(first, RoomStatus.MAINTENANCE);
        physicalRoom(second, RoomStatus.AVAILABLE);
        LocalDate from = LocalDate.now().plusDays(10), to = from.plusDays(2);
        booking(first, BookingStatus.CONFIRMED, from, to);
        assertThat(availability.countAvailableRooms(List.of(first, second, empty), from, to))
                .containsEntry(first, 1L)
                .containsEntry(second, 1L)
                .containsEntry(empty, 0L);
        assertThat(availability.countReadyRooms(List.of(first, second, empty)))
                .containsEntry(first, 1L)
                .containsEntry(second, 1L)
                .doesNotContainKey(empty);
        assertThat(types.findById(first).orElseThrow().getBeds()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    void everyBookingStatusPreservesBedHistoryAndDeleteOnlyDeactivates(BookingStatus status) {
        UUID id = createType();
        var room = physicalRoom(id, RoomStatus.AVAILABLE);
        var booking = booking(id, status, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3));
        assertThatThrownBy(() -> service.save(id, request(null, List.of(new RoomTypeBedRequest(BedType.QUEEN, 1)))))
                .isInstanceOf(RoomTypeBedConfigurationConflictException.class);
        service.delete(id);
        entityManager.clear();
        assertThat(types.findById(id).orElseThrow().getStatus()).isEqualTo(RoomTypeStatus.INACTIVE);
        assertThat(types.findById(id).orElseThrow().bedConfiguration())
                .containsExactlyInAnyOrderEntriesOf(Map.of(BedType.KING, 1, BedType.SINGLE, 2));
        assertThat(rooms.findById(room.getId()).orElseThrow().getRoomType().getId())
                .isEqualTo(id);
        assertThat(bookings.findById(booking.getId())
                        .orElseThrow()
                        .getRoomType()
                        .getId())
                .isEqualTo(id);
        assertThat(publicTypes.getCatalog(0, 100, id.toString(), "name", "asc").content())
                .isEmpty();
    }

    @Test
    void futureBookingPreventsBedEditsButPermitsIdenticalConfiguration() {
        UUID id = createType();
        booking(
                id,
                BookingStatus.CONFIRMED,
                LocalDate.now().plusDays(20),
                LocalDate.now().plusDays(22));
        assertThatThrownBy(() -> service.save(id, request(null, List.of(new RoomTypeBedRequest(BedType.KING, 2)))))
                .isInstanceOf(RoomTypeBedConfigurationConflictException.class);
        service.save(
                id,
                request(
                        null,
                        List.of(new RoomTypeBedRequest(BedType.SINGLE, 2), new RoomTypeBedRequest(BedType.KING, 1))));
        assertConsistentApis(id);
    }

    @Test
    void legacyBookedTypeRemainsReadableAndCannotBeGuessedDuringUpdate() {
        RoomType legacy = types.saveAndFlush(RoomType.builder()
                .code("L-" + UUID.randomUUID())
                .name("Legacy")
                .capacityAdults(2)
                .capacityChildren(1)
                .maxGuests(3)
                .basePrice(1000000)
                .bedType("King hoặc Twin")
                .build());
        booking(
                legacy.getId(),
                BookingStatus.CONFIRMED,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7));
        var response = service.save(legacy.getId(), request(null, null));
        assertThat(response.beds()).isEmpty();
        assertThat(response.bedType()).isEqualTo("King hoặc Twin");
        assertThat(response.bedConfigurationStatus()).isEqualTo(BedConfigurationStatus.UNCONFIGURED);
        assertThatThrownBy(() ->
                        service.save(legacy.getId(), request(null, List.of(new RoomTypeBedRequest(BedType.KING, 1)))))
                .isInstanceOf(RoomTypeBedConfigurationConflictException.class);
        assertConsistentApis(legacy.getId());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void categoriesCanBeCreatedAndRenamedWithoutChangingInventory() throws Exception {
        String code =
                "CATEGORY_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        long inventoryBefore = rooms.count();
        mvc.perform(post("/api/v1/management/room-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Custom category\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code));
        UUID id = categories.findAll().stream()
                .filter(c -> c.getCode().equals(code))
                .findFirst()
                .orElseThrow()
                .getId();
        mvc.perform(put("/api/v1/management/room-categories/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Renamed category\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed category"));
        assertThat(rooms.count()).isEqualTo(inventoryBefore);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managementHttpExposesCategoryAndConsistentBeds() throws Exception {
        UUID category = categoryId();
        mvc.perform(post("/api/v1/management/room-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                category,
                                "[{\"bedType\":\"SINGLE\",\"quantity\":2},{\"bedType\":\"KING\",\"quantity\":1}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.id").value(category.toString()))
                .andExpect(jsonPath("$.data.beds[0].bedType").value("KING"))
                .andExpect(jsonPath("$.data.beds[1].bedType").value("SINGLE"))
                .andExpect(jsonPath("$.data.bedType").value("1 KING + 2 SINGLE"))
                .andExpect(jsonPath("$.data.bedConfigurationStatus").value("CONFIGURED"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "[null]",
                "[{\"bedType\":\"KING\",\"quantity\":0}]",
                "[{\"bedType\":null,\"quantity\":1}]",
                "[{\"bedType\":\"EXTRA\",\"quantity\":1}]",
                "[{\"bedType\":\"KING\",\"quantity\":1},{\"bedType\":\"KING\",\"quantity\":1}]"
            })
    @WithMockUser(roles = "MANAGER")
    void invalidBedsAreRejectedByHttp(String beds) throws Exception {
        mvc.perform(post("/api/v1/management/room-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(categoryId(), beds)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void httpReturnsConflictWhenEditingBookedBeds() throws Exception {
        UUID id = createType();
        booking(
                id,
                BookingStatus.PENDING,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12));
        mvc.perform(put("/api/v1/management/room-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(categoryId(), "[{\"bedType\":\"QUEEN\",\"quantity\":1}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TYPE_BEDS_LOCKED"));
        mvc.perform(delete("/api/v1/management/room-types/" + id)).andExpect(status().isOk());
        assertThat(service.detail(id).status()).isEqualTo(RoomTypeStatus.INACTIVE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBookingLockPreventsRacingBedChange() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        UUID id = transaction.execute(ignored -> createType());
        CountDownLatch bookingLocked = new CountDownLatch(1),
                releaseBooking = new CountDownLatch(1),
                editStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> booked = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                types.findByIdForUpdate(id).orElseThrow();
                booking(
                        id,
                        BookingStatus.CONFIRMED,
                        LocalDate.now().plusDays(10),
                        LocalDate.now().plusDays(12));
                bookingLocked.countDown();
                await(releaseBooking);
            }));
            assertThat(bookingLocked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> edited = executor.submit(() -> {
                editStarted.countDown();
                service.save(id, request(null, List.of(new RoomTypeBedRequest(BedType.QUEEN, 1))));
            });
            assertThat(editStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> edited.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseBooking.countDown();
            booked.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> edited.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RoomTypeBedConfigurationConflictException.class);
            assertThat(service.detail(id).bedType()).isEqualTo("1 KING + 2 SINGLE");
        } finally {
            releaseBooking.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out waiting for concurrent test transaction");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private UUID createType() {
        return service.save(
                        null,
                        request(
                                categoryId(),
                                List.of(
                                        new RoomTypeBedRequest(BedType.KING, 1),
                                        new RoomTypeBedRequest(BedType.SINGLE, 2))))
                .id();
    }

    private RoomTypeWriteRequest capacityRequest(int adults, int children, int total) {
        var r = request(null, null);
        return new RoomTypeWriteRequest(
                r.code(),
                r.name(),
                r.description(),
                r.shortDescription(),
                r.basePrice(),
                total,
                adults,
                children,
                r.bedType(),
                r.areaM2(),
                r.status(),
                r.images(),
                r.amenityIds(),
                null,
                null,
                null,
                null,
                null);
    }

    @ParameterizedTest
    @EnumSource(
            value = BookingStatus.class,
            names = {"CONFIRMED", "PENDING", "CHECKED_IN"})
    void blocksCapacityReductionForLiveBookings(BookingStatus status) {
        UUID id = createType();
        var b = booking(id, status, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));
        jdbc.update(
                "UPDATE bookings SET adult_count=2, child_count=1, expired_at=CURRENT_TIMESTAMP + interval '15 minutes' WHERE id=?",
                b.getId());
        assertThatThrownBy(() -> service.save(id, capacityRequest(2, 0, 2)))
                .isInstanceOf(com.hanbay.hotel.room.exception.RoomTypeCapacityConflictException.class);
    }

    @Test
    void overdueCheckedInGuestsStillProtectCapacity() {
        UUID id = createType();
        var b = booking(
                id,
                BookingStatus.CHECKED_IN,
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(1));
        jdbc.update("UPDATE bookings SET adult_count=2 WHERE id=?", b.getId());
        assertThatThrownBy(() -> service.save(id, capacityRequest(1, 1, 2)))
                .isInstanceOf(com.hanbay.hotel.room.exception.RoomTypeCapacityConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = BookingStatus.class,
            names = {"CANCELLED", "EXPIRED", "CHECKED_OUT"})
    void historicalOrCancelledBookingsDoNotBlockCapacityChange(BookingStatus status) {
        UUID id = createType();
        var b = booking(id, status, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));
        jdbc.update("UPDATE bookings SET adult_count=2,child_count=1 WHERE id=?", b.getId());
        assertThat(service.save(id, capacityRequest(1, 1, 2)).maxGuests()).isEqualTo(2);
    }

    @Test
    void expiredPendingHoldAndSmallerConfirmedPartyAllowReduction() {
        UUID id = createType();
        var b = booking(
                id,
                BookingStatus.PENDING,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7));
        jdbc.update(
                "UPDATE bookings SET adult_count=2,child_count=1,expired_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?",
                b.getId());
        booking(
                id,
                BookingStatus.CONFIRMED,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7));
        assertThat(service.save(id, capacityRequest(1, 1, 2)).maxGuests()).isEqualTo(2);
    }

    private UUID categoryId() {
        return categories.findAll().stream()
                .filter(c -> c.getCode().equals("FAMILY"))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private RoomTypeWriteRequest request(UUID categoryId, List<RoomTypeBedRequest> beds) {
        return new RoomTypeWriteRequest(
                "T-" + UUID.randomUUID(),
                "Room " + UUID.randomUUID(),
                null,
                null,
                1000000,
                3,
                2,
                1,
                null,
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

    private Room physicalRoom(UUID typeId, RoomStatus status) {
        return rooms.saveAndFlush(Room.builder()
                .roomType(types.findById(typeId).orElseThrow())
                .roomNumber("R-" + UUID.randomUUID().toString().substring(0, 12))
                .status(status)
                .floor(1)
                .build());
    }

    private Booking booking(UUID typeId, BookingStatus status, LocalDate from, LocalDate to) {
        return bookings.saveAndFlush(Booking.builder()
                .bookingCode("B-" + UUID.randomUUID())
                .roomType(types.findById(typeId).orElseThrow())
                .checkInDate(from)
                .checkOutDate(to)
                .status(status)
                .build());
    }

    private void assertConsistentApis(UUID id) {
        var management = service.detail(id);
        var detail = publicTypes.getDetail(id);
        var catalog = publicTypes.getCatalog(0, 100, management.name(), "name", "asc").content().stream()
                .filter(type -> type.id().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(detail.beds()).isEqualTo(management.beds());
        assertThat(catalog.beds()).isEqualTo(management.beds());
        assertThat(detail.bedType()).isEqualTo(management.bedType());
        assertThat(catalog.bedConfigurationStatus()).isEqualTo(management.bedConfigurationStatus());
    }

    private String body(UUID category, String beds) {
        return """
                {"code":"HTTP-%s","name":"HTTP Room","basePrice":1000000,"maxGuests":3,"capacityAdults":2,
                "capacityChildren":1,"status":"ACTIVE","images":[],"amenityIds":[],"categoryId":"%s","beds":%s}
                """
                .formatted(UUID.randomUUID().toString().toUpperCase(Locale.ROOT), category, beds);
    }
}
