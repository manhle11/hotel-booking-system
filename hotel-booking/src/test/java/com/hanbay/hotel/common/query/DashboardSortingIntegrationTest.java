package com.hanbay.hotel.common.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingGuest;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.maintenance.domain.MaintenanceRequest;
import com.hanbay.hotel.payment.domain.Payment;
import com.hanbay.hotel.payment.domain.PaymentRefund;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.domain.PaymentWebhookEvent;
import com.hanbay.hotel.payment.domain.WebhookDeduplicationStrategy;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.user.domain.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
@WithMockUser(roles = "MANAGER")
class DashboardSortingIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-03T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        props.add("spring.datasource.username", POSTGRES::getUsername);
        props.add("spring.datasource.password", POSTGRES::getPassword);
        props.add("spring.docker.compose.enabled", () -> false);
    }

    @TestConfiguration
    static class Time {
        @Bean
        @Primary
        Clock sortingClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DashboardTableQuery query;

    @Autowired
    MockMvc mvc;

    private final List<Room> rooms = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<PaymentWebhookEvent> events = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();

    @BeforeEach
    void fixture() {
        // Only the isolated Testcontainers database; never the developer's configured database.
        jdbc.execute("TRUNCATE TABLE room_types CASCADE");
        rooms.clear();
        payments.clear();
        events.clear();
        bookings.clear();
        var manager = User.builder()
                .email("sort-" + UUID.randomUUID() + "@example.com")
                .fullName("Sorting fixture manager")
                .build();
        em.persist(manager);
        String[] names = {"Zeta", "Alpha", "Đào"};
        String[] numbers = {"100", "2", "10"};
        int[] amounts = {100, 2, 20};
        for (int i = 0; i < 3; i++) {
            var type = RoomType.builder()
                    .code("SORT-" + i)
                    .name(names[i])
                    .basePrice(amounts[i])
                    .maxGuests(i == 1 ? 10 : i + 2)
                    .capacityAdults(i == 1 ? 10 : i + 2)
                    .build();
            em.persist(type);
            var room = Room.builder()
                    .roomType(type)
                    .roomNumber(numbers[i])
                    .floor(i + 1)
                    .status(i == 1 ? RoomStatus.CLEANING : RoomStatus.DIRTY)
                    .note(i == 0 ? null : names[i])
                    .build();
            em.persist(room);
            rooms.add(room);
            var booking = Booking.builder()
                    .bookingCode("SORT-BOOK-" + i)
                    .roomType(type)
                    .status(BookingStatus.CHECKED_IN)
                    .checkInDate(TODAY.minusDays(5 - i))
                    .checkOutDate(i == 1 ? TODAY.plusDays(2) : TODAY.minusDays(i))
                    .adultCount(i == 1 ? 10 : i + 1)
                    .totalAmount(amounts[i])
                    .paidAmount(i)
                    .build();
            em.persist(booking);
            bookings.add(booking);
            em.persist(BookingGuest.builder()
                    .booking(booking)
                    .fullName(names[i])
                    .isPrimary(true)
                    .build());
            var payment = Payment.builder()
                    .booking(booking)
                    .paymentCode("SORT-PAY-" + i)
                    .amount(amounts[i])
                    .status(i == 1 ? PaymentStatus.PENDING : PaymentStatus.SUCCESS)
                    .paidAt(i == 1 ? null : NOW.minusSeconds(i * 60L))
                    .expiresAt(NOW.plusSeconds(600))
                    .build();
            em.persist(payment);
            payments.add(payment);
            var event = PaymentWebhookEvent.builder()
                    .eventId(numbers[i])
                    .payment(i == 2 ? null : payment)
                    .deduplicationKey("SORT-EVENT-" + i)
                    .deduplicationStrategy(WebhookDeduplicationStrategy.EVENT_ID)
                    .receivedAt(NOW.minusSeconds(i * 60L))
                    .processingAttempts(i)
                    .payload(Map.of("gateway", names[i], "transferAmount", i == 0 ? "1000" : i == 1 ? 20 : "invalid"))
                    .build();
            em.persist(event);
            events.add(event);
            em.persist(PaymentRefund.builder()
                    .payment(payment)
                    .amount(BigDecimal.valueOf(amounts[i]))
                    .requestedAt(NOW.minusSeconds(i * 60L))
                    .idempotencyKey("SORT-REFUND-" + i)
                    .reason(names[i])
                    .bankName(names[i])
                    .accountHolderName(names[i])
                    .accountNumber("00100" + i)
                    .build());
            em.persist(MaintenanceRequest.create(room, names[i], null, manager));
        }
        em.flush();
    }

    static Stream<String> sorts() {
        return Stream.of(
                        "ROOM_TYPES:code,name,basePrice,maxGuests,status",
                        "ROOMS:roomNumber,roomTypeName,floor,status,note",
                        "HOUSEKEEPING:roomNumber,roomTypeName,floor,note,status",
                        "MAINTENANCE:roomNumber,title,status,createdAt",
                        "DEPARTURES:bookingCode,guestName,roomTypeName,stay,guests,status,totalAmount,paidAmount,outstandingAmount",
                        "PAYMENTS:bookingCode,paymentCode,amount,status,createdAt,paidAt",
                        "WEBHOOKS:eventId,gateway,paymentCode,transferAmount,status,attempts,receivedAt",
                        "REFUNDS:bookingCode,paymentCode,amount,bankAccount,reason,requestedAt,status")
                .flatMap(group -> {
                    var parts = group.split(":");
                    return Arrays.stream(parts[1].split(",")).map(field -> parts[0] + ":" + field);
                });
    }

    private List<UUID> ids(DashboardTableQuery.Table table, String sort, int page, int size) {
        return query.findAll(table, sort, page, size, ids -> ids, Function.identity())
                .getContent();
    }

    @ParameterizedTest
    @MethodSource("sorts")
    void everyVisibleColumnSortsBothDirectionsInPostgresWithoutLosingRows(String input) {
        String[] parts = input.split(":");
        var table = DashboardTableQuery.Table.valueOf(parts[0]);
        var asc = ids(table, parts[1] + ",asc", 0, 10);
        var desc = ids(table, parts[1] + ",desc", 0, 10);
        assertThat(asc).hasSize(3).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(desc);
        assertThat(ids(table, parts[1] + ",asc", 0, 10)).containsExactlyElementsOf(asc);
    }

    @Test
    void numericRoomNumbersSortAcrossPageBoundariesAndTextHandlesVietnamese() {
        assertThat(ids(DashboardTableQuery.Table.ROOMS, "roomNumber,asc", 0, 2))
                .containsExactly(rooms.get(1).getId(), rooms.get(2).getId());
        assertThat(ids(DashboardTableQuery.Table.ROOMS, "roomNumber,asc", 1, 2))
                .containsExactly(rooms.get(0).getId());
        assertThat(ids(DashboardTableQuery.Table.ROOMS, "roomTypeName,asc", 0, 10))
                .containsExactly(
                        rooms.get(1).getId(), rooms.get(2).getId(), rooms.get(0).getId());
    }

    @Test
    void numericJsonAmountsHandleMalformedPayloadAndNullPayments() {
        assertThat(ids(DashboardTableQuery.Table.WEBHOOKS, "transferAmount,asc", 0, 10))
                .containsExactly(
                        events.get(2).getId(),
                        events.get(1).getId(),
                        events.get(0).getId());
        assertThat(ids(DashboardTableQuery.Table.WEBHOOKS, "paymentCode,desc", 0, 10))
                .endsWith(events.get(2).getId());
    }

    @Test
    void filtersRemainAppliedAndNullDatesStayLastInBothDirections() {
        var filtered = query.findAll(
                DashboardTableQuery.Table.PAYMENTS,
                "amount,desc",
                0,
                10,
                "SORT-BOOK",
                "SUCCESS",
                ids -> ids,
                Function.identity());
        assertThat(filtered.getTotalElements()).isEqualTo(2);
        assertThat(filtered.getContent())
                .containsExactly(payments.get(0).getId(), payments.get(2).getId());
        assertThat(ids(DashboardTableQuery.Table.PAYMENTS, "paidAt,asc", 0, 10))
                .endsWith(payments.get(1).getId());
        assertThat(ids(DashboardTableQuery.Table.PAYMENTS, "paidAt,desc", 0, 10))
                .endsWith(payments.get(1).getId());
        assertThat(ids(DashboardTableQuery.Table.DEPARTURES, "guests,asc", 0, 10))
                .containsExactly(
                        bookings.get(0).getId(),
                        bookings.get(2).getId(),
                        bookings.get(1).getId());
        assertThat(ids(DashboardTableQuery.Table.DEPARTURES, "status,asc", 0, 10))
                .startsWith(bookings.get(1).getId());
    }

    @Test
    void httpSortIsGlobalAndInvalidSortReturns400WithoutChangingData() throws Exception {
        mvc.perform(get("/api/v1/management/rooms")
                        .param("sort", "roomNumber,asc")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("2"))
                .andExpect(jsonPath("$.data.content[1].roomNumber").value("10"))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mvc.perform(get("/api/v1/management/rooms")
                        .param("sort", "roomNumber,asc")
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("100"));
        mvc.perform(get("/api/v1/payment-monitoring/payments")
                        .param("sort", "amount,asc")
                        .param("keyword", "SORT-BOOK")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].amount").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/management/room-types").param("sort", "basePrice,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].basePrice").value(100));
        mvc.perform(get("/api/v1/maintenance-requests").param("sort", "roomNumber,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("2"));
        mvc.perform(get("/api/v1/front-desk/departures/today").param("sort", "guests,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].adults").value(10));
        mvc.perform(get("/api/v1/payment-monitoring/webhook-events").param("sort", "transferAmount,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transferAmount").value(0));
        mvc.perform(get("/api/v1/payment-monitoring/refunds").param("sort", "amount,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].amount").value(100));
        mvc.perform(get("/api/v1/maintenance-requests").param("sort", "untrusted,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(3);
    }

    @Test
    @WithMockUser(roles = "HOUSEKEEPER")
    void housekeepingSortsAcrossPagesAndOnlyReturnsRoomsStillRequiringCleaning() throws Exception {
        for (var status : List.of(RoomStatus.AVAILABLE, RoomStatus.OCCUPIED, RoomStatus.MAINTENANCE)) {
            em.persist(Room.builder()
                    .roomType(rooms.get(0).getRoomType())
                    .roomNumber("OTHER-" + status)
                    .floor(1)
                    .status(status)
                    .build());
        }
        em.flush();
        em.clear();
        String endpoint = "/api/v1/housekeeping/rooms";
        mvc.perform(get(endpoint).param("sort", "roomNumber,asc").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("2"))
                .andExpect(jsonPath("$.data.content[1].roomNumber").value("10"));
        mvc.perform(get(endpoint)
                        .param("sort", "roomNumber,asc")
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("100"));
        mvc.perform(get(endpoint).param("sort", "roomTypeName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomType.name").value("Alpha"));
        mvc.perform(get(endpoint).param("sort", "floor,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].floor").value(3));
        mvc.perform(get(endpoint).param("sort", "status,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("DIRTY"))
                .andExpect(jsonPath("$.data.content[2].status").value("CLEANING"));
        for (var direction : List.of("asc", "desc")) {
            mvc.perform(get(endpoint).param("sort", "note," + direction))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[2].note").isEmpty());
        }
        mvc.perform(get(endpoint).param("sort", "status;drop table rooms,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").exists());

        mvc.perform(patch(endpoint + "/" + rooms.get(1).getId() + "/complete-cleaning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        // The test wraps requests in one transaction; flush to model a committed PATCH before the next GET.
        em.flush();
        mvc.perform(get(endpoint).param("sort", "roomNumber,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("10"))
                .andExpect(jsonPath("$.data.content[1].roomNumber").value("100"));
        mvc.perform(patch(endpoint + "/" + rooms.get(0).getId() + "/start-cleaning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLEANING"));
        em.flush();
        mvc.perform(get(endpoint).param("sort", "status,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomNumber").value("100"));
    }

    @Test
    void sortedHousekeepingEndpointStillRequiresHousekeeperRole() throws Exception {
        mvc.perform(get("/api/v1/housekeeping/rooms").param("sort", "roomNumber,asc"))
                .andExpect(status().isForbidden());
    }
}
