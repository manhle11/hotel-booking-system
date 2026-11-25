package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.request.*;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.BookingService;
import com.hanbay.hotel.booking.service.CheckInService;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.repository.*;
import com.hanbay.hotel.room.service.RoomMaintenanceService;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"booking.expiration.cron=-", "logging.level.root=WARN"})
@Testcontainers(disabledWithoutDocker = true)
class RoomAssignmentIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24),
            START = TODAY.plusDays(5),
            END = TODAY.plusDays(60);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired
    RoomAssignmentService service;

    @Autowired
    BookingService bookingService;

    @Autowired
    CheckInService checkIn;

    @Autowired
    RoomMaintenanceService maintenance;

    @Autowired
    BookingRepository bookings;

    @Autowired
    RoomRepository rooms;

    @Autowired
    RoomTypeRepository types;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AssignmentStore store;

    @Autowired
    com.hanbay.hotel.booking.policy.HotelStayPolicy stayPolicy;

    @MockitoBean
    Clock clock;

    private UUID typeId, first, second;

    @BeforeEach
    void fixture() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        SecurityContextHolder.clearContext();
        tx.executeWithoutResult(status -> {
            RoomType type = types.saveAndFlush(RoomType.builder()
                    .code("ASSIGN-" + UUID.randomUUID())
                    .name("Assignment test")
                    .basePrice(500000)
                    .capacityAdults(2)
                    .capacityChildren(0)
                    .maxGuests(2)
                    .build());
            typeId = type.getId();
            first = rooms.saveAndFlush(Room.builder()
                            .roomType(type)
                            .roomNumber("A-" + UUID.randomUUID().toString().substring(0, 8))
                            .floor(2)
                            .status(RoomStatus.AVAILABLE)
                            .build())
                    .getId();
            second = rooms.saveAndFlush(Room.builder()
                            .roomType(type)
                            .roomNumber("B-" + UUID.randomUUID().toString().substring(0, 8))
                            .floor(2)
                            .status(RoomStatus.AVAILABLE)
                            .build())
                    .getId();
        });
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM bookings WHERE room_type_id = ?", typeId);
            jdbc.update("DELETE FROM rooms WHERE room_type_id = ?", typeId);
            jdbc.update("DELETE FROM room_types WHERE id = ?", typeId);
        });
    }

    private Booking booking(String code, LocalDate from, LocalDate to, BookingStatus state, Instant expires) {
        return tx.execute(status -> bookings.saveAndFlush(Booking.builder()
                .bookingCode(code + "-" + UUID.randomUUID())
                .roomType(types.findById(typeId).orElseThrow())
                .checkInDate(from)
                .checkOutDate(to)
                .adultCount(1)
                .status(state)
                .expiredAt(expires)
                .totalAmount(1000000)
                .paidAmount(1000000)
                .build()));
    }

    private Booking booking(String code, int from, int to) {
        return booking(code, START.plusDays(from), START.plusDays(to), BookingStatus.CONFIRMED, null);
    }

    private CalendarView calendar() {
        return service.calendar(TODAY, END, typeId);
    }

    private void assign(UUID bookingId, UUID roomId, boolean locked) {
        service.assign(bookingId, roomId, locked, calendar().version(), "", TODAY, END);
    }

    private Stay saved(UUID bookingId) {
        return calendar().bookings().stream()
                .filter(b -> b.id().equals(bookingId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void suggestIsADraftAndApplyDoesNotOccupyRoomsOrSetActualRoom() {
        Booking a = booking("A", 0, 3), b = booking("B", 0, 2);
        Plan plan = service.suggest(typeId, TODAY, END);
        assertThat(plan.after().unassigned()).isZero();
        assertThat(plan.changes()).hasSize(2);
        assertThat(saved(a.getId()).roomId()).isNull();
        service.apply(plan.id(), plan.sourceVersion());
        assertThat(saved(a.getId()).roomId()).isNotNull();
        assertThat(saved(a.getId()).roomId()).isNotEqualTo(saved(b.getId()).roomId());
        assertThat(rooms.findById(first).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(bookings.findById(a.getId()).orElseThrow().getAssignedRoom()).isNull();
        var oldRoomNumbers = rooms.findAllById(List.of(first, second)).stream()
                .map(Room::getRoomNumber)
                .toList();
        jdbc.update(
                "UPDATE rooms SET room_number = ? WHERE id = ?",
                "RENAMED-" + UUID.randomUUID().toString().substring(0, 8),
                first);
        assertThat(jdbc.queryForList(
                        "SELECT details->>'toRoomNumber' FROM room_assignment_history WHERE room_type_id = ? AND action = 'PLAN_APPLIED'",
                        String.class,
                        typeId))
                .containsExactlyInAnyOrderElementsOf(oldRoomNumbers);
        assertThat(service.apply(plan.id(), plan.sourceVersion()).applied()).isTrue();
        assertThat(service.history(typeId).stream().filter(h -> h.action().equals("PLAN_APPLIED")))
                .hasSize(2);
    }

    @Test
    void overdueGuestRemainsVisibleAndBlocksTheWholeFutureCalendar() {
        UUID id = tx.execute(status -> {
            Room room = rooms.findById(first).orElseThrow();
            room.occupy();
            return bookings.saveAndFlush(Booking.builder()
                            .bookingCode("OVERDUE-" + UUID.randomUUID())
                            .roomType(types.findById(typeId).orElseThrow())
                            .assignedRoom(room)
                            .checkInDate(TODAY.minusDays(3))
                            .checkOutDate(TODAY.minusDays(1))
                            .checkedInAt(NOW.minusSeconds(259200))
                            .status(BookingStatus.CHECKED_IN)
                            .adultCount(1)
                            .totalAmount(1000000)
                            .paidAmount(1000000)
                            .build())
                    .getId();
        });
        Stay current = saved(id);
        assertThat(current.overdue()).isTrue();
        assertThat(current.scheduledEnd()).isEqualTo(TODAY.minusDays(1));
        assertThat(current.end()).isAfterOrEqualTo(END);
        LocalDate later = TODAY.plusDays(150);
        var future = service.calendar(later, later.plusDays(30), typeId);
        assertThat(future.bookings()).extracting(Stay::id).contains(id);
        assertThat(future.bookings().get(0).end()).isAfter(later.plusDays(30));
    }

    @Test
    void applyingARoomSwapChecksExclusionAtTheEndOfTheTransaction() {
        Booking a = booking("A", 0, 3), b = booking("B", 0, 3);
        assign(a.getId(), first, false);
        assign(b.getId(), second, false);
        Plan base = service.suggest(typeId, TODAY, END);
        Plan swap = new Plan(
                UUID.randomUUID(),
                typeId,
                TODAY,
                END,
                base.sourceVersion(),
                base.createdAt(),
                base.validUntil(),
                "FEASIBLE",
                "CP_SAT",
                base.before(),
                base.after(),
                List.of(),
                List.of(),
                Map.of(a.getId(), second, b.getId(), first),
                false,
                base.configuration());
        store.savePlan(swap, null);
        service.apply(swap.id(), swap.sourceVersion());
        assertThat(saved(a.getId()).roomId()).isEqualTo(second);
        assertThat(saved(b.getId()).roomId()).isEqualTo(first);
    }

    @Test
    void refreshQueueProducesADraftWithoutApplyingAssignments() {
        Booking a = booking("A", 0, 3), b = booking("B", 0, 3);
        assign(a.getId(), first, true);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM room_assignment_refresh_queue WHERE room_type_id = ?",
                        Integer.class,
                        typeId))
                .isEqualTo(1);
        new RoomAssignmentScheduler(jdbc, service, stayPolicy).refreshDrafts();
        assertThat(service.latest(typeId, TODAY, END)).isNotNull();
        assertThat(saved(b.getId()).roomId()).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM room_assignment_refresh_queue WHERE room_type_id = ?",
                        Integer.class,
                        typeId))
                .isZero();
    }

    @Test
    void aNewHoldInvalidatesTheOldPlanEvenIfItDoesNotOverlap() {
        booking("A", 0, 2);
        Plan plan = service.suggest(typeId, TODAY, END);
        booking("HOLD", START.plusDays(10), START.plusDays(12), BookingStatus.PENDING, NOW.plusSeconds(900));
        assertThatThrownBy(() -> service.apply(plan.id(), plan.sourceVersion()))
                .isInstanceOf(AssignmentException.class)
                .hasMessageContaining("Lịch đã cập nhật");
    }

    @Test
    void planExpiresExactlyAtItsDeadlineWithoutASchedulerWrite() {
        booking("A", 0, 2);
        Plan plan = service.suggest(typeId, TODAY, END);
        when(clock.instant()).thenReturn(plan.validUntil());
        assertThatThrownBy(() -> service.apply(plan.id(), plan.sourceVersion()))
                .isInstanceOf(AssignmentException.class)
                .hasMessageContaining("Lịch đã cập nhật");
    }

    @Test
    void lockingASecondRoomCannotStrandAnotherAcceptedBooking() {
        Booking a = booking("A", 0, 1), b = booking("B", 1, 2), c = booking("C", 0, 2);
        assign(a.getId(), first, true);
        assertThatThrownBy(() -> assign(b.getId(), second, true)).isInstanceOf(AssignmentException.class);
        assertThat(saved(b.getId()).roomId()).isNull();
        assertThat(saved(c.getId()).roomId()).isNull();
        assertThat(saved(a.getId()).allocationStatus()).isEqualTo("LOCKED");
    }

    @Test
    void bookingAdmissionChecksFixedRoomsBeforeCreatingAHold() {
        Booking a = booking("A", 0, 1), b = booking("B", 1, 2);
        assign(a.getId(), first, true);
        assign(b.getId(), second, true);
        long count = bookings.count();
        CreatePendingBookingRequest request = new CreatePendingBookingRequest(
                typeId,
                START,
                START.plusDays(2),
                1,
                0,
                new GuestInfo("Guest", "guest@example.com", "0901234567", "012345678901"),
                List.of());
        assertThatThrownBy(() -> bookingService.createPendingBooking(request)).isInstanceOf(AssignmentException.class);
        assertThat(bookings.count()).isEqualTo(count);
    }

    @Test
    void pendingHoldsStopBlockingAtExactExpiryWithoutChangingTheirStatus() {
        Booking a = booking("A", 0, 1), b = booking("B", 1, 2);
        Booking hold = booking("HOLD", START, START.plusDays(2), BookingStatus.PENDING, NOW.plusSeconds(60));
        assign(a.getId(), first, true);
        assertThatThrownBy(() -> assign(b.getId(), second, true)).isInstanceOf(AssignmentException.class);
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        assign(b.getId(), second, true);
        assertThat(bookings.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM room_allocations WHERE booking_id = ?", Integer.class, hold.getId()))
                .isZero();
    }

    @Test
    void unlockRequiresManagerAndAnExplicitReason() {
        Booking a = booking("A", 0, 2);
        assign(a.getId(), first, true);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("reception", null, "ROLE_RECEPTIONIST"));
        assertThatThrownBy(() -> assign(a.getId(), first, false)).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("manager", null, "ROLE_MANAGER"));
        assertThatThrownBy(() -> assign(a.getId(), first, false)).hasMessageContaining("lý do");
        service.assign(a.getId(), first, false, calendar().version(), "Khách đồng ý bỏ cam kết số phòng", TODAY, END);
        assertThat(saved(a.getId()).allocationStatus()).isEqualTo("TENTATIVE");
    }

    @Test
    void lifecycleSynchronizesActualCheckInAndReleasesCancelledAllocations() {
        Booking a = booking("ARRIVE", TODAY, TODAY.plusDays(2), BookingStatus.CONFIRMED, null);
        assign(a.getId(), first, true);
        UUID staff = tx.execute(status ->
                users.saveAndFlush(User.builder().fullName("Reception").build()).getId());
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(staff.toString(), null, "ROLE_RECEPTIONIST"));
        assertThat(checkIn.getAvailableRooms(a.getBookingCode()))
                .extracting(r -> r.roomId())
                .containsExactly(first);
        checkIn.checkIn(a.getBookingCode(), new CheckInGuestRequest(first));
        assertThat(saved(a.getId()).allocationStatus()).isEqualTo("OCCUPIED");
        Booking other = booking("CANCEL", 5, 7);
        assign(other.getId(), second, true);
        tx.executeWithoutResult(
                status -> jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?", other.getId()));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM room_allocations WHERE booking_id = ? AND active",
                        Integer.class,
                        other.getId()))
                .isZero();
    }

    @Test
    void maintenanceCannotConsumeRoomsNeededByAcceptedBookings() {
        booking("A", 0, 3);
        booking("B", 0, 3);
        String number = rooms.findById(first).orElseThrow().getRoomNumber();
        assertThatThrownBy(() -> maintenance.changeMaintenanceStatus(number, RoomMaintenanceAction.START_MAINTENANCE))
                .isInstanceOf(AssignmentException.class);
        assertThat(rooms.findById(first).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void postgresRejectsOverlapsAtCommitEvenWhenApplicationValidationIsBypassed() {
        Booking a = booking("A", 0, 3), b = booking("B", 0, 3);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    jdbc.update(
                            "INSERT INTO room_allocations(booking_id, room_id, check_in, check_out, status) VALUES (?, ?, ?, ?, 'TENTATIVE')",
                            a.getId(),
                            first,
                            START,
                            START.plusDays(3));
                    jdbc.update(
                            "INSERT INTO room_allocations(booking_id, room_id, check_in, check_out, status) VALUES (?, ?, ?, ?, 'TENTATIVE')",
                            b.getId(),
                            first,
                            START,
                            START.plusDays(3));
                }))
                .hasMessageContaining("commit");
        assertThat(saved(a.getId()).roomId()).isNull();
    }

    @Test
    void onlyOneOfTwoDifferentPlansForTheSameSnapshotCanBeApplied() throws Exception {
        booking("A", 0, 3);
        booking("B", 0, 3);
        Plan a = service.suggest(typeId, TODAY, END), b = service.suggest(typeId, TODAY, END);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> applyAfter(start, a));
            var two = executor.submit(() -> applyAfter(start, b));
            start.countDown();
            assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPLIED", "ASSIGNMENT_STALE");
        } finally {
            executor.shutdownNow();
        }
    }

    private String applyAfter(CountDownLatch start, Plan plan) throws Exception {
        start.await();
        try {
            service.apply(plan.id(), plan.sourceVersion());
            return "APPLIED";
        } catch (AssignmentException e) {
            return e.getErrorCode();
        }
    }
}
