package com.hanbay.hotel.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.request.CheckInGuestRequest;
import com.hanbay.hotel.booking.dto.response.CheckInGuestResponse;
import com.hanbay.hotel.booking.exception.CheckInConflictException;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.CheckInService;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "booking.expiration.cron=-",
            "debug=false",
            "logging.level.root=WARN",
            "logging.level.org.springframework=WARN",
            "logging.level.com.hanbay=WARN"
        })
@Testcontainers(disabledWithoutDocker = true)
class CheckInConcurrencyIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired
    private CheckInService service;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomTypeRepository roomTypes;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private Clock clock;

    private Fixture fixture;

    @BeforeEach
    void seedCommittedFixtures() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        fixture = transactions.execute(status -> {
            var type = roomTypes.saveAndFlush(RoomType.builder()
                    .code("CHECKIN-" + UUID.randomUUID())
                    .name("Check-in concurrency")
                    .basePrice(1000000L)
                    .capacityAdults(2)
                    .capacityChildren(0)
                    .maxGuests(2)
                    .build());
            List<UUID> roomIds = new ArrayList<>();
            List<UUID> bookingIds = new ArrayList<>();
            List<String> bookingCodes = new ArrayList<>();
            List<UUID> staffIds = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                roomIds.add(rooms.saveAndFlush(Room.builder()
                                .roomType(type)
                                .roomNumber("CI-" + UUID.randomUUID().toString().substring(0, 8))
                                .floor(4)
                                .status(RoomStatus.AVAILABLE)
                                .build())
                        .getId());
                var booking = bookings.saveAndFlush(Booking.builder()
                        .bookingCode("CI-" + UUID.randomUUID())
                        .roomType(type)
                        .checkInDate(TODAY)
                        .checkOutDate(TODAY.plusDays(2))
                        .adultCount(2)
                        .status(BookingStatus.CONFIRMED)
                        .totalAmount(2000000L)
                        .paidAmount(2000000L)
                        .build());
                bookingIds.add(booking.getId());
                bookingCodes.add(booking.getBookingCode());
                staffIds.add(users.saveAndFlush(
                                User.builder().fullName("Reception " + i).build())
                        .getId());
            }
            return new Fixture(bookingIds, bookingCodes, roomIds, staffIds);
        });
    }

    @Test
    void twoReceptionistsCannotAssignTheSameRoomToDifferentBookings() throws Exception {
        var outcomes = race("rooms", fixture.roomIds().get(0), request(0, 0, 0), request(1, 0, 1));

        assertThat(outcomes).extracting(Outcome::code).containsExactlyInAnyOrder("SUCCESS", "ROOM_NOT_AVAILABLE");
        var winner =
                outcomes.stream().filter(o -> o.response() != null).findFirst().orElseThrow();
        transactions.executeWithoutResult(status -> {
            var saved = bookings.findAllById(fixture.bookingIds());
            assertThat(saved)
                    .extracting(Booking::getStatus)
                    .containsExactlyInAnyOrder(BookingStatus.CHECKED_IN, BookingStatus.CONFIRMED);
            var checkedIn = saved.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN)
                    .findFirst()
                    .orElseThrow();
            assertRecordedOnce(checkedIn, winner);
            var untouched = saved.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .findFirst()
                    .orElseThrow();
            assertThat(untouched.getAssignedRoom()).isNull();
            assertThat(untouched.getCheckedInAt()).isNull();
            assertThat(untouched.getStatusHistories()).isEmpty();
            assertRoomStates();
        });
    }

    @Test
    void simultaneousRequestsForOneBookingCannotReassignItOrOccupyASecondRoom() throws Exception {
        var outcomes = race("bookings", fixture.bookingIds().get(0), request(0, 0, 0), request(0, 1, 1));

        assertThat(outcomes)
                .extracting(Outcome::code)
                .containsExactlyInAnyOrder("SUCCESS", "BOOKING_ALREADY_CHECKED_IN");
        var winner =
                outcomes.stream().filter(o -> o.response() != null).findFirst().orElseThrow();
        transactions.executeWithoutResult(status -> {
            var checkedIn = bookings.findById(fixture.bookingIds().get(0)).orElseThrow();
            assertRecordedOnce(checkedIn, winner);
            assertThat(bookings.findById(fixture.bookingIds().get(1))
                            .orElseThrow()
                            .getStatus())
                    .isEqualTo(BookingStatus.CONFIRMED);
            assertRoomStates();
        });
    }

    private Request request(int booking, int room, int staff) {
        return new Request(
                fixture.bookingCodes().get(booking),
                fixture.roomIds().get(room),
                fixture.staffIds().get(staff));
    }

    private List<Outcome> race(String table, UUID lockedId, Request first, Request second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var backendPids = new ConcurrentLinkedQueue<Integer>();
        var executor = Executors.newFixedThreadPool(2);
        // Hold the contested row until PostgreSQL confirms both independent transactions are blocked.
        try (var blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("select id from " + table + " where id = ? for update")) {
                lock.setObject(1, lockedId);
                lock.executeQuery().close();
            }
            var a = executor.submit(() -> attempt(first, ready, start, backendPids));
            var b = executor.submit(() -> attempt(second, ready, start, backendPids));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                awaitBothBlocked(List.copyOf(backendPids));
            } finally {
                start.countDown();
                blocker.commit();
            }
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Outcome attempt(
            Request request, CountDownLatch ready, CountDownLatch start, ConcurrentLinkedQueue<Integer> backendPids) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken(request.receptionistId().toString(), null, "ROLE_RECEPTIONIST"));
        try {
            var response = transactions.execute(status -> {
                jdbc.execute("set local lock_timeout = '12s'");
                jdbc.execute("set local statement_timeout = '15s'");
                backendPids.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Check-in race did not start");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return service.checkIn(request.bookingCode(), new CheckInGuestRequest(request.roomId()));
            });
            return new Outcome("SUCCESS", response, request.receptionistId());
        } catch (CheckInConflictException conflict) {
            return new Outcome(conflict.getErrorCode(), null, request.receptionistId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void awaitBothBlocked(List<Integer> pids) throws InterruptedException {
        assertThat(pids).hasSize(2).doesNotHaveDuplicates();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int blocked;
        do {
            blocked = jdbc.queryForObject(
                    """
                    select count(*) from pg_stat_activity
                    where pid in (?, ?) and cardinality(pg_blocking_pids(pid)) > 0
                    """,
                    Integer.class,
                    pids.get(0),
                    pids.get(1));
            if (blocked == 2) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertThat(blocked)
                .as("Both check-in transactions must contend for a real database lock")
                .isEqualTo(2);
    }

    private void assertRecordedOnce(Booking booking, Outcome winner) {
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getId()).isEqualTo(winner.response().bookingId());
        assertThat(booking.getAssignedRoom().getId())
                .isEqualTo(winner.response().room().roomId());
        assertThat(booking.getAssignedRoom().getStatus()).isEqualTo(RoomStatus.OCCUPIED);
        assertThat(booking.getCheckedInAt()).isEqualTo(NOW);
        assertThat(booking.getStatusHistories()).singleElement().satisfies(history -> {
            assertThat(history.getOldStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(history.getNewStatus()).isEqualTo(BookingStatus.CHECKED_IN);
            assertThat(history.getChangedBy().getId()).isEqualTo(winner.receptionistId());
        });
    }

    private void assertRoomStates() {
        assertThat(rooms.findAllById(fixture.roomIds()))
                .extracting(Room::getStatus)
                .containsExactlyInAnyOrder(RoomStatus.OCCUPIED, RoomStatus.AVAILABLE);
    }

    private record Fixture(List<UUID> bookingIds, List<String> bookingCodes, List<UUID> roomIds, List<UUID> staffIds) {}

    private record Request(String bookingCode, UUID roomId, UUID receptionistId) {}

    private record Outcome(String code, CheckInGuestResponse response, UUID receptionistId) {}
}
