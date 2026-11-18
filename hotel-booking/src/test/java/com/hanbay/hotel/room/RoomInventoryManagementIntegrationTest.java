package com.hanbay.hotel.room;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.request.RoomWriteRequest;
import com.hanbay.hotel.room.exception.RoomValidationException;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import com.hanbay.hotel.room.service.RoomManagementService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RoomInventoryManagementIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-09T18:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Time {
        @Bean
        @Primary
        Clock inventoryTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private RoomManagementService service;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomTypeRepository types;

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

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = "NO_SHOW", mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(roles = "MANAGER")
    void deletingReferencedRoomReturnsConflictAndKeepsHistoricalAssignment(BookingStatus status) throws Exception {
        Room room = room(type());
        Booking past =
                booking(room.getRoomType(), status, TODAY.minusDays(5), TODAY.minusDays(3), NOW.minusSeconds(60));
        jdbc.update("UPDATE bookings SET assigned_room_id = ? WHERE id = ?", room.getId(), past.getId());
        entityManager.clear();

        mvc.perform(delete("/api/v1/management/rooms/" + room.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_BOOKING_HISTORY"));

        assertThat(rooms.existsById(room.getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT assigned_room_id FROM bookings WHERE id = ?", UUID.class, past.getId()))
                .isEqualTo(room.getId());
        assertThat(service.detail(room.getId()).roomNumber()).isEqualTo(room.getRoomNumber());
    }

    @Test
    void unusedRoomCanBeDeletedWhileAnotherRoomKeepsItsHistoricalBooking() {
        Room used = room(type()), unused = room(used.getRoomType());
        Booking past =
                booking(used.getRoomType(), BookingStatus.CHECKED_OUT, TODAY.minusDays(5), TODAY.minusDays(3), null);
        jdbc.update("UPDATE bookings SET assigned_room_id = ? WHERE id = ?", used.getId(), past.getId());
        entityManager.clear();

        service.delete(unused.getId());

        assertThat(rooms.existsById(unused.getId())).isFalse();
        assertThat(rooms.existsById(used.getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT assigned_room_id FROM bookings WHERE id = ?", UUID.class, past.getId()))
                .isEqualTo(used.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void expiredPendingAllowsMovingPhysicalInventoryWithoutWaitingForScheduler(int secondsUntilExpiry) {
        Room room = room(type());
        RoomType target = type();
        booking(
                room.getRoomType(),
                BookingStatus.PENDING,
                TODAY.plusDays(1),
                TODAY.plusDays(3),
                NOW.plusSeconds(secondsUntilExpiry));
        assertThat(availability.countAvailableRooms(room.getRoomType().getId(), TODAY.plusDays(1), TODAY.plusDays(3)))
                .isEqualTo(1);

        var result =
                service.update(room.getId(), new RoomWriteRequest(target.getId(), room.getRoomNumber(), 1, "Moved"));

        assertThat(result.roomTypeId()).isEqualTo(target.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void expiredPendingAllowsDeletingAnUnusedRoom(int secondsUntilExpiry) {
        Room room = room(type());
        booking(
                room.getRoomType(),
                BookingStatus.PENDING,
                TODAY.plusDays(1),
                TODAY.plusDays(3),
                NOW.plusSeconds(secondsUntilExpiry));

        service.delete(room.getId());

        assertThat(rooms.existsById(room.getId())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = BookingStatus.class,
            names = {"PENDING", "CONFIRMED", "CHECKED_IN"})
    void liveBookingsStillBlockRemovingOrMovingInventory(BookingStatus status) {
        Room room = room(type());
        RoomType target = type();
        booking(room.getRoomType(), status, TODAY, TODAY.plusDays(2), NOW.plusSeconds(60));

        assertThatThrownBy(() ->
                        service.update(room.getId(), new RoomWriteRequest(target.getId(), room.getRoomNumber(), 1, "")))
                .isInstanceOf(RoomValidationException.class);
        assertThatThrownBy(() -> service.delete(room.getId())).isInstanceOf(RoomValidationException.class);
        assertThat(rooms.findRoomTypeId(room.getId()))
                .contains(room.getRoomType().getId());
    }

    @Test
    void overdueCheckedInGuestStillBlocksRemovingInventory() {
        Room room = room(type());
        booking(room.getRoomType(), BookingStatus.CHECKED_IN, TODAY.minusDays(5), TODAY.minusDays(1), null);

        assertThatThrownBy(() -> service.delete(room.getId())).isInstanceOf(RoomValidationException.class);
        assertThat(rooms.existsById(room.getId())).isTrue();
    }

    @Test
    void completedStayDateUsesHotelDayRatherThanDatabaseDay() {
        Room room = room(type());
        booking(room.getRoomType(), BookingStatus.CONFIRMED, TODAY.minusDays(3), TODAY.minusDays(1), null);

        service.delete(room.getId());

        assertThat(rooms.existsById(room.getId())).isFalse();
    }

    @Test
    void pendingWithoutExpiryDoesNotHoldInventory() {
        Room room = room(type());
        booking(room.getRoomType(), BookingStatus.PENDING, TODAY.plusDays(1), TODAY.plusDays(3), null);
        assertThat(availability.countAvailableRooms(room.getRoomType().getId(), TODAY.plusDays(1), TODAY.plusDays(3)))
                .isEqualTo(1);

        service.delete(room.getId());

        assertThat(rooms.existsById(room.getId())).isFalse();
    }

    private RoomType type() {
        return types.saveAndFlush(RoomType.builder()
                .code("INVENTORY-" + UUID.randomUUID())
                .name("Inventory room")
                .maxGuests(2)
                .capacityAdults(2)
                .capacityChildren(0)
                .basePrice(1000000)
                .build());
    }

    private Room room(RoomType type) {
        return rooms.saveAndFlush(Room.builder()
                .roomType(type)
                .roomNumber("I-" + UUID.randomUUID().toString().substring(0, 12))
                .floor(1)
                .status(RoomStatus.AVAILABLE)
                .build());
    }

    private Booking booking(RoomType type, BookingStatus status, LocalDate from, LocalDate to, Instant expiry) {
        return bookings.saveAndFlush(Booking.builder()
                .bookingCode("B-" + UUID.randomUUID())
                .roomType(type)
                .checkInDate(from)
                .checkOutDate(to)
                .adultCount(1)
                .childCount(0)
                .status(status)
                .expiredAt(expiry)
                .build());
    }
}
