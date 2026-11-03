package com.hanbay.hotel.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class StayInventoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final LocalDate FIRST = LocalDate.of(2026, 11, 5);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @TestConfiguration
    static class Time {
        @Bean
        @Primary
        Clock stayTestClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private RoomAvailabilityCalculator calculator;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RoomTypeRepository roomTypes;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private BookingRepository bookings;

    private RoomType type;

    @Test
    void exposesTheSameHotelClockAndPolicyToUnauthenticatedBookingClients() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/stay-policy"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.zone")
                        .value("Asia/Ho_Chi_Minh"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.hotelToday")
                                .value("2026-11-03"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.checkInTime")
                                .value("14:00:00"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.data.checkOutTime")
                        .value("12:00:00"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.data.paymentHoldMinutes")
                        .value(15));
    }

    @BeforeEach
    void inventory() {
        type = roomTypes.saveAndFlush(RoomType.builder()
                .code("TIME-" + UUID.randomUUID())
                .name("Temporal inventory")
                .capacityAdults(2)
                .maxGuests(2)
                .basePrice(1000000)
                .build());
        for (int number = 0; number < 2; number++) {
            rooms.saveAndFlush(Room.builder()
                    .roomType(type)
                    .roomNumber("T-" + UUID.randomUUID().toString().substring(0, 12))
                    .floor(1)
                    .status(RoomStatus.AVAILABLE)
                    .build());
        }
    }

    private Booking booking(LocalDate from, LocalDate to, BookingStatus status, Instant expiry) {
        return bookings.saveAndFlush(Booking.builder()
                .bookingCode("T-" + UUID.randomUUID())
                .roomType(type)
                .checkInDate(from)
                .checkOutDate(to)
                .status(status)
                .expiredAt(expiry)
                .build());
    }

    @Test
    void adjacentBookingsConsumeOneRoomPerNightRatherThanTwoAcrossTheStay() {
        booking(FIRST, FIRST.plusDays(1), BookingStatus.CONFIRMED, null);
        booking(FIRST.plusDays(1), FIRST.plusDays(2), BookingStatus.CONFIRMED, null);
        assertThat(calculator.countAvailableRooms(type.getId(), FIRST, FIRST.plusDays(2)))
                .isEqualTo(1);
    }

    @Test
    void usesBusiestNightForOverlappingBookingsAndExcludesCurrentBookingWhenEditing() {
        var first = booking(FIRST, FIRST.plusDays(2), BookingStatus.CONFIRMED, null);
        booking(FIRST.plusDays(1), FIRST.plusDays(3), BookingStatus.CONFIRMED, null);
        assertThat(calculator.countAvailableRooms(type.getId(), FIRST, FIRST.plusDays(3)))
                .isZero();
        assertThat(calculator.countAvailableRoomsExcludingBooking(
                        type.getId(), FIRST, FIRST.plusDays(3), first.getId()))
                .isEqualTo(1);
    }

    @Test
    void excludesCheckoutDateAndIgnoresCancelledAndExpiredBookings() {
        booking(FIRST.minusDays(1), FIRST, BookingStatus.CONFIRMED, null);
        booking(FIRST, FIRST.plusDays(2), BookingStatus.CANCELLED, null);
        booking(FIRST, FIRST.plusDays(2), BookingStatus.EXPIRED, null);
        assertThat(calculator.countAvailableRooms(type.getId(), FIRST, FIRST.plusDays(2)))
                .isEqualTo(2);
    }

    @Test
    void releasesPendingInventoryAtExactExpiryAndReservesUnexpiredHold() {
        booking(FIRST, FIRST.plusDays(2), BookingStatus.PENDING, NOW);
        booking(FIRST, FIRST.plusDays(2), BookingStatus.PENDING, NOW.plusSeconds(1));
        assertThat(calculator.countAvailableRooms(type.getId(), FIRST, FIRST.plusDays(2)))
                .isEqualTo(1);
    }

    @Test
    void doesNotSellRoomStillOccupiedByAnOverdueGuest() {
        booking(FIRST.minusDays(5), FIRST.minusDays(3), BookingStatus.CHECKED_IN, null);
        assertThat(calculator.countAvailableRooms(type.getId(), FIRST, FIRST.plusDays(2)))
                .isEqualTo(1);
    }

    @Test
    void excludesRoomsUnderMaintenanceAndNeverReturnsNegativeInventory() {
        rooms.saveAndFlush(Room.builder()
                .roomType(type)
                .roomNumber("M-" + UUID.randomUUID().toString().substring(0, 12))
                .floor(1)
                .status(RoomStatus.MAINTENANCE)
                .build());
        for (int number = 0; number < 3; number++) booking(FIRST, FIRST.plusDays(2), BookingStatus.CONFIRMED, null);
        assertThat(calculator
                        .countAvailableRooms(List.of(type.getId()), FIRST, FIRST.plusDays(2))
                        .get(type.getId()))
                .isZero();
    }
}
