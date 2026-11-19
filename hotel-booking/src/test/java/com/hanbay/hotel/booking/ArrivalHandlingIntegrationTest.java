package com.hanbay.hotel.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.request.ArrivalHandlingRequest;
import com.hanbay.hotel.booking.dto.request.BookingChangeCommand;
import com.hanbay.hotel.booking.dto.request.CancelBookingRequest;
import com.hanbay.hotel.booking.dto.request.CheckInGuestRequest;
import com.hanbay.hotel.booking.dto.request.CreatePendingBookingRequest;
import com.hanbay.hotel.booking.dto.request.GuestInfo;
import com.hanbay.hotel.booking.dto.response.ArrivalHandlingResponse;
import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import com.hanbay.hotel.booking.exception.StayModificationException;
import com.hanbay.hotel.booking.policy.ArrivalPolicy;
import com.hanbay.hotel.booking.repository.BookingOrderRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.*;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.frontdesk.service.ArrivalWorkService;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.payment.dto.request.ConfirmRefundRequest;
import com.hanbay.hotel.payment.dto.request.CreatePaymentRequest;
import com.hanbay.hotel.payment.dto.request.RefundInformationRequest;
import com.hanbay.hotel.payment.exception.InvalidRefundStateException;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.payment.service.PaymentService;
import com.hanbay.hotel.payment.service.RefundManagementService;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties = {
            "booking.expiration.cron=-",
            "payment.sepay.webhook-api-key=arrival-test-secret",
            "payment.sepay.account-number=123456789",
            "payment.sepay.bank-code=MB",
            "payment.sepay.account-name=TEST HOTEL",
            "payment.sepay.qr-base-url=https://example.test/qr",
            "debug=false",
            "logging.level.root=WARN",
            "logging.level.org.springframework=WARN",
            "logging.level.org.hibernate.SQL=OFF",
            "logging.level.com.hanbay=WARN",
            "spring.jpa.show-sql=false",
            "spring.docker.compose.enabled=false"
        })
class ArrivalHandlingIntegrationTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-12T23:00:00Z");
    private static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 12);
    private static final String TOKEN = "arrival-test-token-with-at-least-32-characters";
    private static final java.util.concurrent.atomic.AtomicLong FEE_EVENTS =
            new java.util.concurrent.atomic.AtomicLong(5_000_000);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    ArrivalHandlingService service;

    @Autowired
    ArrivalWorkService arrivalWork;

    @Autowired
    BookingChangeService changes;

    @Autowired
    BookingChangeSettlementService changeSettlements;

    @Autowired
    com.hanbay.hotel.booking.repository.BookingChangeRequestRepository changeRequests;

    @Autowired
    BookingOrderService orderService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    StayModificationService stayModifications;

    @Autowired
    com.hanbay.hotel.payment.service.PaymentWebhookService webhooks;

    @Autowired
    com.hanbay.hotel.payment.config.SePayProperties sepay;

    @Autowired
    BookingRepository bookings;

    @Autowired
    BookingOrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    UserRepository users;

    @Autowired
    RoomRepository rooms;

    @Autowired
    RoomTypeRepository types;

    @Autowired
    BookingAccessTokenService tokens;

    @Autowired
    CheckInService checkIn;

    @Autowired
    BookingService bookingService;

    @Autowired
    RefundManagementService refunds;

    @Autowired
    RoomAvailabilityCalculator availability;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    Clock clock;

    @MockitoBean
    CurrentUserProvider currentUser;

    UUID staffId;
    UUID bookingId;
    UUID roomId;
    UUID typeId;
    String code;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(CUTOFF);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        tx.executeWithoutResult(status -> {
            User staff = users.saveAndFlush(
                    User.builder().fullName("Lễ tân kiểm thử").build());
            staffId = staff.getId();
            RoomType type = types.saveAndFlush(RoomType.builder()
                    .code("A-" + UUID.randomUUID())
                    .name("Phòng thử no-show")
                    .basePrice(800_000)
                    .capacityAdults(2)
                    .maxGuests(2)
                    .build());
            typeId = type.getId();
            roomId = rooms.saveAndFlush(Room.builder()
                            .roomNumber("A-" + UUID.randomUUID().toString().substring(0, 8))
                            .roomType(type)
                            .floor(1)
                            .status(RoomStatus.AVAILABLE)
                            .build())
                    .getId();
            Booking booking = newBooking(type, staff, true);
            booking.recordPayment(2_800_000);
            bookings.saveAndFlush(booking);
            bookingId = booking.getId();
            code = booking.getBookingCode();
            savePayment(booking, null, 2_800_000);
        });
        when(currentUser.currentUserId()).thenReturn(Optional.of(staffId));
        authenticate("RECEPTIONIST");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void closesWithFirstNightFeeRefundsUnusedServicesAndReleasesInventoryExactlyOnce() {
        assertThat(available()).isZero();
        var preview = service.preview(code);
        assertThat(preview.standardFee()).isEqualTo(800_000);
        assertThat(preview.refundAmount()).isEqualTo(2_000_000);
        var request = request(preview, 800_000);
        var closed = service.markNoShow(code, request);
        assertThat(closed.bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(closed.refundAmount()).isEqualTo(2_000_000);
        assertThat(closed.outstandingAmount()).isZero();
        assertThat(available()).isEqualTo(1);
        service.markNoShow(code, request);
        tx.executeWithoutResult(status -> {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            assertThat(booking.getTotalAmount()).isEqualTo(800_000);
            assertThat(booking.getNoShowOriginalTotal()).isEqualTo(2_800_000);
            assertThat(booking.getNights()).hasSize(3);
            assertThat(booking.getAssignedRoom()).isNull();
            assertThat(booking.getCheckedInAt()).isNull();
            assertThat(booking.getStatusHistories().stream().filter(h -> h.getOldStatus() != h.getNewStatus()))
                    .hasSize(1);
            assertThat(payments.findAllForBookingSettlement(bookingId).get(0).getRefunds())
                    .hasSize(1);
            assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        });
    }

    @Test
    void exactCutoffIsEligibleButOneSecondEarlierCannotClose() {
        when(clock.instant()).thenReturn(CUTOFF.minusSeconds(1));
        var early = service.preview(code);
        assertThat(early.canMarkNoShow()).isFalse();
        assertThatThrownBy(() -> service.markNoShow(code, request(early, 800_000)))
                .isInstanceOf(StayModificationException.class);
        when(clock.instant()).thenReturn(CUTOFF);
        assertThat(service.preview(code).canMarkNoShow()).isTrue();
    }

    @Test
    void receptionCanRequestButOnlyManagerCanApproveLateArrivalWithoutExtraFee() {
        var hold = new ArrivalHandlingRequest.Hold(CUTOFF.plusSeconds(4 * 3600), "Điện thoại: khách xin đến 10:00");
        var requested = service.hold(code, hold);
        assertThat(requested.holdUntil()).isNull();
        assertThat(requested.holdRequestedUntil()).isEqualTo(hold.until());
        assertThat(checkIn.getBookingContext(code).canCheckIn()).isFalse();
        authenticate("MANAGER");
        var approved = service.hold(code, hold);
        assertThat(approved.holdUntil()).isEqualTo(hold.until());
        assertThat(approved.canMarkNoShow()).isFalse();
        assertThatThrownBy(() -> service.markNoShow(code, request(approved, 800_000)))
                .isInstanceOf(StayModificationException.class);
        authenticate("RECEPTIONIST");
        assertThat(checkIn.getBookingContext(code).canCheckIn()).isTrue();
        checkIn.checkIn(code, new CheckInGuestRequest(roomId, null));
        tx.executeWithoutResult(status -> {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
            assertThat(booking.getTotalAmount()).isEqualTo(2_800_000);
            assertThat(booking.getNoShowFee()).isNull();
            assertThat(booking.getCheckInDate()).isEqualTo(ARRIVAL);
            assertThat(booking.getCheckOutDate()).isEqualTo(ARRIVAL.plusDays(3));
        });
    }

    @Test
    void holdsExpireAtExactDeadlineAndCannotOutliveTheOriginalStay() {
        authenticate("MANAGER");
        assertThatThrownBy(() ->
                        service.hold(code, new ArrivalHandlingRequest.Hold(CUTOFF.plusSeconds(4 * 86400), "Quá kỳ")))
                .isInstanceOf(StayModificationException.class);
        Instant until = CUTOFF.plusSeconds(3600);
        service.hold(code, new ArrivalHandlingRequest.Hold(until, "Khách hẹn 07:00"));
        when(clock.instant()).thenReturn(until);
        assertThat(service.preview(code).canMarkNoShow()).isTrue();
        assertThat(checkIn.getBookingContext(code).canCheckIn()).isFalse();
    }

    @Test
    void legacyBookingsAndWaiversRequireManagerAndKeepTheApprovedFee() {
        tx.executeWithoutResult(status -> {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            // Model a booking created before policy acceptance existed.
            org.springframework.test.util.ReflectionTestUtils.setField(booking, "arrivalPolicyVersion", null);
        });
        var preview = service.preview(code);
        assertThat(preview.legacyPolicy()).isTrue();
        assertThatThrownBy(() -> service.markNoShow(code, request(preview, 800_000)))
                .isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        var closed = service.markNoShow(code, request(service.preview(code), 0));
        assertThat(closed.feeAmount()).isZero();
        assertThat(closed.refundAmount()).isEqualTo(2_800_000);
    }

    @Test
    void cannotUseStaleFinancialPreviewOrWaiveFeesAsReception() {
        var preview = service.preview(code);
        assertThatThrownBy(() -> service.markNoShow(code, request(preview, 0)))
                .isInstanceOf(AccessDeniedException.class);
        tx.executeWithoutResult(
                status -> bookings.findById(bookingId).orElseThrow().applyTotalAmount(3_000_000));
        assertThatThrownBy(() -> service.markNoShow(code, request(preview, 800_000)))
                .isInstanceOf(StayModificationException.class)
                .hasMessageContaining("thay đổi");
        assertThat(service.preview(code).bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void refundNeedsDestinationAndOwnerCanSupplyItWithoutLeakingStaffNotes() {
        var closed = service.markNoShow(code, request(service.preview(code), 800_000));
        UUID refundId = closed.refunds().get(0).refundId();
        assertThatThrownBy(() -> refunds.confirmRefund(refundId, new ConfirmRefundRequest("BANK-REF-123")))
                .isInstanceOf(InvalidRefundStateException.class);
        when(currentUser.currentUserId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.customerSettlement(code, "wrong-token"))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingNotFoundException.class);
        assertThat(service.customerSettlement(code, TOKEN).history()).isEmpty();
        var supplied = service.supplyRefundInformation(
                code,
                TOKEN,
                new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Ngân hàng", "NGUYEN VAN A", "123456789"));
        assertThat(supplied.refunds().get(0).informationRequired()).isFalse();
        refunds.confirmRefund(refundId, new ConfirmRefundRequest("BANK-REF-123"));
        assertThat(service.customerSettlement(code, TOKEN).refunds().get(0).status())
                .isEqualTo(PaymentRefundStatus.SUCCESS);
    }

    @Test
    void oneNoShowDoesNotRefundAnotherRoomsAllocationOrChangeItsStay() {
        String[] codes = tx.execute(status -> {
            RoomType type = types.findById(typeId).orElseThrow();
            User actor = users.findById(staffId).orElseThrow();
            Booking first = newBooking(type, actor, true);
            Booking second = newBooking(type, actor, true);
            BookingOrder order = orders.saveAndFlush(BookingOrder.builder()
                    .orderCode("O-" + UUID.randomUUID())
                    .bookedBy(actor)
                    .contactName("Khách đoàn")
                    .contactEmail("group@example.test")
                    .checkInDate(ARRIVAL)
                    .checkOutDate(ARRIVAL.plusDays(3))
                    .status(BookingOrderStatus.CONFIRMED)
                    .accessTokenHash(tokens.hash(TOKEN))
                    .requestHash(tokens.hash("request"))
                    .idempotencyKey(UUID.randomUUID().toString())
                    .expiresAt(CUTOFF.plusSeconds(3600))
                    .build());
            order.addBooking(first);
            order.addBooking(second);
            bookings.saveAndFlush(first);
            bookings.saveAndFlush(second);
            Room room = rooms.findById(roomId).orElseThrow();
            room.occupy();
            second.checkIn(room, actor, CUTOFF.minusSeconds(3600), null);
            Payment payment = savePayment(first, order, 5_600_000);
            payment.allocateTo(first);
            payment.allocateTo(second);
            payment.getAllocations().forEach(a -> a.apply(2_800_000));
            payments.flush();
            return new String[] {first.getBookingCode(), second.getBookingCode()};
        });
        var closed = service.markNoShow(codes[0], request(service.preview(codes[0]), 800_000));
        assertThat(closed.refundAmount()).isEqualTo(2_000_000);
        var sibling = checkIn.getBookingContext(codes[1]);
        assertThat(sibling.bookingStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(sibling.totalAmount()).isEqualTo(2_800_000);
        assertThat(sibling.paidAmount()).isEqualTo(2_800_000);
        assertThat(sibling.assignedRoom().roomId()).isEqualTo(roomId);
    }

    @Test
    void concurrentNoShowRequestsProduceOneSettlementAndOneRefund() throws Exception {
        var request = request(service.preview(code), 800_000);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<ArrivalHandlingResponse> close = () -> {
                authenticate("RECEPTIONIST");
                start.await(10, TimeUnit.SECONDS);
                try {
                    return service.markNoShow(code, request);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var first = pool.submit(close);
            var second = pool.submit(close);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
            assertThat(second.get(20, TimeUnit.SECONDS).bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
            assertThat(service.preview(code).refunds()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void settlesAllPaymentsIncludingPartiallyPaidExtensionWithoutKeepingUnusedStayDebt() {
        tx.executeWithoutResult(status -> {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            booking.extendTo(
                    ARRIVAL.plusDays(4),
                    List.of(BookingNight.builder()
                            .stayDate(ARRIVAL.plusDays(3))
                            .pricePerNight(400_000)
                            .build()),
                    400_000);
            booking.recordPayment(200_000);
            Payment additional = Payment.builder()
                    .booking(booking)
                    .paymentCode("EXTRA-" + UUID.randomUUID())
                    .amount(400_000)
                    .status(PaymentStatus.PENDING)
                    .expiresAt(CUTOFF.plusSeconds(3600))
                    .build();
            additional.addTransaction(PaymentTransaction.builder()
                    .type(PaymentTransactionType.PAYMENT)
                    .status(PaymentTransactionStatus.SUCCESS)
                    .amount(200_000)
                    .receivedAt(CUTOFF)
                    .build());
            payments.saveAndFlush(additional);
        });
        var preview = service.preview(code);
        assertThat(preview.refundAmount()).isEqualTo(2_200_000);
        var closed = service.markNoShow(code, request(preview, 800_000));
        assertThat(closed.outstandingAmount()).isZero();
        assertThat(closed.refundAmount()).isEqualTo(2_200_000);
        tx.executeWithoutResult(status -> {
            assertThat(bookings.findById(bookingId).orElseThrow().getTotalAmount())
                    .isEqualTo(800_000);
            assertThat(payments.findAllForBookingSettlement(bookingId))
                    .noneMatch(p -> p.getStatus() == PaymentStatus.PENDING);
        });
    }

    @Test
    void existingOverpaymentRefundIsReservedBeforeNoShowAndNeverRefundedTwice() {
        tx.executeWithoutResult(status -> {
            Payment payment = payments.findAllForBookingSettlement(bookingId).get(0);
            payment.addTransaction(PaymentTransaction.builder()
                    .type(PaymentTransactionType.PAYMENT)
                    .status(PaymentTransactionStatus.SUCCESS)
                    .amount(100_000)
                    .receivedAt(CUTOFF)
                    .build());
            payment.requestRefund(PaymentRefund.builder()
                    .amount(BigDecimal.valueOf(100_000))
                    .refundMethod(null)
                    .reason("Hoàn tiền thừa")
                    .idempotencyKey("overpay-" + UUID.randomUUID())
                    .requestedAt(CUTOFF)
                    .build());
        });
        var closed = service.markNoShow(code, request(service.preview(code), 800_000));
        assertThat(closed.refundAmount()).isEqualTo(2_100_000); // Existing 100k plus new 2m, from 2.9m received.
        tx.executeWithoutResult(status -> {
            var requests =
                    payments.findAllForBookingSettlement(bookingId).get(0).getRefunds();
            assertThat(requests).hasSize(2);
            assertThat(requests.stream().filter(r -> r.getIdempotencyKey().startsWith("no-show:")))
                    .singleElement()
                    .extracting(r -> r.getAmount().longValueExact())
                    .isEqualTo(2_000_000L);
        });
    }

    @Test
    void replacementRequiresManagerAndCreatesANewUnpaidStayWithoutMovingTheOldRefund() {
        UUID ownerId = tx.execute(status -> {
            User owner =
                    users.saveAndFlush(User.builder().fullName("Người đặt gốc").build());
            org.springframework.test.util.ReflectionTestUtils.setField(
                    bookings.findById(bookingId).orElseThrow(), "bookedBy", owner);
            return owner.getId();
        });
        var request = new CreatePendingBookingRequest(
                typeId,
                ARRIVAL.plusDays(1),
                ARRIVAL.plusDays(2),
                2,
                0,
                new GuestInfo("Khách đặt kỳ mới", "replacement@example.test", "0900000000", "TEST-ID"),
                List.of(),
                List.of(),
                code);
        authenticate("MANAGER");
        assertThatThrownBy(() -> bookingService.createPendingBooking(request))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
        service.markNoShow(code, request(service.preview(code), 800_000));
        authenticate("RECEPTIONIST");
        assertThatThrownBy(() -> bookingService.createPendingBooking(request))
                .isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        var created = bookingService.createPendingBooking(request);
        tx.executeWithoutResult(status -> {
            Booking replacement = bookings.findById(created.bookingId()).orElseThrow();
            assertThat(replacement.getReplacementForBookingId()).isEqualTo(bookingId);
            assertThat(replacement.getBookedBy().getId()).isEqualTo(ownerId);
            assertThat(replacement.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(replacement.getPaidAmount()).isZero();
            assertThat(replacement.getTotalAmount()).isEqualTo(800_000);
            assertThat(replacement.getStatusHistories())
                    .anyMatch(h -> h.getChangedBy() != null
                            && staffId.equals(h.getChangedBy().getId())
                            && h.getReason().contains("thay booking"));
        });
        assertThat(service.preview(code).refundAmount()).isEqualTo(2_000_000);
    }

    @Test
    void confirmedLegacyCancellationReleasesRoomsWhileFinancialReviewRemainsPending() {
        var command = new BookingChangeCommand.Create(
                BookingChangeRequest.Type.CANCELLATION, null, null, "Khách gọi báo hủy");
        var request = changes.create(code, command);
        assertThat(changes.create(code, command).id()).isEqualTo(request.id());
        assertThat(changes.history(code)).hasSize(1);
        assertThatThrownBy(() -> changes.create(
                        code,
                        new BookingChangeCommand.Create(
                                BookingChangeRequest.Type.RESCHEDULE,
                                ARRIVAL.plusDays(10),
                                ARRIVAL.plusDays(12),
                                "Xin đổi ngày")))
                .isInstanceOf(StayModificationException.class);
        var preview = service.preview(code);
        assertThat(preview.canMarkNoShow()).isFalse();
        assertThat(preview.settlementReviewRequired()).isTrue();
        assertThatThrownBy(() -> service.collectFee(code, UUID.randomUUID(), 1, 0, "Chưa chốt phí"))
                .isInstanceOf(StayModificationException.class);
        assertThatThrownBy(() -> paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN))
                .isInstanceOf(com.hanbay.hotel.payment.exception.PaymentBookingNotPayableException.class);
        assertThatThrownBy(() -> service.markNoShow(code, request(preview, 800_000)))
                .isInstanceOf(StayModificationException.class);
        tx.executeWithoutResult(s -> {
            Booking b = bookings.findById(bookingId).orElseThrow();
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(b.isCancellationReviewRequired()).isTrue();
            assertThat(b.getCheckInDate()).isEqualTo(ARRIVAL);
            assertThat(b.getTotalAmount()).isZero();
            assertThat(b.getCancellationFee()).isNull();
            assertThat(payments.findAllForBookingSettlement(bookingId).get(0).getRefunds())
                    .isEmpty();
        });
    }

    @Test
    void managerCanApproveExplicitLateCancellationBeforeNoShowCutoffWithoutNoShowPenalty() {
        when(clock.instant()).thenReturn(CUTOFF.minusSeconds(3600));
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.CANCELLATION, null, null, "Khách báo hủy sát giờ"));
        var quote = changes.quote(request.id());
        assertThat(quote.requiresFeeDecision()).isTrue();
        assertThat(quote.feeAmount()).isNull();
        assertThatThrownBy(() -> changes.approve(
                        request.id(),
                        new BookingChangeCommand.Approve(quote.quoteToken(), 500_000L, "Chấp thuận ngoại lệ")))
                .isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        assertThatThrownBy(() -> changes.approve(
                        request.id(), new BookingChangeCommand.Approve(quote.quoteToken(), null, "Chưa chọn phí")))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
        var command =
                new BookingChangeCommand.Approve(quote.quoteToken(), 500_000L, "Phí ngoại lệ đã thống nhất với khách");
        changes.approve(request.id(), command);
        changes.approve(request.id(), command);
        var closed = service.preview(code);
        assertThat(closed.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(closed.staffCancellation()).isTrue();
        assertThat(closed.feeAmount()).isEqualTo(500_000);
        assertThat(closed.refundAmount()).isEqualTo(2_300_000);
        assertThat(closed.noShowAt()).isNull();
        assertThat(closed.refunds()).hasSize(1);
        assertThat(service.customerSettlement(code, TOKEN).history()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"48,0", "24,1400000"})
    void cancellationPolicyUsesTheTimeOfCustomerRequestEvenWhenManagerDecidesLater(int hours, long expectedFee) {
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minusSeconds(hours * 3600L));
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(BookingChangeRequest.Type.CANCELLATION, null, null, "Khách báo hủy"));
        when(clock.instant()).thenReturn(CUTOFF);
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        assertThat(quote.requiresFeeDecision()).isFalse();
        assertThat(quote.feeAmount()).isEqualTo(expectedFee);
        assertThatThrownBy(() -> changes.approve(
                        request.id(),
                        new BookingChangeCommand.Approve(quote.quoteToken(), expectedFee + 1, "Không được tăng phí")))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(quote.quoteToken(), null, "Theo chính sách tại lúc báo hủy"));
        assertThat(service.preview(code).refundAmount()).isEqualTo(2_800_000 - expectedFee);
    }

    @Test
    void rejectsRequestWithoutChangingBookingAndAllowsAnotherRequest() {
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(12),
                        "Xin đổi ngày"));
        authenticate("MANAGER");
        changes.reject(request.id(), new BookingChangeCommand.Reject("Khách chưa đồng ý giá mới"));
        changes.reject(request.id(), new BookingChangeCommand.Reject("Gửi lại"));
        assertThat(service.preview(code).canMarkNoShow()).isTrue();
        tx.executeWithoutResult(
                s -> assertThat(bookings.findById(bookingId).orElseThrow().getCheckInDate())
                        .isEqualTo(ARRIVAL));
        var next = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.CANCELLATION, null, null, "Khách đổi sang hủy"));
        assertThat(next.id()).isNotEqualTo(request.id());
        assertThat(changes.history(code)).hasSize(2);
    }

    @Test
    void reschedulesOverlappingNightsAtomicallyAndNoShowLaterRefundsOnlyRemainingCredit() {
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(1),
                        ARRIVAL.plusDays(3),
                        "Khách đến ngày mai"));
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        assertThat(quote.canApprove()).isTrue();
        assertThat(quote.newTotalAmount()).isEqualTo(1_600_000);
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(quote.quoteToken(), null, "Đã kiểm tra giá và tồn phòng"));
        tx.executeWithoutResult(s -> {
            Booking b = bookings.findById(bookingId).orElseThrow();
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(b.getStayRevision()).isEqualTo(1);
            assertThat(b.getPaidAmount()).isEqualTo(1_600_000);
            assertThat(b.getNights())
                    .extracting(BookingNight::getStayDate)
                    .containsExactly(ARRIVAL.plusDays(1), ARRIVAL.plusDays(2));
        });
        when(clock.instant()).thenReturn(CUTOFF.plusSeconds(86400));
        var closed = service.markNoShow(code, request(service.preview(code), 800_000));
        assertThat(closed.feeAmount()).isEqualTo(800_000);
        assertThat(closed.refunds()).hasSize(2);
        assertThat(closed.refundAmount()).isEqualTo(2_000_000);
    }

    @Test
    void publicCancellationAfterCheaperRescheduleDoesNotRefundMoneyTwice() {
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(12),
                        "Đổi sang kỳ ngắn hơn"));
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        changes.approve(request.id(), new BookingChangeCommand.Approve(quote.quoteToken(), null, "Giữ giá dịch vụ"));
        assertThat(bookingService.getCancellationEligibility(code).refundAmount())
                .isEqualTo(1_600_000);
        var cancelled = bookingService.cancelBooking(
                bookingId,
                new CancelBookingRequest(
                        "Hủy sau đổi lịch",
                        new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Ngân hàng", "KHACH A", "123456789")));
        assertThat(cancelled.refundAmount()).isEqualTo(1_600_000);
        assertThat(service.customerSettlement(code, TOKEN).refundAmount()).isEqualTo(2_800_000);
    }

    @Test
    void repricesRepeatedChangesAndReplacesOldBalanceQrEvenIfTheBalanceIsUnchanged() {
        authenticate("MANAGER");
        var first = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(15),
                        "Đổi lần một"));
        changes.approve(
                first.id(),
                new BookingChangeCommand.Approve(changes.quote(first.id()).quoteToken(), null, "Duyệt lần một"));
        var payment1 = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        var second = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(11),
                        ARRIVAL.plusDays(16),
                        "Đổi lần hai"));
        changes.approve(
                second.id(),
                new BookingChangeCommand.Approve(changes.quote(second.id()).quoteToken(), null, "Duyệt lần hai"));
        var payment2 = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        assertThat(payment2.transactionRef()).isNotEqualTo(payment1.transactionRef());
        tx.executeWithoutResult(s -> {
            assertThat(bookings.findById(bookingId).orElseThrow().getTotalAmount())
                    .isEqualTo(4_000_000);
            assertThat(payments.findAllForBookingSettlement(bookingId).stream()
                            .filter(p -> p.getStatus() == PaymentStatus.PENDING))
                    .hasSize(1);
        });
        // Full cancellation refunds only the actual 2.8m receipts, not the unpaid new total of 4m.
        assertThat(bookingService.getCancellationEligibility(code).refundAmount())
                .isEqualTo(2_800_000);
    }

    @Test
    void requiresFreshQuoteWhenPricesChangeAndRejectsInsufficientInventoryWithoutMutation() {
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(12),
                        "Xin đổi ngày"));
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        tx.executeWithoutResult(s -> org.springframework.test.util.ReflectionTestUtils.setField(
                types.findById(typeId).orElseThrow(), "basePrice", 900_000L));
        assertThatThrownBy(() -> changes.approve(
                        request.id(), new BookingChangeCommand.Approve(quote.quoteToken(), null, "Giá đã cũ")))
                .isInstanceOf(StayModificationException.class)
                .hasMessageContaining("thay đổi");
        tx.executeWithoutResult(s -> {
            Booking other = newBooking(
                    types.findById(typeId).orElseThrow(),
                    users.findById(staffId).orElseThrow(),
                    true);
            org.springframework.test.util.ReflectionTestUtils.setField(other, "checkInDate", ARRIVAL.plusDays(10));
            org.springframework.test.util.ReflectionTestUtils.setField(other, "checkOutDate", ARRIVAL.plusDays(12));
            bookings.saveAndFlush(other);
        });
        assertThat(changes.quote(request.id()).canApprove()).isFalse();
        tx.executeWithoutResult(
                s -> assertThat(bookings.findById(bookingId).orElseThrow().getCheckInDate())
                        .isEqualTo(ARRIVAL));
    }

    @Test
    void reschedulesWholeOrderExcludingItsOwnRoomsFromInventoryAndAggregatesEveryRefund() {
        var codes = confirmedGroup();
        var request = changes.create(
                codes[0],
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(1),
                        ARRIVAL.plusDays(3),
                        "Cả đoàn đến muộn một ngày"));
        assertThat(changes.history(codes[1])).hasSize(1);
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        assertThat(quote.rooms()).hasSize(2);
        assertThat(quote.canApprove()).as(quote.blockReason()).isTrue();
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(quote.quoteToken(), null, "Đã thống nhất với trưởng đoàn"));
        UUID orderId = tx.execute(s -> {
            var b = bookings.findByBookingCode(codes[0]).orElseThrow();
            assertThat(b.getOrder().getCheckInDate()).isEqualTo(ARRIVAL.plusDays(1));
            assertThat(b.getOrder().getBookings())
                    .allMatch(room -> room.getCheckInDate().equals(ARRIVAL.plusDays(1)) && room.getStayRevision() == 1);
            // An additional room payment is outside the original order payment but must remain visible/refundable.
            savePayment(b, null, 200_000);
            b.recordPayment(200_000);
            return b.getOrder().getId();
        });
        var cancel = changes.create(
                codes[1],
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.CANCELLATION, null, null, "Trưởng đoàn báo hủy"));
        changes.approve(
                cancel.id(),
                new BookingChangeCommand.Approve(changes.quote(cancel.id()).quoteToken(), 0L, "Miễn toàn bộ phí hủy"));
        var result = orderService.get(orderId, TOKEN);
        assertThat(result.status()).isEqualTo(BookingOrderStatus.CANCELLED);
        assertThat(result.refundAmount()).isEqualTo(5_800_000);
        assertThat(result.refundInformationRequired()).isTrue();
        result = orderService.supplyRefundInformation(
                orderId,
                TOKEN,
                new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Ngân hàng", "TRUONG DOAN", "123456789"));
        assertThat(result.refundInformationRequired()).isFalse();
    }

    @Test
    void publicWholeOrderCancellationAfterRepricingRefundsOnlyUnreservedReceipts() {
        var codes = confirmedGroup();
        var request = changes.create(
                codes[0],
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(12),
                        "Đoàn đổi kỳ ngắn hơn"));
        authenticate("MANAGER");
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(
                        changes.quote(request.id()).quoteToken(), null, "Đã xác minh tồn phòng"));
        UUID orderId = tx.execute(s ->
                bookings.findByBookingCode(codes[0]).orElseThrow().getOrder().getId());
        assertThat(orderService.cancellationEligibility(orderId, TOKEN).refundAmount())
                .isEqualTo(3_200_000);
        var result = orderService.cancel(
                orderId,
                TOKEN,
                new CancelBookingRequest(
                        "Khách hủy sau đổi lịch",
                        new RefundInformationRequest(
                                RefundMethod.BANK_TRANSFER, "Ngân hàng", "TRUONG DOAN", "123456789")));
        assertThat(result.status()).isEqualTo(BookingOrderStatus.CANCELLED);
        assertThat(result.refundAmount()).isEqualTo(5_600_000);
        assertThat(result.rooms()).allMatch(r -> r.status() == BookingStatus.CANCELLED);
    }

    @Test
    void groupApprovalIsBlockedIfAnyRoomChecksInWhileTheRequestIsPending() {
        var codes = confirmedGroup();
        var request = changes.create(
                codes[0],
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Khách đề nghị đổi cả đoàn"));
        tx.executeWithoutResult(s -> {
            var b = bookings.findByBookingCode(codes[1]).orElseThrow();
            var room = rooms.findById(roomId).orElseThrow();
            room.occupy();
            b.checkIn(room, users.findById(staffId).orElseThrow(), CUTOFF, null);
        });
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        assertThat(quote.canApprove()).isFalse();
        assertThatThrownBy(() -> changes.approve(
                        request.id(),
                        new BookingChangeCommand.Approve(quote.quoteToken(), 0L, "Không được hủy phòng đang ở")))
                .isInstanceOf(StayModificationException.class);
        assertThat(service.preview(codes[0]).bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void concurrentCancellationApprovalsProduceOneDecisionAndOneRefund() throws Exception {
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(BookingChangeRequest.Type.CANCELLATION, null, null, "Khách báo hủy"));
        var command =
                new BookingChangeCommand.Approve(changes.quote(request.id()).quoteToken(), 0L, "Quản lý miễn phí hủy");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<BookingChangeRequest.Status> task = () -> {
                authenticate("MANAGER");
                start.await(10, TimeUnit.SECONDS);
                try {
                    return changes.approve(request.id(), command).status();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var a = pool.submit(task);
            var b = pool.submit(task);
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(BookingChangeRequest.Status.APPROVED);
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(BookingChangeRequest.Status.APPROVED);
            assertThat(service.preview(code).refunds()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private String[] confirmedGroup() {
        return confirmedGroup(2);
    }

    private String[] confirmedGroup(int count) {
        return confirmedGroup(count, false);
    }

    private String[] confirmedGroup(int count, boolean unified) {
        return tx.execute(s -> {
            var type = types.findById(typeId).orElseThrow();
            var actor = users.findById(staffId).orElseThrow();
            for (int i = 0; i < count; i++)
                rooms.saveAndFlush(Room.builder()
                        .roomNumber("G-" + UUID.randomUUID().toString().substring(0, 8))
                        .roomType(type)
                        .floor(1)
                        .status(RoomStatus.AVAILABLE)
                        .build());
            var order = orders.saveAndFlush(BookingOrder.builder()
                    .orderCode("O-" + UUID.randomUUID())
                    .bookedBy(actor)
                    .contactName("Khách đoàn")
                    .contactEmail("group@example.test")
                    .checkInDate(ARRIVAL)
                    .checkOutDate(ARRIVAL.plusDays(3))
                    .status(BookingOrderStatus.CONFIRMED)
                    .accessTokenHash(tokens.hash(TOKEN))
                    .requestHash(tokens.hash("request"))
                    .idempotencyKey(UUID.randomUUID().toString())
                    .expiresAt(CUTOFF.plusSeconds(3600))
                    .build());
            var members = new ArrayList<Booking>();
            for (int i = 0; i < count; i++) {
                var b = unified ? newUnifiedBooking(type, actor) : newBooking(type, actor, true);
                order.addBooking(b);
                bookings.saveAndFlush(b);
                members.add(b);
            }
            var payment = savePayment(members.get(0), order, 2_800_000L * count);
            members.forEach(payment::allocateTo);
            payment.getAllocations().forEach(allocation -> allocation.apply(2_800_000));
            payments.flush();
            return members.stream().map(Booking::getBookingCode).toArray(String[]::new);
        });
    }

    @Test
    void expectedArrivalTracksPastAppointmentsWithoutChangingReceptionRightsOrPrices() {
        Instant now = CUTOFF.minusSeconds(12 * 3600);
        when(clock.instant()).thenReturn(now);
        var result =
                service.estimate(code, new ArrivalHandlingRequest.Estimate(now.plusSeconds(3600), "Khách hẹn 19:00"));
        assertThat(result.progress().receptionDeadline()).isEqualTo(CUTOFF);
        assertThat(result.holdUntil()).isNull();
        assertThat(result.progress().group()).isEqualTo(ArrivalProgress.Group.WAITING);
        assertThat(workContains(ArrivalProgress.Group.WAITING, null)).isTrue();
        when(clock.instant()).thenReturn(now.plusSeconds(3601));
        assertThat(service.preview(code).progress().reasons()).contains(ArrivalProgress.Reason.PAST_ETA);
        assertThat(workContains(ArrivalProgress.Group.ACTION_REQUIRED, ArrivalProgress.Reason.PAST_ETA))
                .isTrue();
        assertThat(workContains(ArrivalProgress.Group.WAITING, null)).isFalse();
        assertThat(service.preview(code).canMarkNoShow()).isFalse();
        tx.executeWithoutResult(
                s -> assertThat(bookings.findById(bookingId).orElseThrow().getTotalAmount())
                        .isEqualTo(2_800_000));
    }

    @Test
    void arrivalAfterCutoffDoesNotAuthorizeReceptionAndManagerCanRejectOrApproveRequest() {
        var expected = CUTOFF.plusSeconds(3600);
        var until = CUTOFF.plusSeconds(7200);
        var estimated = service.estimate(code, new ArrivalHandlingRequest.Estimate(expected, null));
        assertThat(estimated.progress().reasons())
                .contains(ArrivalProgress.Reason.ETA_AFTER_DEADLINE, ArrivalProgress.Reason.RECEPTION_EXPIRED);
        assertThat(estimated.progress().canReceive()).isFalse();
        var held = service.hold(code, new ArrivalHandlingRequest.Hold(until, "Khách xin đến 07:00", expected));
        assertThat(held.progress().holdPending()).isTrue();
        assertThat(held.canMarkNoShow()).isFalse();
        assertThat(workContains(ArrivalProgress.Group.ACTION_REQUIRED, ArrivalProgress.Reason.HOLD_REQUEST))
                .isTrue();
        var decision = new ArrivalHandlingRequest.HoldDecision(until, false, "Không nhận ngoại lệ; đã báo khách");
        assertThatThrownBy(() -> service.decideHold(code, decision)).isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        var rejected = service.decideHold(code, decision);
        assertThat(rejected.holdRequestedUntil()).isNull();
        assertThat(rejected.progress().receptionDeadline()).isEqualTo(CUTOFF);
        assertThatThrownBy(() -> service.decideHold(code, decision)).isInstanceOf(StayModificationException.class);
        authenticate("RECEPTIONIST");
        service.hold(code, new ArrivalHandlingRequest.Hold(until, "Khách đề nghị lại", expected));
        authenticate("MANAGER");
        var approved = service.decideHold(
                code, new ArrivalHandlingRequest.HoldDecision(until, true, "Chấp thuận trước 08:00"));
        assertThat(approved.progress().canReceive()).isTrue();
        assertThat(approved.progress().receptionDeadline()).isEqualTo(until);
        assertThat(workContains(ArrivalProgress.Group.WAITING, null)).isTrue();
        when(clock.instant()).thenReturn(until);
        assertThat(service.preview(code).progress().canReceive()).isFalse();
        assertThat(workContains(ArrivalProgress.Group.ACTION_REQUIRED, ArrivalProgress.Reason.RECEPTION_EXPIRED))
                .isTrue();
    }

    @Test
    void invalidEtaAndStaleOrOutOfStayHoldDecisionsCannotChangeReception() {
        assertThatThrownBy(
                        () -> service.estimate(code, new ArrivalHandlingRequest.Estimate(CUTOFF.minusSeconds(1), null)))
                .isInstanceOf(StayModificationException.class);
        assertThatThrownBy(() -> service.estimate(
                        code, new ArrivalHandlingRequest.Estimate(CUTOFF.plusSeconds(3 * 86400), null)))
                .isInstanceOf(StayModificationException.class);
        var until = CUTOFF.plusSeconds(7200);
        service.hold(code, new ArrivalHandlingRequest.Hold(until, "Khách xin đến", CUTOFF.plusSeconds(3600)));
        service.hold(
                code,
                new ArrivalHandlingRequest.Hold(until.plusSeconds(3600), "Đổi hạn đề nghị", CUTOFF.plusSeconds(3600)));
        authenticate("MANAGER");
        assertThatThrownBy(() ->
                        service.decideHold(code, new ArrivalHandlingRequest.HoldDecision(until, true, "Đề nghị đã cũ")))
                .isInstanceOf(StayModificationException.class);
        when(clock.instant()).thenReturn(until.plusSeconds(3600));
        assertThatThrownBy(() -> service.decideHold(
                        code, new ArrivalHandlingRequest.HoldDecision(until.plusSeconds(3600), true, "Đã hết hạn")))
                .isInstanceOf(StayModificationException.class);
        assertThat(service.decideHold(
                                code,
                                new ArrivalHandlingRequest.HoldDecision(
                                        until.plusSeconds(3600), false, "Từ chối đề nghị hết hạn"))
                        .progress()
                        .holdPending())
                .isFalse();
    }

    @Test
    void structuredContactShowsOnWorkListAndClosedCasesMoveToResolvedOnlyForTheHotelDay() {
        service.structuredContact(
                code,
                new ArrivalHandlingRequest.StructuredContact(
                        ArrivalContact.Channel.PHONE, ArrivalContact.Outcome.NO_ANSWER, "Gọi hai lần"));
        var item = arrivalWork.list(ArrivalProgress.Group.ACTION_REQUIRED, null, 0, 100).content().stream()
                .filter(b -> b.bookingCode().equals(code))
                .findFirst()
                .orElseThrow();
        assertThat(item.progress().lastContactAt()).isEqualTo(CUTOFF);
        assertThat(item.progress().lastContactOutcome()).isEqualTo(ArrivalContact.Outcome.NO_ANSWER);
        assertThat(item.progress().lastContactNote()).isEqualTo("Gọi hai lần");
        service.markNoShow(code, request(service.preview(code), 800_000));
        assertThat(workContains(ArrivalProgress.Group.ACTION_REQUIRED, null)).isFalse();
        assertThat(workContains(ArrivalProgress.Group.RESOLVED, null)).isTrue();
        when(clock.instant()).thenReturn(CUTOFF.plusSeconds(86400));
        assertThat(workContains(ArrivalProgress.Group.RESOLVED, null)).isFalse();
    }

    @Test
    void changeRequestsAppearInActionWorkAndStructuredCancellationCannotBeMarkedNoShow() {
        changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.CANCELLATION,
                        null,
                        null,
                        "Khách báo hủy",
                        ArrivalContact.Channel.PHONE));
        assertThat(workContains(ArrivalProgress.Group.ACTION_REQUIRED, ArrivalProgress.Reason.CHANGE_REQUEST))
                .isFalse();
        var quote = service.preview(code);
        assertThat(quote.progress().lastContactOutcome()).isEqualTo(ArrivalContact.Outcome.CANCELLATION);
        assertThatThrownBy(() -> service.markNoShow(
                        code,
                        new ArrivalHandlingRequest.NoShow(
                                quote.previewToken(),
                                800_000L,
                                "r",
                                "Khách báo hủy",
                                ArrivalContact.Channel.PHONE,
                                ArrivalContact.Outcome.CANCELLATION)))
                .isInstanceOf(StayModificationException.class)
                .hasMessageContaining("yêu cầu riêng");
    }

    @Test
    void checkedInGuestAppearsAsResolvedAndPaginationHasStableCounts() {
        when(clock.instant()).thenReturn(CUTOFF.minusSeconds(1));
        checkIn.checkIn(code, new CheckInGuestRequest(roomId, null));
        assertThat(workContains(ArrivalProgress.Group.RESOLVED, null)).isTrue();
        assertThat(workContains(ArrivalProgress.Group.WAITING, null)).isFalse();
        var first = arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, 0, 1);
        assertThat(first.content()).hasSize(1);
        var next = arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, 1, 1);
        assertThat(next.totalElements()).isEqualTo(first.totalElements());
        if (!next.content().isEmpty())
            assertThat(next.content().get(0).bookingId())
                    .isNotEqualTo(first.content().get(0).bookingId());
    }

    @Test
    void resolvedHistoryFiltersWholeHotelDaysAndPaginatesNewestFirst() {
        LocalDate day = ARRIVAL.minusDays(7);
        Instant start = day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant end = start.plusSeconds(86400);
        resolvedBooking(BookingStatus.NO_SHOW, start.minusSeconds(1));
        String checkedIn = resolvedBooking(BookingStatus.CHECKED_IN, start);
        String checkedOut = resolvedBooking(BookingStatus.CHECKED_OUT, start.plusSeconds(3600));
        String noShow = resolvedBooking(BookingStatus.NO_SHOW, end.minusSeconds(1));
        String cancelled = resolvedBooking(BookingStatus.CANCELLED, end);
        resolvedBooking(BookingStatus.CANCELLED, end.plusSeconds(86400));

        var first = arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, day, day, 0, 2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.content()).extracting(b -> b.bookingCode()).containsExactly(noShow, checkedOut);
        assertThat(first.content())
                .extracting(b -> b.resolvedAt())
                .containsExactly(end.minusSeconds(1), start.plusSeconds(3600));
        var second = arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, day, day, 1, 2);
        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(second.content()).extracting(b -> b.bookingCode()).containsExactly(checkedIn);

        var twoDays = arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, day, day.plusDays(1), 0, 20);
        assertThat(twoDays.totalElements()).isEqualTo(4);
        assertThat(twoDays.content())
                .extracting(b -> b.bookingCode())
                .containsExactly(cancelled, noShow, checkedOut, checkedIn);
        for (String databaseZone : List.of("UTC", "Pacific/Honolulu")) {
            var sameRange = tx.execute(ignored -> {
                jdbc.execute("SET LOCAL TIME ZONE '" + databaseZone + "'");
                return arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, day, day.plusDays(1), 0, 20);
            });
            assertThat(sameRange.content())
                    .extracting(b -> b.bookingCode())
                    .containsExactly(cancelled, noShow, checkedOut, checkedIn);
        }
        assertThat(arrivalWork
                        .list(ArrivalProgress.Group.RESOLVED, null, 0, 100)
                        .content())
                .extracting(b -> b.bookingCode())
                .doesNotContain(checkedIn, checkedOut, noShow, cancelled);
        assertThat(workContains(ArrivalProgress.Group.ACTION_REQUIRED, null)).isTrue();
    }

    @Test
    void resolvedHistoryRejectsIncompleteReversedAndUnrelatedDateFilters() {
        assertThatThrownBy(() -> arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, ARRIVAL, null, 0, 20))
                .isInstanceOf(com.hanbay.hotel.common.exception.RequestValidationException.class);
        assertThatThrownBy(() -> arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, null, ARRIVAL, 0, 20))
                .isInstanceOf(com.hanbay.hotel.common.exception.RequestValidationException.class);
        assertThatThrownBy(() ->
                        arrivalWork.list(ArrivalProgress.Group.RESOLVED, null, ARRIVAL, ARRIVAL.minusDays(1), 0, 20))
                .isInstanceOf(com.hanbay.hotel.common.exception.RequestValidationException.class);
        assertThatThrownBy(() -> arrivalWork.list(ArrivalProgress.Group.WAITING, null, ARRIVAL, ARRIVAL, 0, 20))
                .isInstanceOf(com.hanbay.hotel.common.exception.RequestValidationException.class);
    }

    private String resolvedBooking(BookingStatus status, Instant at) {
        return tx.execute(ignored -> {
            Booking booking = Booking.builder()
                    .bookingCode("BK-HISTORY-" + UUID.randomUUID())
                    .roomType(types.findById(typeId).orElseThrow())
                    .checkInDate(ARRIVAL.minusDays(10))
                    .checkOutDate(ARRIVAL.minusDays(8))
                    .status(status)
                    .checkedInAt(status == BookingStatus.CHECKED_IN || status == BookingStatus.CHECKED_OUT ? at : null)
                    .checkedOutAt(status == BookingStatus.CHECKED_OUT ? at.plusSeconds(3 * 86400) : null)
                    .noShowAt(status == BookingStatus.NO_SHOW ? at : null)
                    .noShowFee(status == BookingStatus.NO_SHOW ? 0L : null)
                    .noShowOriginalTotal(status == BookingStatus.NO_SHOW ? 0L : null)
                    .noShowPolicyVersion(status == BookingStatus.NO_SHOW ? ArrivalPolicy.VERSION : null)
                    .cancelledAt(status == BookingStatus.CANCELLED ? at : null)
                    .build();
            return bookings.saveAndFlush(booking).getBookingCode();
        });
    }

    private void useUnifiedBooking() {
        tx.executeWithoutResult(status -> {
            Booking old = bookings.findById(bookingId).orElseThrow();
            Booking b =
                    newUnifiedBooking(old.getRoomType(), users.findById(staffId).orElseThrow());
            b.recordPayment(2_800_000);
            old.cancel(users.findById(staffId).orElseThrow(), "Thay fixture kiểm thử", CUTOFF);
            bookings.saveAndFlush(b);
            bookingId = b.getId();
            code = b.getBookingCode();
            savePayment(b, null, 2_800_000);
        });
    }

    @Test
    void customerCancellationEndsPendingStandaloneRescheduleAndPreservesItsAudit() {
        useUnifiedBooking();
        var request = changes.customerCreate(
                code,
                TOKEN,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Chờ đổi ngày"));
        bookingService.cancelBooking(code, new CancelBookingRequest("Khách hủy thay cho đổi lịch", null));
        var closed = changes.history(code).stream()
                .filter(row -> row.id().equals(request.id()))
                .findFirst()
                .orElseThrow();
        assertThat(closed.status()).isEqualTo(BookingChangeRequest.Status.REJECTED);
        assertThat(closed.snapshot()).containsEntry("autoClosed", true).containsEntry("customerInitiated", true);
        assertThat(closed.decisionNote()).contains("đã hủy");
        assertThat(changes.list(BookingChangeRequest.Status.PENDING, 0, 100).content())
                .noneMatch(row -> row.id().equals(request.id()));
        authenticate("MANAGER");
        assertThatThrownBy(() -> changes.approve(
                        request.id(), new BookingChangeCommand.Approve("stale", 0L, "Không được áp dụng")))
                .isInstanceOf(StayModificationException.class);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void customerWholeOrderCancellationClosesThePendingRescheduleWithoutDeletingHistory() {
        String[] group = confirmedGroup(2, true);
        var request = changes.customerCreate(
                group[0],
                TOKEN,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Chờ đổi toàn đơn"));
        UUID orderId = tx.execute(status ->
                bookings.findByBookingCode(group[0]).orElseThrow().getOrder().getId());
        orderService.cancel(orderId, TOKEN, new CancelBookingRequest("Khách hủy toàn đơn", null));
        var ended = changes.history(group[0]).stream()
                .filter(row -> row.id().equals(request.id()))
                .findFirst()
                .orElseThrow();
        assertThat(ended.status()).isEqualTo(BookingChangeRequest.Status.REJECTED);
        assertThat(ended.snapshot()).containsEntry("closureReason", "BOOKING_CANCELLED");
        assertThat(changes.history(group[1])).anyMatch(row -> row.id().equals(request.id()));
    }

    @Test
    void migrationRepairsOldPendingRequestsOnCancelledBookingsWithoutTouchingActiveRequests() throws Exception {
        useUnifiedBooking();
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Yêu cầu cũ cần phục hồi"));
        tx.executeWithoutResult(status -> bookings.findById(bookingId)
                .orElseThrow()
                .cancel(users.findById(staffId).orElseThrow(), "Mô phỏng dữ liệu cũ", CUTOFF));
        var sql = new org.springframework.core.io.ClassPathResource(
                "db/migration/V202611181700__close_reschedule_requests_for_cancelled_bookings.sql");
        try (var stream = sql.getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        var result = changes.history(code).get(0);
        assertThat(result.id()).isEqualTo(request.id());
        assertThat(result.status()).isEqualTo(BookingChangeRequest.Status.REJECTED);
        assertThat(result.snapshot()).containsEntry("autoClosed", true);
    }

    private UUID applyMonitoredChange(int hoursBeforeArrival, long nightlyPrice) {
        useUnifiedBooking();
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minus(Duration.ofHours(hoursBeforeArrival)));
        tx.executeWithoutResult(status -> org.springframework.test.util.ReflectionTestUtils.setField(
                types.findById(typeId).orElseThrow(), "basePrice", nightlyPrice));
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Giám sát thu hoàn sau đổi"));
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(
                        quote.quoteToken(),
                        null,
                        "Khách đã đồng ý",
                        true,
                        true,
                        Map.of(),
                        quote.additionalAmount() > 0 ? clock.instant().plusSeconds(3600) : null));
        return request.id();
    }

    @Test
    void settlementMonitorTracksPartialPaymentOverdueAndFinallyPaidFromActualTransactions() {
        UUID id = applyMonitoredChange(72, 1_000_000);
        var initial = changeSettlements.get(id);
        assertThat(initial.outstandingAmount()).isEqualTo(200_000);
        assertThat(initial.collectionStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(initial.additionalAtApproval()).isEqualTo(200_000);
        var qr = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        transfer(qr.transactionRef(), 100_000);
        assertThat(changeSettlements.get(id).outstandingAmount()).isEqualTo(100_000);
        when(clock.instant()).thenReturn(initial.balanceDueAt());
        assertThat(changeSettlements.get(id).collectionStatus()).isEqualTo("OVERDUE");
        var renewed = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        transfer(renewed.transactionRef(), 100_000);
        var paid = changeSettlements.get(id);
        assertThat(paid.collectionStatus()).isEqualTo("PAID");
        assertThat(paid.overdue()).isFalse();
        assertThat(paid.outstandingAmount()).isZero();
        assertThat(paid.receivedAmount()).isEqualTo(3_000_000);
        assertThat(paid.additionalAtApproval()).isEqualTo(200_000);
        assertThat(changeSettlements.list(code, 0, 20).content())
                .extracting(row -> row.requestId())
                .containsExactly(id);
        assertThat(changeSettlements.list("NO-SUCH-BOOKING", 0, 20).content()).isEmpty();
    }

    @Test
    void monitorMarksRefundedOnlyAfterBankReconciliationAndShowsCancelledScopeSeparately() {
        UUID id = applyMonitoredChange(72, 100_000);
        var waiting = changeSettlements.get(id);
        assertThat(waiting.refundStatus()).isEqualTo("PENDING");
        assertThat(waiting.pendingRefundAmount()).isEqualTo(2_500_000);
        assertThat(waiting.refundedAmount()).isZero();
        service.supplyRefundInformation(
                code, TOKEN, new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Bank", "KHACH A", "123456789"));
        String reference = "MONITOR-" + UUID.randomUUID();
        refunds.confirmRefund(waiting.refunds().get(0).refundId(), new ConfirmRefundRequest(reference));
        var completed = changeSettlements.get(id);
        assertThat(completed.refundStatus()).isEqualTo("REFUNDED");
        assertThat(completed.pendingRefundAmount()).isZero();
        assertThat(completed.refundedAmount()).isEqualTo(2_500_000);
        assertThat(completed.refunds().get(0).bankReference()).isEqualTo(reference);
        bookingService.cancelBooking(code, new CancelBookingRequest("Khách hủy sau đổi", null));
        var cancelled = changeSettlements.get(id);
        assertThat(cancelled.stayStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.pendingRefundAmount()).isEqualTo(300_000);
        assertThat(cancelled.refundStatus()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(cancelled.refundAtApproval()).isEqualTo(2_500_000);
    }

    @Test
    void monitorCountsEachOrderOnceAndKeepsRoomRefundsSeparateFromOtherRoomsDebts() {
        String[] group = confirmedGroup(2, true);
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minus(Duration.ofHours(72)));
        var request = changes.create(
                group[0],
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Đổi cả đơn"));
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(quote.quoteToken(), null, "Khách đồng ý", true, true, Map.of(), null));
        UUID orderId = tx.execute(status ->
                bookings.findByBookingCode(group[0]).orElseThrow().getOrder().getId());
        String orderCode = orderService.get(orderId, TOKEN).orderCode();
        var result = changeSettlements.list(orderCode, 0, 20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).roomCount()).isEqualTo(2);
        assertThat(result.content().get(0).receivedAmount()).isEqualTo(5_600_000);
        assertThat(result.content().get(0).pendingRefundAmount()).isEqualTo(800_000);
        assertThat(result.content().get(0).refundedAmount()).isZero();
        assertThat(changeSettlements.list(group[1], 0, 20).content()).hasSize(1);
    }

    @Test
    void monitorListsOnlyTheLatestAppliedChangeWhileKeepingBothInHistory() {
        UUID first = applyMonitoredChange(72, 800_000);
        Instant nextEventAt = clock.instant().plusSeconds(1);
        when(clock.instant()).thenReturn(nextEventAt);
        var next = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(20),
                        ARRIVAL.plusDays(23),
                        "Đổi lần nữa có ngoại lệ"));
        var quote = changes.allowException(next.id(), "Quản lý xét lần đổi thứ hai");
        changes.approve(
                next.id(),
                new BookingChangeCommand.Approve(
                        quote.quoteToken(), null, "Khách đồng ý lại", true, true, Map.of(), null));
        var result = changeSettlements.list(code, 0, 20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).requestId()).isEqualTo(next.id());
        assertThat(changes.history(code)).extracting(row -> row.id()).contains(first, next.id());
    }

    @Test
    void unifiedNoShowAfterCheaperRescheduleUsesOriginalFeeAndReleasesInventory() {
        useUnifiedBooking();
        rescheduleToCheaperStay(code);

        var preview = service.preview(code);
        assertThat(preview.canMarkNoShow()).isTrue();
        var closed = service.markNoShow(code, request(preview, 800_000));

        assertThat(closed.bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(closed.originalTotalAmount()).isEqualTo(300_000);
        assertThat(closed.feeAmount()).isEqualTo(800_000);
        assertThat(closed.outstandingAmount()).isEqualTo(500_000);
        assertThat(closed.refundAmount()).isEqualTo(2_500_000);
        assertThat(closed.refunds()).hasSize(1);
        assertThat(service.markNoShow(code, request(preview, 800_000)).refunds())
                .hasSize(1);
        long availableAfter = tx.execute(s -> availability
                .countAvailableRooms(List.of(typeId), ARRIVAL.plusDays(10), ARRIVAL.plusDays(13))
                .get(typeId));
        assertThat(availableAfter).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unifiedCancellationAfterCheaperRescheduleKeepsOriginalFeeAndOutstandingBalance(boolean frontDesk) {
        useUnifiedBooking();
        rescheduleToCheaperStay(code);

        if (frontDesk) {
            var quote = changes.cancellationQuote(code);
            assertThat(quote.feeAmount()).isEqualTo(800_000);
            var result = changes.create(
                    code,
                    new BookingChangeCommand.Create(
                            BookingChangeRequest.Type.CANCELLATION, null, null, "Khách xác nhận hủy kỳ mới"));
            assertThat(result.status()).isEqualTo(BookingChangeRequest.Status.APPROVED);
        } else {
            var eligibility = bookingService.getCancellationEligibility(code);
            assertThat(eligibility.feeAmount()).isEqualTo(800_000);
            assertThat(eligibility.outstandingAmount()).isEqualTo(500_000);
            assertThat(eligibility.refundAmount()).isZero();
            var result = bookingService.cancelBooking(code, new CancelBookingRequest("Hủy sát giờ kỳ mới", null));
            assertThat(result.refundAmount()).isZero();
        }

        var settlement = service.preview(code);
        assertThat(settlement.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(settlement.feeAmount()).isEqualTo(800_000);
        assertThat(settlement.outstandingAmount()).isEqualTo(500_000);
        assertThat(settlement.refundAmount()).isEqualTo(2_500_000);
        assertThat(settlement.refunds()).hasSize(1);
        tx.executeWithoutResult(s -> {
            var booking = bookings.findById(bookingId).orElseThrow();
            assertThat(booking.getTotalAmount()).isEqualTo(800_000);
            assertThat(booking.getPaidAmount()).isEqualTo(300_000);
            assertThat(booking.getPolicyFeeTotal()).isEqualTo(800_000);
        });
    }

    @Test
    void unifiedWholeOrderCancellationAfterCheaperRescheduleKeepsOriginalFeeForEveryRoom() {
        var codes = confirmedGroup(2, true);
        rescheduleToCheaperStay(codes[0]);
        UUID orderId = tx.execute(s ->
                bookings.findByBookingCode(codes[0]).orElseThrow().getOrder().getId());

        var eligibility = orderService.cancellationEligibility(orderId, TOKEN);
        assertThat(eligibility.feeAmount()).isEqualTo(1_600_000);
        assertThat(eligibility.outstandingAmount()).isEqualTo(1_000_000);
        assertThat(eligibility.refundAmount()).isZero();
        var result = orderService.cancel(orderId, TOKEN, new CancelBookingRequest("Cả đoàn hủy sát giờ", null));

        assertThat(result.status()).isEqualTo(BookingOrderStatus.CANCELLED);
        assertThat(result.totalAmount()).isEqualTo(1_600_000);
        assertThat(result.paidAmount()).isEqualTo(600_000);
        assertThat(result.refundAmount()).isEqualTo(5_000_000);
        assertThat(result.rooms()).allSatisfy(room -> {
            assertThat(room.status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(room.totalAmount()).isEqualTo(800_000);
            assertThat(room.paidAmount()).isEqualTo(300_000);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyCancellationSupersedesPendingRescheduleAndReleasesRoomsBeforeSettlement(boolean wholeOrder) {
        String[] codes = wholeOrder ? confirmedGroup() : new String[] {code};
        var reschedule = changes.create(
                codes[0],
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Khách xin đổi ngày"));
        var command = new BookingChangeCommand.Create(
                BookingChangeRequest.Type.CANCELLATION, null, null, "Khách xác nhận hủy thay vì đổi");

        var cancellation = changes.create(codes[0], command);

        assertThat(cancellation.status()).isEqualTo(BookingChangeRequest.Status.PENDING);
        assertThat(changes.create(codes[0], command).id()).isEqualTo(cancellation.id());
        assertThat(changes.history(codes[0]))
                .filteredOn(r -> r.id().equals(reschedule.id()))
                .singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(BookingChangeRequest.Status.REJECTED));
        for (String member : codes) {
            var settlement = service.preview(member);
            assertThat(settlement.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(settlement.settlementReviewRequired()).isTrue();
            tx.executeWithoutResult(s -> assertThat(
                            bookings.findByBookingCode(member).orElseThrow().getTotalAmount())
                    .isZero());
        }
        assertThat(available()).isEqualTo(wholeOrder ? codes.length : 1);
    }

    private void rescheduleToCheaperStay(String bookingCode) {
        when(clock.instant())
                .thenReturn(ARRIVAL.atTime(14, 0)
                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toInstant()
                        .minusSeconds(5 * 86400));
        tx.executeWithoutResult(s -> org.springframework.test.util.ReflectionTestUtils.setField(
                types.findById(typeId).orElseThrow(), "basePrice", 100_000L));
        var request = changes.create(
                bookingCode,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Đổi sớm sang kỳ rẻ hơn"));
        authenticate("MANAGER");
        var quote = changes.quote(request.id());
        assertThat(quote.feeAmount()).isZero();
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(
                        quote.quoteToken(), null, "Khách đồng ý giá mới", true, true, Map.of(), null));
        authenticate("RECEPTIONIST");
        when(clock.instant()).thenReturn(CUTOFF.plusSeconds(10 * 86400));
    }

    @Test
    void expiredAdditionalBalanceQrCanBeRenewedWithoutDuplicateKey() {
        useUnifiedBooking();
        Instant now = CUTOFF.minusSeconds(5 * 86400);
        when(clock.instant()).thenReturn(now);
        tx.executeWithoutResult(s -> org.springframework.test.util.ReflectionTestUtils.setField(
                types.findById(typeId).orElseThrow(), "basePrice", 1_000_000L));
        var change = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Đổi kỳ đắt hơn"));
        authenticate("MANAGER");
        var quote = changes.quote(change.id());
        changes.approve(
                change.id(),
                new BookingChangeCommand.Approve(
                        quote.quoteToken(), null, "Khách đã đồng ý", true, true, Map.of(), now.plusSeconds(86400)));
        var first = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        assertThat(paymentService
                        .createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN)
                        .transactionRef())
                .isEqualTo(first.transactionRef());
        when(clock.instant()).thenReturn(first.expiredAt().plusSeconds(1));
        var renewed = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        assertThat(renewed.transactionRef()).isNotEqualTo(first.transactionRef());
        assertThat(renewed.amount()).isEqualTo(200_000);
    }

    @Test
    void earlyCheckoutAfterCompletedRescheduleRefundUsesRemainingReceipts() {
        useUnifiedBooking();
        rescheduleToCheaperStay(code);
        when(clock.instant())
                .thenReturn(ARRIVAL.plusDays(10)
                        .atTime(14, 0)
                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toInstant());
        var destination = new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Ngân hàng", "KHACH A", "123456789");
        var settlement = service.supplyRefundInformation(code, TOKEN, destination);
        refunds.confirmRefund(
                settlement.refunds().get(0).refundId(), new ConfirmRefundRequest("REPRICE-" + UUID.randomUUID()));
        checkIn.checkIn(code, new CheckInGuestRequest(roomId));
        var date = ARRIVAL.plusDays(11);
        when(clock.instant())
                .thenReturn(
                        date.atTime(12, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        var result = stayModifications.earlyCheckout(
                bookingId,
                new com.hanbay.hotel.booking.dto.request.EarlyCheckoutRequest(
                        date, destination, "Khách trả sớm sau đổi lịch", null, null, 0L));
        assertThat(result.refundAmount()).isEqualTo(160_000);
        tx.executeWithoutResult(s -> {
            var payment = payments.findAllForBookingSettlement(bookingId).get(0);
            assertThat(payment.getRefunds()).hasSize(2);
            assertThat(payment.getRefunds().stream()
                            .mapToLong(r -> r.getAmount().longValueExact())
                            .sum())
                    .isEqualTo(2_660_000);
            assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
            assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.DIRTY);
        });
    }

    private void closeWithFeeDebt(boolean cancelled) {
        useUnifiedBooking();
        rescheduleToCheaperStay(code);
        if (cancelled) bookingService.cancelBooking(code, new CancelBookingRequest("Khách xác nhận hủy", null));
        else service.markNoShow(code, request(service.preview(code), 800_000));
        assertThat(service.preview(code).outstandingAmount()).isEqualTo(500_000);
    }

    private void transfer(long id, String paymentCode, long amount) {
        webhooks.handleSePayWebhook(
                "Apikey " + sepay.webhookApiKey(),
                new com.hanbay.hotel.payment.dto.request.SePayWebhookRequest(
                        id,
                        "TEST",
                        LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh")),
                        sepay.accountNumber(),
                        null,
                        paymentCode,
                        paymentCode,
                        com.hanbay.hotel.payment.dto.SePayTransferType.IN,
                        amount,
                        amount,
                        "Thu phí kiểm thử",
                        "FEE-" + id));
    }

    private long transfer(String paymentCode, long amount) {
        long id = FEE_EVENTS.incrementAndGet();
        transfer(id, paymentCode, amount);
        return id;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closedFeeCashCollectionIsPartialIdempotentAndNeverReopensStay(boolean cancelled) {
        closeWithFeeDebt(cancelled);
        UUID receipt = UUID.randomUUID();
        var first = service.collectFee(code, receipt, 200_000, 500_000, "Đã nhận tiền mặt từ khách");
        assertThat(first.outstandingAmount()).isEqualTo(300_000);
        assertThat(service.collectFee(code, receipt, 200_000, 500_000, "Gửi lại")
                        .outstandingAmount())
                .isEqualTo(300_000);
        assertThatThrownBy(() -> service.collectFee(code, receipt, 100_000, 300_000, "Đổi số tiền"))
                .isInstanceOf(StayModificationException.class);
        assertThatThrownBy(() -> service.collectFee(code, UUID.randomUUID(), 400_000, 300_000, "Thu vượt"))
                .isInstanceOf(StayModificationException.class);
        var last = service.collectFee(code, UUID.randomUUID(), 300_000, 300_000, "Thu đủ phần còn lại");
        assertThat(last.outstandingAmount()).isZero();
        assertThat(last.bookingStatus()).isEqualTo(cancelled ? BookingStatus.CANCELLED : BookingStatus.NO_SHOW);
        tx.executeWithoutResult(
                s -> assertThat(bookings.findById(bookingId).orElseThrow().getAssignedRoom())
                        .isNull());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void feeQrSupportsPartialPaymentRenewalAndRefundsOverpaymentWithoutReopening(boolean cancelled) {
        closeWithFeeDebt(cancelled);
        var first = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        assertThat(first.amount()).isEqualTo(500_000);
        assertThat(paymentService.getPaymentStatus(first.paymentId(), TOKEN).policyFeePayment())
                .isTrue();
        transfer(first.transactionRef(), 200_000);
        assertThat(service.preview(code).outstandingAmount()).isEqualTo(300_000);
        var renewed = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        assertThat(renewed.transactionRef()).isNotEqualTo(first.transactionRef());
        assertThat(renewed.amount()).isEqualTo(300_000);
        long event = transfer(renewed.transactionRef(), 400_000);
        transfer(event, renewed.transactionRef(), 400_000);
        var settlement = service.preview(code);
        assertThat(settlement.outstandingAmount()).isZero();
        assertThat(settlement.availableAmount()).isEqualTo(800_000);
        assertThat(settlement.refundAmount()).isEqualTo(2_600_000);
        assertThat(settlement.bookingStatus()).isEqualTo(cancelled ? BookingStatus.CANCELLED : BookingStatus.NO_SHOW);
    }

    @Test
    void expiredFeeQrTransferIsRefundedAndFreshQrStillCollectsOnlyOutstandingFee() {
        closeWithFeeDebt(false);
        var expired = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        when(clock.instant()).thenReturn(expired.expiredAt().plusSeconds(1));
        transfer(expired.transactionRef(), 500_000);
        var settlement = service.preview(code);
        assertThat(settlement.outstandingAmount()).isEqualTo(500_000);
        assertThat(settlement.availableAmount()).isEqualTo(300_000);
        assertThat(settlement.refundAmount()).isEqualTo(3_000_000);
        var renewed = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        assertThat(renewed.transactionRef()).isNotEqualTo(expired.transactionRef());
        assertThat(renewed.amount()).isEqualTo(500_000);
        transfer(renewed.transactionRef(), 500_000);
        assertThat(service.preview(code).outstandingAmount()).isZero();
        assertThat(service.preview(code).bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void lateFeeQrTransferAfterCashCollectionIsRefundedWithoutDoubleCredit() {
        closeWithFeeDebt(false);
        var qr = paymentService.createPaymentRequest(new CreatePaymentRequest(bookingId), TOKEN);
        service.collectFee(code, UUID.randomUUID(), 500_000, 500_000, "Khách chọn trả tiền mặt");
        transfer(qr.transactionRef(), 500_000);
        var settlement = service.preview(code);
        assertThat(settlement.outstandingAmount()).isZero();
        assertThat(settlement.availableAmount()).isEqualTo(800_000);
        assertThat(settlement.refundAmount()).isEqualTo(3_000_000);
        assertThat(settlement.bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void feeCollectionOnOneOrderRoomDoesNotUseAnotherRoomsCredit() {
        var codes = confirmedGroup(2, true);
        rescheduleToCheaperStay(codes[0]);
        service.markNoShow(codes[0], request(service.preview(codes[0]), 800_000));
        service.collectFee(codes[0], UUID.randomUUID(), 500_000, 500_000, "Khách phòng một trả tiền mặt");
        assertThat(service.preview(codes[0]).outstandingAmount()).isZero();
        var other = service.preview(codes[1]);
        assertThat(other.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(other.availableAmount()).isEqualTo(300_000);
    }

    @Test
    void unifiedCancellationAtFrontDeskClosesImmediatelyAndIsIdempotentWithoutManager() {
        useUnifiedBooking();
        var command = new BookingChangeCommand.Create(
                BookingChangeRequest.Type.CANCELLATION, null, null, "Khách xác nhận hủy");
        var result = changes.create(code, command);
        assertThat(result.status()).isEqualTo(BookingChangeRequest.Status.APPROVED);
        assertThat(service.preview(code).bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(service.preview(code).feeAmount()).isEqualTo(800_000);
        assertThat(service.preview(code).refundAmount()).isEqualTo(2_000_000);
        assertThat(changes.create(code, command).id()).isEqualTo(result.id());
        assertThat(available()).isEqualTo(1);
        tx.executeWithoutResult(
                status -> assertThat(bookings.findById(bookingId).orElseThrow().getPolicyFees())
                        .hasSize(1));
    }

    @Test
    void unifiedRescheduleRequiresConsentKeepsOriginalBaseAndCancellationKeepsAccruedFee() {
        useUnifiedBooking();
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minusSeconds(30 * 3600));
        var r = changes.customerCreate(
                code,
                TOKEN,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Khách đổi ngày"));
        authenticate("MANAGER");
        var q = changes.quote(r.id());
        assertThat(q.feeAmount()).isEqualTo(400_000);
        assertThatThrownBy(() ->
                        changes.approve(r.id(), new BookingChangeCommand.Approve(q.quoteToken(), null, "Chưa đồng ý")))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
        changes.customerConsent(code, TOKEN, r.id(), q.quoteToken());
        changes.approve(
                r.id(),
                new BookingChangeCommand.Approve(q.quoteToken(), null, "Khách đã đồng ý", false, true, Map.of(), null));
        tx.executeWithoutResult(status -> {
            Booking b = bookings.findById(bookingId).orElseThrow();
            assertThat(b.getOriginalFirstNightAmount()).isEqualTo(800_000);
            assertThat(b.getPolicyFeeTotal()).isEqualTo(400_000);
            assertThat(b.getTotalAmount()).isEqualTo(2_800_000);
        });
        var cancelled = bookingService.cancelBooking(
                code, new com.hanbay.hotel.booking.dto.request.CancelBookingRequest("Khách hủy trước kỳ mới", null));
        assertThat(cancelled.refundAmount()).isEqualTo(2_400_000);
        assertThat(service.preview(code).feeAmount()).isEqualTo(400_000);
        tx.executeWithoutResult(
                status -> assertThat(bookings.findById(bookingId).orElseThrow().getPolicyFees())
                        .hasSize(2));
    }

    @Test
    void unifiedRejectsSecondRescheduleAndDatesBeyondOriginalNinetyDays() {
        useUnifiedBooking();
        var r = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE, ARRIVAL.plusDays(91), ARRIVAL.plusDays(94), "Vượt hạn"));
        assertThat(changes.quote(r.id()).canApprove()).isFalse();
    }

    @Test
    void shortenedPeriodQuotesAndReservesActualRefundOnlyAfterManagerAndCustomerAgree() {
        useUnifiedBooking();
        tx.executeWithoutResult(status -> bookings.findById(bookingId).orElseThrow().getNights().stream()
                .filter(night -> night.getStayDate().equals(ARRIVAL.plusDays(2)))
                .findFirst()
                .orElseThrow()
                .setPricePerNight(1_100_000));
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minus(Duration.ofHours(72)));
        var request = changes.customerCreate(
                code,
                TOKEN,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE, ARRIVAL, ARRIVAL.plusDays(2), "Rút một đêm"));
        var draft = changes.customerQuote(code, TOKEN, request.id());
        assertThat(draft.pricingAvailable()).isTrue();
        assertThat(draft.canApprove()).isFalse();
        assertThat(draft.newTotalAmount()).isEqualTo(1_700_000);
        assertThat(draft.feeAmount()).isZero();
        assertThat(draft.refundAmount()).isEqualTo(1_100_000);
        tx.executeWithoutResult(status -> {
            assertThat(bookings.findById(bookingId).orElseThrow().getNights()).hasSize(3);
            assertThat(payments.findAllForBookingSettlement(bookingId).get(0).getRefunds())
                    .isEmpty();
        });
        authenticate("MANAGER");
        var allowed = changes.allowException(request.id(), "Chấp thuận rút ngắn trước khi ở");
        changes.customerConsent(code, TOKEN, request.id(), allowed.quoteToken());
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(
                        allowed.quoteToken(), null, "Khách đồng ý mức hoàn", false, true, Map.of(), null));
        tx.executeWithoutResult(status -> {
            var booking = bookings.findById(bookingId).orElseThrow();
            assertThat(booking.getNights()).hasSize(2);
            assertThat(booking.getTotalAmount()).isEqualTo(1_700_000);
            assertThat(booking.getOriginalFirstNightAmount()).isEqualTo(800_000);
            assertThat(payments.findAllForBookingSettlement(bookingId).get(0).getRefunds())
                    .singleElement()
                    .satisfies(refund -> {
                        assertThat(refund.getAmount()).isEqualByComparingTo("1100000");
                        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
                    });
        });
    }

    @Test
    void addedNightKeepsSavedNightPricesAndRecordsOnlyTheAdditionalBalance() {
        useUnifiedBooking();
        tx.executeWithoutResult(status -> bookings.findById(bookingId).orElseThrow().getNights().stream()
                .filter(night -> night.getStayDate().equals(ARRIVAL.plusDays(2)))
                .findFirst()
                .orElseThrow()
                .setPricePerNight(1_100_000));
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minus(Duration.ofHours(12)));
        var request = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE, ARRIVAL, ARRIVAL.plusDays(4), "Thêm một đêm"));
        var draft = changes.quote(request.id());
        assertThat(draft.pricingAvailable()).isTrue();
        assertThat(draft.newTotalAmount()).isEqualTo(3_600_000);
        assertThat(draft.feeAmount()).isZero();
        assertThat(draft.additionalAmount()).isEqualTo(800_000);
        authenticate("MANAGER");
        var allowed = changes.allowException(request.id(), "Chấp thuận gia hạn cùng ngày nhận");
        changes.approve(
                request.id(),
                new BookingChangeCommand.Approve(
                        allowed.quoteToken(),
                        null,
                        "Khách đồng ý thu thêm",
                        true,
                        true,
                        Map.of(),
                        arrival.plus(Duration.ofDays(1))));
        tx.executeWithoutResult(status -> {
            var booking = bookings.findById(bookingId).orElseThrow();
            assertThat(booking.getNights()).hasSize(4);
            assertThat(booking.getNights().stream()
                            .sorted(Comparator.comparing(BookingNight::getStayDate))
                            .map(BookingNight::getPricePerNight)
                            .toList())
                    .containsExactly(800_000L, 900_000L, 1_100_000L, 800_000L);
            assertThat(booking.getTotalAmount()).isEqualTo(3_600_000);
            assertThat(booking.getPaidAmount()).isEqualTo(2_800_000);
            assertThat(booking.getPolicyFeeTotal()).isZero();
        });
    }

    @Test
    void unifiedNoShowAfterPaidRescheduleCapsTotalFeeAndReductionCreatesAppendOnlyAdjustment() {
        useUnifiedBooking();
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minusSeconds(30 * 3600));
        var r = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Đổi có phí"));
        authenticate("MANAGER");
        var q = changes.quote(r.id());
        changes.approve(
                r.id(),
                new BookingChangeCommand.Approve(
                        q.quoteToken(), null, "Khách đồng ý tổng giá", true, true, Map.of(), null));
        when(clock.instant()).thenReturn(CUTOFF.plusSeconds(10 * 86400));
        var closed = service.markNoShow(code, request(service.preview(code), 800_000));
        assertThat(closed.feeAmount()).isEqualTo(800_000);
        assertThat(closed.refundAmount()).isEqualTo(2_000_000);
        tx.executeWithoutResult(status -> {
            var b = bookings.findById(bookingId).orElseThrow();
            assertThat(b.getPolicyFees().stream()
                            .mapToLong(BookingPolicyFee::getAmount)
                            .sum())
                    .isEqualTo(800_000);
            assertThat(b.getPolicyFees()).hasSize(2);
        });
        authenticate("RECEPTIONIST");
        assertThatThrownBy(() -> service.reduceFee(code, 0, "Xin miễn")).isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        var reduced = service.reduceFee(code, 200_000, "Quản lý chấp thuận giảm theo chứng từ");
        assertThat(reduced.feeAmount()).isEqualTo(200_000);
        assertThat(reduced.refundAmount()).isEqualTo(2_600_000);
        service.reduceFee(code, 200_000, "Gửi lại cùng kết quả");
        tx.executeWithoutResult(status -> {
            var b = bookings.findById(bookingId).orElseThrow();
            assertThat(b.getPolicyFees()).hasSize(3);
            assertThat(b.getPolicyFees().stream()
                            .mapToLong(BookingPolicyFee::getAmount)
                            .sum())
                    .isEqualTo(200_000);
        });
    }

    @Test
    void customerPolicyAndRescheduleCannotBeAccessedWithAnotherGuestsToken() {
        useUnifiedBooking();
        when(currentUser.currentUserId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.customerPolicy(code, "wrong-token"))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingNotFoundException.class);
        assertThatThrownBy(() -> changes.customerCreate(
                        code,
                        "wrong-token",
                        new BookingChangeCommand.Create(
                                BookingChangeRequest.Type.RESCHEDULE,
                                ARRIVAL.plusDays(10),
                                ARRIVAL.plusDays(13),
                                "Sai quyền")))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingNotFoundException.class);
        var r = changes.customerCreate(
                code,
                TOKEN,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Khách không đăng nhập"));
        assertThat(r.requestedBy()).isEqualTo("Khách qua mã truy cập");
        assertThat(changes.customerHistory(code, TOKEN)).hasSize(1);
    }

    @Test
    void unifiedSecondRescheduleHasNoAutomaticEntitlementAndUnpaidBalanceKeepsConfirmed() {
        useUnifiedBooking();
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(clock.instant()).thenReturn(arrival.minusSeconds(10 * 3600));
        var r = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(10),
                        ARRIVAL.plusDays(13),
                        "Đổi sát giờ"));
        authenticate("MANAGER");
        var q = changes.quote(r.id());
        assertThat(q.additionalAmount()).isEqualTo(400_000);
        assertThatThrownBy(() -> changes.approve(
                        r.id(),
                        new BookingChangeCommand.Approve(
                                q.quoteToken(), null, "Thiếu hạn thu", true, true, Map.of(), null)))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
        changes.approve(
                r.id(),
                new BookingChangeCommand.Approve(
                        q.quoteToken(),
                        null,
                        "Khách đồng ý hạn thu",
                        true,
                        true,
                        Map.of(),
                        clock.instant().plusSeconds(86400)));
        assertThat(service.preview(code).bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(service.preview(code).balanceDueAt()).isNotNull();
        var second = changes.create(
                code,
                new BookingChangeCommand.Create(
                        BookingChangeRequest.Type.RESCHEDULE,
                        ARRIVAL.plusDays(20),
                        ARRIVAL.plusDays(23),
                        "Đổi lần hai"));
        assertThat(changes.quote(second.id()).canApprove()).isFalse();
        assertThat(changes.quote(second.id()).blockReason()).contains("một lần");
        authenticate("RECEPTIONIST");
        assertThatThrownBy(() -> changes.allowException(second.id(), "Xin đổi thêm"))
                .isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        var exceptional = changes.allowException(second.id(), "Đã đối chiếu chứng từ; cho xét lần hai");
        assertThat(exceptional.canApprove()).isTrue();
        assertThat(exceptional.feeAmount()).isEqualTo(800_000);
        assertThatThrownBy(() -> changes.approve(
                        second.id(),
                        new BookingChangeCommand.Approve(exceptional.quoteToken(), null, "Chưa đồng ý lại")))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
    }

    @Test
    void selectedArrivalScopeUpdatesAllFourRoomsWithoutChangingAdmissionOrFinances() {
        var codes = confirmedGroup(4);
        var selected = List.of(codes);
        var eta = CUTOFF.plusSeconds(2 * 3600 + 50 * 60);
        var result =
                service.estimate(codes[0], new ArrivalHandlingRequest.Estimate(eta, "Cả đoàn báo đến 08:50", selected));
        assertThat(result.orderRooms()).hasSize(4).allSatisfy(r -> {
            assertThat(r.expectedAt()).isEqualTo(eta);
            assertThat(r.receptionDeadline()).isEqualTo(CUTOFF);
            assertThat(r.holdUntil()).isNull();
        });
        assertThat(result.canMarkNoShow()).isTrue(); // ETA alone never grants reception after cutoff.
        tx.executeWithoutResult(status -> {
            for (String c : codes) {
                var b = bookings.findByBookingCode(c).orElseThrow();
                assertThat(b.getTotalAmount()).isEqualTo(2_800_000);
                assertThat(b.getPolicyFeeTotal()).isZero();
                assertThat(b.getStatusHistories())
                        .anySatisfy(h -> assertThat(h.getReason()).contains("4 phòng cùng đơn"));
            }
        });
        assertThat(arrivalWork
                        .list(ArrivalProgress.Group.ACTION_REQUIRED, null, 0, 100)
                        .content())
                .filteredOn(r -> selected.contains(r.bookingCode()))
                .allSatisfy(r -> {
                    assertThat(r.orderCode()).isEqualTo(result.orderCode());
                    assertThat(r.roomCount()).isEqualTo(4);
                });
    }

    @Test
    void omittedScopeStillAffectsOneRoomAndExplicitSubsetDoesNotOverwriteOtherRooms() {
        var codes = confirmedGroup(4);
        var eta = CUTOFF.plusSeconds(3600);
        service.estimate(codes[0], new ArrivalHandlingRequest.Estimate(eta, "Chỉ một phòng"));
        assertThat(service.preview(codes[1]).progress().expectedAt()).isNull();
        service.estimate(
                codes[0],
                new ArrivalHandlingRequest.Estimate(
                        eta.plusSeconds(600), "Hai phòng đến chung", List.of(codes[0], codes[2])));
        assertThat(service.preview(codes[2]).progress().expectedAt()).isEqualTo(eta.plusSeconds(600));
        assertThat(service.preview(codes[1]).progress().expectedAt()).isNull();
        assertThat(service.preview(codes[3]).progress().expectedAt()).isNull();
    }

    @Test
    void scopeCannotIncludeAnotherOrderOrAnAlreadyCheckedInRoomAndNeverPartiallyUpdates() {
        var codes = confirmedGroup();
        var eta = CUTOFF.plusSeconds(3600);
        assertThatThrownBy(() -> service.estimate(
                        codes[0], new ArrivalHandlingRequest.Estimate(eta, "Sai phạm vi", List.of(codes[0], code))))
                .isInstanceOf(StayModificationException.class);
        tx.executeWithoutResult(status -> {
            var b = bookings.findByBookingCode(codes[1]).orElseThrow();
            b.checkIn(
                    rooms.findById(roomId).orElseThrow(),
                    users.findById(staffId).orElseThrow(),
                    CUTOFF.minusSeconds(3600),
                    null);
        });
        assertThat(service.preview(codes[0]).orderRooms())
                .filteredOn(r -> r.bookingCode().equals(codes[1]))
                .allSatisfy(r -> assertThat(r.canManageArrival()).isFalse());
        assertThatThrownBy(() -> service.estimate(
                        codes[0], new ArrivalHandlingRequest.Estimate(eta, "Đã nhận một phòng", List.of(codes))))
                .isInstanceOf(StayModificationException.class);
        assertThat(service.preview(codes[0]).progress().expectedAt()).isNull();
    }

    @Test
    void invalidTimeForAnySelectedRoomRejectsTheWholeBatch() {
        var codes = confirmedGroup();
        tx.executeWithoutResult(status -> bookings.findByBookingCode(codes[1])
                .orElseThrow()
                .rescheduleConfirmed(
                        ARRIVAL.plusDays(1),
                        ARRIVAL.plusDays(4),
                        List.of(),
                        2_800_000,
                        0,
                        users.findById(staffId).orElseThrow(),
                        "Khác kỳ nhận"));
        when(clock.instant()).thenReturn(CUTOFF.minusSeconds(12 * 3600));
        assertThatThrownBy(() -> service.estimate(
                        codes[0],
                        new ArrivalHandlingRequest.Estimate(
                                CUTOFF.minusSeconds(10 * 3600), "Chưa vào kỳ phòng thứ hai", List.of(codes))))
                .isInstanceOf(StayModificationException.class);
        assertThat(service.preview(codes[0]).progress().expectedAt()).isNull();
    }

    @Test
    void groupLateArrivalRequiresManagerAndAChangedRequestCannotBeApprovedPartially() {
        var codes = confirmedGroup(4);
        var eta = CUTOFF.plusSeconds(3600);
        var until = CUTOFF.plusSeconds(7200);
        service.hold(codes[0], new ArrivalHandlingRequest.Hold(until, "Cả đoàn xin nhận muộn", eta, List.of(codes)));
        for (String c : codes) {
            var r = service.preview(c);
            assertThat(r.holdUntil()).isNull();
            assertThat(r.holdRequestedUntil()).isEqualTo(until);
        }
        assertThatThrownBy(() -> service.decideHold(
                        codes[0],
                        new ArrivalHandlingRequest.HoldDecision(until, true, "Không đủ quyền", List.of(codes))))
                .isInstanceOf(AccessDeniedException.class);
        authenticate("MANAGER");
        service.decideHold(
                codes[0], new ArrivalHandlingRequest.HoldDecision(until, true, "Duyệt cả đoàn", List.of(codes)));
        for (String c : codes) {
            assertThat(service.preview(c).holdUntil()).isEqualTo(until);
            assertThat(service.preview(c).canMarkNoShow()).isFalse();
        }
        service.releaseHold(
                codes[0],
                new ArrivalHandlingRequest.Contact("Kết thúc ngoại lệ của hai phòng", List.of(codes[0], codes[1])));
        assertThat(service.preview(codes[0]).holdUntil()).isNull();
        assertThat(service.preview(codes[2]).holdUntil()).isEqualTo(until);
        authenticate("RECEPTIONIST");
        var later = until.plusSeconds(3600);
        service.hold(
                codes[0],
                new ArrivalHandlingRequest.Hold(later, "Đề nghị mới hai phòng", eta, List.of(codes[0], codes[1])));
        service.hold(codes[1], new ArrivalHandlingRequest.Hold(later.plusSeconds(3600), "Phòng này đổi giờ", eta));
        authenticate("MANAGER");
        assertThatThrownBy(() -> service.decideHold(
                        codes[0],
                        new ArrivalHandlingRequest.HoldDecision(
                                later, true, "Dữ liệu đã đổi", List.of(codes[0], codes[1]))))
                .isInstanceOf(StayModificationException.class);
        assertThat(service.preview(codes[0]).holdUntil()).isNull();
    }

    @Test
    void contactCanApplyToSelectedRoomsButNoShowStillClosesOnlyTheChosenRoom() {
        var codes = confirmedGroup();
        var selected = List.of(codes);
        service.structuredContact(
                codes[0],
                new ArrivalHandlingRequest.StructuredContact(
                        ArrivalContact.Channel.PHONE,
                        ArrivalContact.Outcome.NO_ANSWER,
                        "Đã gọi đại diện đoàn",
                        selected));
        assertThat(service.preview(codes[1]).progress().lastContactOutcome())
                .isEqualTo(ArrivalContact.Outcome.NO_ANSWER);
        var result = service.markNoShow(codes[0], request(service.preview(codes[0]), 800_000));
        assertThat(result.bookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(service.preview(codes[1]).bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void updatedEtaPastTheRequestedHoldPreventsApprovalOfEveryRoomInTheSelection() {
        var codes = confirmedGroup();
        var until = CUTOFF.plusSeconds(7200);
        service.hold(
                codes[0],
                new ArrivalHandlingRequest.Hold(
                        until, "Hai phòng xin đến muộn", CUTOFF.plusSeconds(3600), List.of(codes)));
        service.estimate(
                codes[1], new ArrivalHandlingRequest.Estimate(until.plusSeconds(60), "Phòng thứ hai đổi giờ hẹn"));
        authenticate("MANAGER");
        assertThatThrownBy(() -> service.decideHold(
                        codes[0],
                        new ArrivalHandlingRequest.HoldDecision(until, true, "Hạn chưa phù hợp", List.of(codes))))
                .isInstanceOf(StayModificationException.class);
        for (String c : codes) assertThat(service.preview(c).holdUntil()).isNull();
    }

    private boolean workContains(ArrivalProgress.Group group, ArrivalProgress.Reason reason) {
        return arrivalWork.list(group, reason, 0, 100).content().stream()
                .anyMatch(b -> b.bookingCode().equals(code));
    }

    private long available() {
        return tx.execute(status -> availability
                .countAvailableRooms(List.of(typeId), ARRIVAL.plusDays(1), ARRIVAL.plusDays(3))
                .get(typeId));
    }

    private Booking newBooking(RoomType type, User owner, boolean modern) {
        Booking booking = Booking.builder()
                .bookingCode("BK-" + UUID.randomUUID())
                .bookedBy(owner)
                .roomType(type)
                .checkInDate(ARRIVAL)
                .checkOutDate(ARRIVAL.plusDays(3))
                .status(BookingStatus.CONFIRMED)
                .totalAmount(2_800_000)
                .arrivalPolicyVersion(modern ? ArrivalPolicy.VERSION : null)
                .accessTokenHash(tokens.hash(TOKEN))
                .build();
        for (int i = 0; i < 3; i++)
            booking.addNight(BookingNight.builder()
                    .stayDate(ARRIVAL.plusDays(i))
                    .pricePerNight(i == 0 ? 800_000 : 900_000)
                    .build());
        return booking;
    }

    private Booking newUnifiedBooking(RoomType type, User owner) {
        Booking booking = Booking.builder()
                .bookingCode("BK-U-" + UUID.randomUUID())
                .roomType(type)
                .bookedBy(owner)
                .checkInDate(ARRIVAL)
                .checkOutDate(ARRIVAL.plusDays(3))
                .status(BookingStatus.PENDING)
                .totalAmount(2_800_000)
                .arrivalPolicyVersion(com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.VERSION)
                .accessTokenHash(tokens.hash(TOKEN))
                .expiredAt(CUTOFF.plusSeconds(3600))
                .build();
        for (int i = 0; i < 3; i++)
            booking.addNight(BookingNight.builder()
                    .stayDate(ARRIVAL.plusDays(i))
                    .pricePerNight(i == 0 ? 800_000 : 900_000)
                    .build());
        booking.acceptStandardTerms(CUTOFF.minusSeconds(86400 * 10));
        booking.confirmAutomatically(CUTOFF);
        return booking;
    }

    private Payment savePayment(Booking booking, BookingOrder order, long amount) {
        Payment payment = Payment.builder()
                .booking(booking)
                .order(order)
                .paymentCode(
                        "PAY-20261112000000-" + UUID.randomUUID().toString().replace("-", ""))
                .amount(amount)
                .status(PaymentStatus.SUCCESS)
                .paidAt(CUTOFF.minusSeconds(86400))
                .expiresAt(CUTOFF.plusSeconds(3600))
                .build();
        payment.addTransaction(PaymentTransaction.builder()
                .type(PaymentTransactionType.PAYMENT)
                .status(PaymentTransactionStatus.SUCCESS)
                .amount(amount)
                .receivedAt(CUTOFF.minusSeconds(86400))
                .build());
        return payments.saveAndFlush(payment);
    }

    private ArrivalHandlingRequest.NoShow request(ArrivalHandlingResponse preview, long fee) {
        return new ArrivalHandlingRequest.NoShow(
                preview.previewToken(),
                fee,
                "Khách xác nhận không đến; đã đối chiếu chính sách",
                "Điện thoại: khách xác nhận không đến");
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(staffId.toString(), null, "ROLE_" + role));
    }
}
