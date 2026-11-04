package com.hanbay.hotel.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.request.*;
import com.hanbay.hotel.booking.dto.response.BookingOrderResponse;
import com.hanbay.hotel.booking.exception.*;
import com.hanbay.hotel.booking.repository.BookingOrderRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.*;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.payment.dto.SePayTransferType;
import com.hanbay.hotel.payment.dto.request.*;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.payment.service.PaymentService;
import com.hanbay.hotel.payment.service.PaymentWebhookService;
import com.hanbay.hotel.pricing.dto.result.PricingResult;
import com.hanbay.hotel.pricing.dto.result.RoomPricingResult;
import com.hanbay.hotel.pricing.service.PricingService;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties = {
            "booking.expiration.cron=-",
            "payment.sepay.webhook-api-key=order-test-secret",
            "payment.sepay.account-number=123456789",
            "payment.sepay.bank-code=MB",
            "payment.sepay.account-name=TEST HOTEL",
            "payment.sepay.qr-base-url=https://example.test/qr",
            "debug=false",
            "logging.level.root=WARN",
            "logging.level.org.springframework=WARN",
            "logging.level.com.hanbay=WARN"
        })
class BookingOrderIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-04T06:00:00Z");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 10);
    private static final AtomicLong EVENT_IDS = new AtomicLong(8_000_000);
    private static final String TOKEN = "test-order-token-with-at-least-32-characters";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("multi_room_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.docker.compose.enabled", () -> "false");
    }

    @Autowired
    BookingOrderService service;

    @Autowired
    BookingOrderRepository orders;

    @Autowired
    BookingRepository bookings;

    @Autowired
    PaymentRepository payments;

    @Autowired
    RoomTypeRepository roomTypes;

    @Autowired
    RoomRepository rooms;

    @Autowired
    UserRepository users;

    @Autowired
    PaymentWebhookService webhooks;

    @Autowired
    PaymentService paymentService;

    @Autowired
    BookingService bookingService;

    @Autowired
    CheckInService checkInService;

    @Autowired
    TransactionTemplate transaction;

    @MockitoBean
    Clock clock;

    @MockitoBean
    PricingService pricing;

    private UUID deluxe;
    private UUID suite;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(pricing.calculatePrice(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            List<BookingNight> nights = booking.getCheckInDate()
                    .datesUntil(booking.getCheckOutDate())
                    .map(date -> BookingNight.builder()
                            .stayDate(date)
                            .pricePerNight(1000)
                            .build())
                    .toList();
            long total = nights.size() * 1000L;
            return new PricingResult(new RoomPricingResult(nights, total, 0, total), 0, total);
        });
        transaction.executeWithoutResult(status -> {
            deluxe = seedRoomType("Deluxe", 2);
            suite = seedRoomType("Suite", 1);
        });
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsMixedRoomOrderWithOneDeadlineAndServerCalculatedTotals() {
        var created = create(deluxe, deluxe, suite);
        assertThat(created.rooms()).hasSize(3);
        assertThat(created.totalAmount()).isEqualTo(6000);
        assertThat(created.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        transaction.executeWithoutResult(status -> {
            var order = orders.findById(created.orderId()).orElseThrow();
            assertThat(order.getBookings()).allSatisfy(booking -> {
                assertThat(booking.getExpiredAt()).isEqualTo(order.getExpiresAt());
                assertThat(booking.getNights()).hasSize(2);
                assertThat(booking.getOrderRoomNumber()).isPositive();
            });
        });
    }

    @Test
    void retriesCreationWithoutTakingInventoryTwiceAndRejectsChangedPayloadOrToken() {
        String key = UUID.randomUUID().toString();
        var request = request(deluxe, suite);
        var first = service.create(request, key, TOKEN);
        var retried = service.create(request, key, TOKEN);
        assertThat(retried.orderId()).isEqualTo(first.orderId());
        assertThatThrownBy(() -> service.create(request(deluxe), key, TOKEN))
                .isInstanceOf(BookingConflictException.class);
        assertThatThrownBy(() -> service.create(request, key, "wrong-token"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void insufficientAggregateStockCreatesNoOrderAndNoRoomBookings() {
        long orderCount = orders.count();
        long bookingCount = bookings.count();
        assertThatThrownBy(() -> create(deluxe, deluxe, deluxe)).isInstanceOf(RoomNotAvailableException.class);
        assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(bookings.count()).isEqualTo(bookingCount);
    }

    @Test
    void invalidSecondRoomRollsBackTheAlreadyCreatedFirstRoom() {
        long orderCount = orders.count();
        long bookingCount = bookings.count();
        var request = new CreateBookingOrderRequest(
                CHECK_IN,
                CHECK_IN.plusDays(2),
                guest(),
                List.of(
                        new CreateBookingOrderRequest.RoomSelection(deluxe, 1, 0, List.of()),
                        new CreateBookingOrderRequest.RoomSelection(suite, 20, 0, List.of())));
        assertThatThrownBy(() -> service.create(request, UUID.randomUUID().toString(), TOKEN))
                .isInstanceOf(CapacityExceededException.class);
        assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(bookings.count()).isEqualTo(bookingCount);
    }

    @Test
    void concurrentOrdersCannotOversellTheLastRoom() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> book = () -> {
            start.await();
            try {
                create(suite);
                return true;
            } catch (RoomNotAvailableException exception) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(book);
            Future<Boolean> second = executor.submit(book);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onePaymentHasPerRoomAllocationsAndIsReusedOnDoubleClick() {
        var order = create(deluxe, suite);
        var first = service.createPayment(order.orderId(), TOKEN);
        var second = service.createPayment(order.orderId(), TOKEN);
        assertThat(first.payment().paymentId()).isEqualTo(second.payment().paymentId());
        assertThat(first.payment().amount()).isEqualTo(4000);
        assertThat(first.payment().bookingCode()).isEqualTo(order.orderCode());
        transaction.executeWithoutResult(status -> {
            var payment = payments.findById(first.payment().paymentId()).orElseThrow();
            assertThat(payment.getAllocations()).hasSize(2);
            assertThat(payment.getAllocations())
                    .extracting(PaymentAllocation::getAmount)
                    .containsExactly(2000L, 2000L);
        });
    }

    @Test
    void splitTransfersConfirmEveryRoomOnlyAfterFullPaymentAndDeduplicateWebhook() {
        var order = service.createPayment(create(deluxe, suite).orderId(), TOKEN);
        var first = transfer(order, 1500);
        webhook(first);
        webhook(first);
        var partial = service.get(order.orderId(), TOKEN);
        assertThat(partial.status()).isEqualTo(BookingOrderStatus.PENDING);
        assertThat(partial.paidAmount()).isEqualTo(1500);
        assertThat(partial.rooms()).allSatisfy(room -> assertThat(room.status()).isEqualTo(BookingStatus.PENDING));
        webhook(transfer(order, 2500));
        var completed = service.get(order.orderId(), TOKEN);
        assertThat(completed.status()).isEqualTo(BookingOrderStatus.CONFIRMED);
        assertThat(completed.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(completed.rooms()).allSatisfy(room -> {
            assertThat(room.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(room.paidAmount()).isEqualTo(room.totalAmount());
        });
    }

    @Test
    void overpaymentDoesNotInflateRoomBalancesAndExtraTransferIsRefunded() {
        var order = service.createPayment(create(deluxe, suite).orderId(), TOKEN);
        webhook(transfer(order, 4500));
        var completed = service.get(order.orderId(), TOKEN);
        assertThat(completed.paidAmount()).isEqualTo(4000);
        assertThat(completed.refundAmount()).isEqualTo(500);
        assertThat(completed.refundInformationRequired()).isTrue();
        var information = new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Test bank", "Guest", "12345678");
        var supplied = service.supplyRefundInformation(order.orderId(), TOKEN, information);
        assertThat(supplied.refundInformationRequired()).isFalse();
        assertThatThrownBy(() -> service.supplyRefundInformation(order.orderId(), "wrong-token", information))
                .isInstanceOf(BookingNotFoundException.class);
        webhook(transfer(order, 600));
        var duplicatePayment = service.get(order.orderId(), TOKEN);
        assertThat(duplicatePayment.paidAmount()).isEqualTo(4000);
        assertThat(duplicatePayment.refundAmount()).isEqualTo(1100);
    }

    @Test
    void expiryReleasesAllRoomsAndRefundsPartialPaymentAndLaterTransfer() {
        var order = service.createPayment(create(deluxe, suite).orderId(), TOKEN);
        webhook(transfer(order, 1000));
        when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        var expired = service.get(order.orderId(), TOKEN);
        assertThat(expired.status()).isEqualTo(BookingOrderStatus.EXPIRED);
        assertThat(expired.rooms()).allSatisfy(room -> assertThat(room.status()).isEqualTo(BookingStatus.EXPIRED));
        assertThat(expired.refundAmount()).isEqualTo(1000);
        webhook(transfer(order, 3000));
        var late = service.get(order.orderId(), TOKEN);
        assertThat(late.status()).isEqualTo(BookingOrderStatus.EXPIRED);
        assertThat(late.refundAmount()).isEqualTo(4000);
        assertThat(create(deluxe, suite).status()).isEqualTo(BookingOrderStatus.PENDING);
    }

    @Test
    void paymentWaitingForLocksCannotConfirmRoomsAfterTheHoldDeadline() {
        var order = service.createPayment(create(deluxe, suite).orderId(), TOKEN);
        var transfer = transfer(order, 4000);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(901));
        webhook(transfer);
        var expired = service.get(order.orderId(), TOKEN);
        assertThat(expired.status()).isEqualTo(BookingOrderStatus.EXPIRED);
        assertThat(expired.paidAmount()).isZero();
        assertThat(expired.refundAmount()).isEqualTo(4000);
        assertThat(expired.rooms()).allSatisfy(room -> assertThat(room.status()).isEqualTo(BookingStatus.EXPIRED));
    }

    @Test
    void anonymousCancellationIsAtomicAndRepeatableAndReleasesInventory() {
        var order = create(deluxe, deluxe, suite);
        var cancelled = service.cancel(order.orderId(), TOKEN, new CancelBookingRequest("Đổi kế hoạch"));
        assertThat(cancelled.status()).isEqualTo(BookingOrderStatus.CANCELLED);
        assertThat(cancelled.rooms())
                .allSatisfy(room -> assertThat(room.status()).isEqualTo(BookingStatus.CANCELLED));
        assertThat(service.cancel(order.orderId(), TOKEN, new CancelBookingRequest("Lặp lại"))
                        .status())
                .isEqualTo(BookingOrderStatus.CANCELLED);
        assertThat(create(deluxe, deluxe, suite).rooms()).hasSize(3);
    }

    @Test
    void confirmedCancellationUsesCombinedRefundAndOneRefundRequest() {
        var order = service.createPayment(create(deluxe, suite).orderId(), TOKEN);
        webhook(transfer(order, 4000));
        var eligibility = service.cancellationEligibility(order.orderId(), TOKEN);
        assertThat(eligibility.refundAmount()).isEqualTo(4000);
        assertThatThrownBy(() -> service.cancel(order.orderId(), TOKEN, new CancelBookingRequest("Hủy")))
                .isInstanceOf(BookingValidationException.class);
        var bank = new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Test bank", "TEST GUEST", "12345678");
        var cancelled = service.cancel(order.orderId(), TOKEN, new CancelBookingRequest("Đổi kế hoạch", bank));
        assertThat(cancelled.refundAmount()).isEqualTo(4000);
        assertThat(cancelled.status()).isEqualTo(BookingOrderStatus.CANCELLED);
        transaction.executeWithoutResult(
                status -> assertThat(payments.findById(order.payment().paymentId())
                                .orElseThrow()
                                .getRefunds())
                        .singleElement()
                        .satisfies(refund -> {
                            assertThat(refund.getBankName()).isEqualTo("Test bank");
                            assertThat(refund.getAmount()).isEqualByComparingTo("4000");
                        }));
    }

    @Test
    void partialPaymentCanBeCancelledAndRefundedWithoutLosingTheMoney() {
        var order = service.createPayment(create(deluxe, suite).orderId(), TOKEN);
        webhook(transfer(order, 1250));
        assertThat(service.cancellationEligibility(order.orderId(), TOKEN).refundAmount())
                .isEqualTo(1250);
        var bank = new RefundInformationRequest(RefundMethod.BANK_TRANSFER, "Test", "Guest", "12345678");
        assertThat(service.cancel(order.orderId(), TOKEN, new CancelBookingRequest("Hủy", bank))
                        .refundAmount())
                .isEqualTo(1250);
    }

    @Test
    void privateDetailsAndPaymentCannotBeAccessedWithWrongToken() {
        var order = create(deluxe, suite);
        assertThatThrownBy(() -> service.get(order.orderId(), "wrong-token"))
                .isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.createPayment(order.orderId(), null))
                .isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(order.orderId(), "wrong-token", new CancelBookingRequest("Hủy")))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void roomsCannotBePaidOrEditedIndividuallyThroughLegacyEndpoints() {
        var order = create(deluxe, suite);
        var room = order.rooms().get(0);
        assertThatThrownBy(() -> paymentService.createPaymentRequest(new CreatePaymentRequest(room.bookingId()), TOKEN))
                .isInstanceOf(com.hanbay.hotel.payment.exception.PaymentBookingNotPayableException.class);
        var update = new CreatePendingBookingRequest(deluxe, CHECK_IN, CHECK_IN.plusDays(2), 1, 0, guest(), List.of());
        assertThatThrownBy(() -> bookingService.updatePendingBooking(room.bookingId(), update, TOKEN))
                .isInstanceOf(BookingValidationException.class);
    }

    @Test
    void accountHistoryGroupsRoomsAndRequiresTheCorrectOwner() {
        UUID owner = transaction.execute(
                status -> users.saveAndFlush(User.builder().build()).getId());
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(owner.toString(), null, "ROLE_USER"));
        var order = create(deluxe, suite);
        assertThat(service.history(0, 10).content())
                .extracting(BookingOrderResponse::orderId)
                .contains(order.orderId());
        assertThat(bookingService.getCurrentUserBookingHistory(0, 10, null).content())
                .isEmpty();
        assertThat(service.get(order.orderId(), null).rooms()).hasSize(2);
        UUID other = transaction.execute(
                status -> users.saveAndFlush(User.builder().build()).getId());
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(other.toString(), null, "ROLE_USER"));
        assertThatThrownBy(() -> service.get(order.orderId(), null)).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void receptionistCanCheckInOneRoomWithoutChangingTheOtherAndWholeOrderCancellationIsDenied() {
        var order = service.createPayment(create(deluxe, deluxe).orderId(), TOKEN);
        webhook(transfer(order, 4000));
        when(clock.instant())
                .thenReturn(CHECK_IN.atTime(15, 0)
                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toInstant());
        UUID receptionist = transaction.execute(
                status -> users.saveAndFlush(User.builder().build()).getId());
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(receptionist.toString(), null, "ROLE_RECEPTIONIST"));
        var room = order.rooms().get(0);
        var available = checkInService.getAvailableRooms(room.bookingCode());
        checkInService.checkIn(
                room.bookingCode(), new CheckInGuestRequest(available.get(0).roomId(), null));
        var updated = service.get(order.orderId(), TOKEN);
        assertThat(updated.rooms())
                .extracting(BookingOrderResponse.Room::status)
                .containsExactly(BookingStatus.CHECKED_IN, BookingStatus.CONFIRMED);
        assertThat(service.cancellationEligibility(order.orderId(), TOKEN).canCancel())
                .isFalse();
        assertThatThrownBy(() -> service.cancel(order.orderId(), TOKEN, new CancelBookingRequest("Hủy")))
                .isInstanceOf(BookingCancellationNotAllowedException.class);
    }

    private BookingOrderResponse create(UUID... types) {
        return service.create(request(types), UUID.randomUUID().toString(), TOKEN);
    }

    private CreateBookingOrderRequest request(UUID... types) {
        return new CreateBookingOrderRequest(
                CHECK_IN,
                CHECK_IN.plusDays(2),
                guest(),
                Arrays.stream(types)
                        .map(id -> new CreateBookingOrderRequest.RoomSelection(id, 1, 0, List.of()))
                        .toList());
    }

    private GuestInfo guest() {
        return new GuestInfo("Test guest", "order@example.test", "0912345678", "TEST-IDENTITY");
    }

    private UUID seedRoomType(String name, int count) {
        RoomType type = roomTypes.saveAndFlush(RoomType.builder()
                .code("ORDER-" + UUID.randomUUID())
                .name(name)
                .basePrice(1000)
                .capacityAdults(2)
                .capacityChildren(1)
                .maxGuests(3)
                .build());
        for (int i = 0; i < count; i++)
            rooms.saveAndFlush(Room.builder()
                    .roomType(type)
                    .roomNumber("ORDER-" + UUID.randomUUID().toString().substring(0, 20))
                    .status(RoomStatus.AVAILABLE)
                    .build());
        return type.getId();
    }

    private SePayWebhookRequest transfer(BookingOrderResponse order, long amount) {
        long id = EVENT_IDS.incrementAndGet();
        return new SePayWebhookRequest(
                id,
                "Test",
                LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh")),
                "123456789",
                null,
                order.payment().transactionRef(),
                order.payment().transferContent(),
                SePayTransferType.IN,
                amount,
                0,
                "Test order payment",
                "TEST-" + id);
    }

    private void webhook(SePayWebhookRequest request) {
        webhooks.handleSePayWebhook("Apikey order-test-secret", request);
    }
}
