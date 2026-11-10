package com.hanbay.hotel.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.exception.BookingNotFoundException;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.booking.service.BookingAccessTokenService;
import com.hanbay.hotel.review.dto.CreateReviewRequest;
import com.hanbay.hotel.review.dto.ManagedReviewFilter;
import com.hanbay.hotel.review.dto.ManagedReviewSummaryResponse;
import com.hanbay.hotel.review.exception.ReviewException;
import com.hanbay.hotel.review.repository.RoomTypeReviewRepository;
import com.hanbay.hotel.review.service.RoomTypeReviewService;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "booking.expiration.cron=-",
            "logging.level.root=WARN",
            "debug=false",
            "logging.level.org.hibernate.SQL=OFF",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
@Testcontainers(disabledWithoutDocker = true)
class RoomTypeReviewIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final String TOKEN = "review-order-token-at-least-32-characters";

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
    RoomTypeReviewService service;

    @Autowired
    RoomTypeReviewRepository reviews;

    @Autowired
    BookingRepository bookings;

    @Autowired
    BookingOrderRepository orders;

    @Autowired
    RoomTypeRepository types;

    @Autowired
    UserRepository users;

    @Autowired
    BookingAccessTokenService tokens;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @MockitoBean
    Clock clock;

    private User owner;
    private RoomType type;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        owner = users.saveAndFlush(User.builder()
                .fullName("Nguyễn Văn An")
                .email(UUID.randomUUID() + "@example.test")
                .build());
        type = roomType();
        signIn(owner, "ROLE_USER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkoutReviewIsPublishedThenManagerDeletionChangesListAverageAndEligibility() {
        Booking one = booking(type, null);
        Booking two = booking(type, null);
        var first = service.create(one.getId(), new CreateReviewRequest(1, "  Cần cải thiện vệ sinh  "));
        service.create(two.getId(), new CreateReviewRequest(5, null));
        assertThat(service.summary(type.getId()).averageRating()).isEqualTo(3.0);
        assertThat(service.publicReviews(type.getId(), 1, 0, 6).totalElements()).isEqualTo(1);
        assertThat(first.authorName()).isEqualTo("Nguyễn ***");
        signIn(owner, "ROLE_MANAGER");
        assertThat(service.manage(type.getId(), null, false, 0, 20).totalElements())
                .isEqualTo(2);
        service.delete(first.id(), "Spam");
        assertThat(service.summary(type.getId()).averageRating()).isEqualTo(5.0);
        assertThat(service.summary(type.getId()).totalReviews()).isEqualTo(1);
        assertThat(service.publicReviews(type.getId(), 1, 0, 6).totalElements()).isZero();
        assertThat(service.manage(type.getId(), null, false, 0, 20).totalElements())
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select deleted_by from room_type_reviews where id = ?", UUID.class, first.id()))
                .isEqualTo(owner.getId());
        assertThat(service.eligibility(one.getId()).reasonCode()).isEqualTo("REVIEW_REMOVED");
        assertThatThrownBy(() -> service.create(one.getId(), new CreateReviewRequest(5, "Gửi lại")))
                .isInstanceOf(ReviewException.class)
                .hasMessageContaining("xóa");
    }

    @Test
    void oneReviewPerOrderAndTypeButOtherTypesAndLaterStaysRemainEligible() {
        BookingOrder order = order();
        Booking first = booking(type, order);
        Booking duplicate = booking(type, order);
        RoomType suite = roomType();
        Booking other = booking(suite, order);
        service.create(first.getId(), new CreateReviewRequest(4, "Tốt"));
        assertThatThrownBy(() -> service.create(duplicate.getId(), new CreateReviewRequest(5, null)))
                .isInstanceOf(ReviewException.class);
        assertThat(service.eligibility(other.getId()).canReview()).isTrue();
        service.create(other.getId(), new CreateReviewRequest(5, null));
        service.create(booking(type, order()).getId(), new CreateReviewRequest(3, null));
        assertThat(service.summary(type.getId()).totalReviews()).isEqualTo(2);
    }

    @Test
    void concurrentRequestsForTwoRoomsOfSameTypeCreateOnlyOneReview() throws Exception {
        BookingOrder order = order();
        Booking first = booking(type, order);
        Booking second = booking(type, order);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (UUID bookingId : List.of(first.getId(), second.getId())) {
                results.add(executor.submit(() -> {
                    start.await();
                    signIn(owner, "ROLE_USER");
                    try {
                        service.create(bookingId, new CreateReviewRequest(5, "Đồng thời"));
                        return "created";
                    } catch (ReviewException exception) {
                        return exception.getErrorCode();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (var result : results) outcomes.add(result.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("created", "REVIEW_ALREADY_EXISTS");
            assertThat(service.summary(type.getId()).totalReviews()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deletionOfLastReviewReturnsEmptySummaryAndDatabaseKeepsUniqueness() {
        Booking booking = booking(type, null);
        var review = service.create(booking.getId(), new CreateReviewRequest(2, null));
        signIn(owner, "ROLE_MANAGER");
        service.delete(review.id(), "Theo yêu cầu");
        assertThat(service.summary(type.getId()).averageRating()).isNull();
        assertThat(service.summary(type.getId()).totalReviews()).isZero();
        assertThatThrownBy(() -> jdbc.update(
                        "insert into room_type_reviews(id, booking_id, room_type_id, reviewer_name, rating) values (?, ?, ?, 'Khách', 5)",
                        UUID.randomUUID(),
                        booking.getId(),
                        type.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void managerSeesStayDetailsReplyAndDeletionAuditWithoutLeakingDetailsToPublic() {
        Booking stay = booking(type, null);
        var review = service.create(stay.getId(), new CreateReviewRequest(2, "Cần hỗ trợ"));
        signIn(owner, "ROLE_MANAGER");
        var row = service.manage(type.getId(), null, false, 0, 20).content().get(0);
        assertThat(row.stays()).hasSize(1);
        assertThat(row.bookingCode()).isEqualTo(stay.getBookingCode());
        assertThat(row.guestName()).isEqualTo(owner.getFullName());
        service.reply(review.id(), "  Chúng tôi đã khắc phục.  ");
        assertThat(service.publicReviews(type.getId(), null, 0, 6)
                        .content()
                        .get(0)
                        .hotelReply())
                .isEqualTo("Chúng tôi đã khắc phục.");
        service.removeReply(review.id());
        assertThat(service.publicReviews(type.getId(), null, 0, 6)
                        .content()
                        .get(0)
                        .hotelReply())
                .isNull();
        service.reply(review.id(), "Cảm ơn bạn.");
        service.delete(review.id(), "Nội dung vi phạm");
        service.delete(review.id(), "Lý do lần sau");
        var removed = service.manage(type.getId(), null, true, 0, 20).content().get(0);
        assertThat(removed.deleteReason()).isEqualTo("Nội dung vi phạm");
        assertThat(removed.deletedByName()).isEqualTo(owner.getFullName());
        assertThat(removed.review().hotelReply()).isEqualTo("Cảm ơn bạn.");
        assertThatThrownBy(() -> service.reply(review.id(), "Không hợp lệ")).isInstanceOf(ReviewException.class);
    }

    @Test
    void publicAndManagerPaginationReturnAnExistingPageAfterDeletion() {
        for (int i = 0; i < 7; i++)
            service.create(booking(type, null).getId(), new CreateReviewRequest(5, "Review " + i));
        var last = service.publicReviews(type.getId(), null, 1, 6).content().get(0);
        signIn(owner, "ROLE_MANAGER");
        service.delete(last.id(), "Yêu cầu của khách");
        var page = service.publicReviews(type.getId(), null, 1, 6);
        assertThat(page.page()).isZero();
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.content()).hasSize(6);
        var managed = service.manage(type.getId(), null, false, 20, 20);
        assertThat(managed.page()).isZero();
        assertThat(managed.content()).hasSize(6);
    }

    @Test
    void dateAndReplyFiltersAndSummaryUseAllMatchingReviewsAndHotelLocalDayBoundaries() {
        var before = service.create(booking(type, null).getId(), new CreateReviewRequest(4, "Before"));
        var start = service.create(booking(type, null).getId(), new CreateReviewRequest(2, "Start"));
        var midday = service.create(booking(type, null).getId(), new CreateReviewRequest(5, "Midday"));
        var end = service.create(booking(type, null).getId(), new CreateReviewRequest(1, "End"));
        var after = service.create(booking(type, null).getId(), new CreateReviewRequest(3, "After"));
        var deleted = service.create(booking(type, null).getId(), new CreateReviewRequest(2, "Deleted"));
        setReviewTime(before.id(), "2026-11-08T16:59:59Z");
        setReviewTime(start.id(), "2026-11-08T17:00:00Z");
        setReviewTime(midday.id(), "2026-11-09T05:00:00Z");
        setReviewTime(end.id(), "2026-11-09T16:59:59.999999Z");
        setReviewTime(after.id(), "2026-11-09T17:00:00Z");
        setReviewTime(deleted.id(), "2026-11-09T05:00:00Z");
        signIn(owner, "ROLE_MANAGER");
        service.reply(midday.id(), "Cảm ơn bạn");
        service.reply(deleted.id(), "Đã xử lý");
        service.delete(deleted.id(), "Theo yêu cầu");

        var day = LocalDate.of(2026, 11, 9);
        var filter = new ManagedReviewFilter(type.getId(), null, false, null, null, day, day);
        assertThat(service.manage(filter, 0, 1).totalElements()).isEqualTo(3);
        assertThat(service.managedSummary(filter)).isEqualTo(new ManagedReviewSummaryResponse(3, 2, 1, 2));
        var replied = new ManagedReviewFilter(type.getId(), null, false, true, null, day, day);
        assertThat(service.manage(replied, 0, 20).content())
                .extracting(row -> row.review().id())
                .containsExactly(midday.id());
        assertThat(service.managedSummary(replied)).isEqualTo(new ManagedReviewSummaryResponse(1, 0, 1, 0));
        var unreplied = new ManagedReviewFilter(type.getId(), null, false, false, null, day, day);
        assertThat(service.managedSummary(unreplied)).isEqualTo(new ManagedReviewSummaryResponse(2, 2, 0, 2));
        assertThat(service.manage(unreplied, 0, 20).totalElements()).isEqualTo(2);
        var history = new ManagedReviewFilter(type.getId(), null, true, true, null, day, day);
        assertThat(service.managedSummary(history)).isEqualTo(new ManagedReviewSummaryResponse(1, 0, 1, 1));
        assertThat(service.manage(history, 0, 20).content())
                .extracting(row -> row.review().id())
                .containsExactly(deleted.id());
        var empty = new ManagedReviewFilter(type.getId(), 4, false, null, null, day, day);
        assertThat(service.managedSummary(empty)).isEqualTo(new ManagedReviewSummaryResponse(0, 0, 0, 0));
        assertThat(service.manage(empty, 0, 20).content()).isEmpty();
    }

    @Test
    void searchesNamesAndAnyBookingInTheReviewedRoomTypeWithoutWildcardExpansionOrDuplicates() {
        BookingOrder order = order();
        Booking first = booking(type, order);
        Booking second = booking(type, order);
        var grouped = service.create(first.getId(), new CreateReviewRequest(2, "Grouped"));
        var single = service.create(booking(type, null).getId(), new CreateReviewRequest(5, "Single"));
        jdbc.update("update room_type_reviews set reviewer_name = ? where id = ?", "Nguyễn Minh An", grouped.id());
        jdbc.update("update room_type_reviews set reviewer_name = ? where id = ?", "Khách %_đặc biệt", single.id());
        signIn(owner, "ROLE_MANAGER");
        for (String search : List.of(
                "nGUYỄN mINH",
                order.getOrderCode().toLowerCase(Locale.ROOT),
                first.getBookingCode(),
                second.getBookingCode())) {
            var filter = new ManagedReviewFilter(type.getId(), null, false, null, search, null, null);
            var result = service.manage(filter, 0, 20);
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).review().id()).isEqualTo(grouped.id());
            assertThat(result.content().get(0).stays())
                    .extracting(stay -> stay.bookingCode())
                    .containsExactlyInAnyOrder(first.getBookingCode(), second.getBookingCode());
            assertThat(service.managedSummary(filter)).isEqualTo(new ManagedReviewSummaryResponse(1, 1, 0, 1));
        }
        var literal = new ManagedReviewFilter(type.getId(), null, false, null, "%_", null, null);
        assertThat(service.manage(literal, 0, 20).content())
                .extracting(row -> row.review().id())
                .containsExactly(single.id());
        assertThat(service.managedSummary(literal)).isEqualTo(new ManagedReviewSummaryResponse(1, 1, 0, 0));
    }

    @Test
    void managerSqlCountStaysConstantWhenPageGrowsFromOneToTwentyReviews() {
        for (int i = 0; i < 25; i++) {
            service.create(booking(type, order()).getId(), new CreateReviewRequest(i % 5 + 1, "Review " + i));
        }
        signIn(owner, "ROLE_MANAGER");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(service.manage(type.getId(), null, false, 0, 1).content()).hasSize(1);
        long singlePageStatements = statistics.getPrepareStatementCount();
        statistics.clear();
        var page = service.manage(type.getId(), null, false, 0, 20);
        long fullPageStatements = statistics.getPrepareStatementCount();
        assertThat(page.content()).hasSize(20).allSatisfy(row -> assertThat(row.stays())
                .hasSize(1));
        assertThat(fullPageStatements).isEqualTo(singlePageStatements).isEqualTo(3);
        assertThat(service.managedSummary(new ManagedReviewFilter(type.getId(), null, false, null, null, null, null)))
                .isEqualTo(new ManagedReviewSummaryResponse(25, 25, 0, 10));
    }

    private void setReviewTime(UUID id, String timestamp) {
        jdbc.update(
                "update room_type_reviews set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.parse(timestamp)),
                id);
    }

    private BookingOrder guestOrder() {
        return orders.saveAndFlush(BookingOrder.builder()
                .orderCode("ORD-" + UUID.randomUUID())
                .contactName("Trần Minh")
                .contactEmail("guest-review@example.test")
                .checkInDate(LocalDate.of(2026, 11, 7))
                .checkOutDate(LocalDate.of(2026, 11, 9))
                .status(BookingOrderStatus.CONFIRMED)
                .accessTokenHash(tokens.hash(TOKEN))
                .idempotencyKey(UUID.randomUUID().toString())
                .requestHash(tokens.hash("test"))
                .expiresAt(NOW)
                .build());
    }

    private void signIn(User user, String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(user.getId().toString(), "unused", role));
    }

    private RoomType roomType() {
        return types.saveAndFlush(RoomType.builder()
                .code("REVIEW_" + UUID.randomUUID().toString().replace("-", ""))
                .name("Deluxe Twin")
                .basePrice(1000000)
                .maxGuests(2)
                .capacityAdults(2)
                .capacityChildren(0)
                .build());
    }

    private BookingOrder order() {
        return orders.saveAndFlush(BookingOrder.builder()
                .orderCode("ORD-REVIEW-" + UUID.randomUUID())
                .bookedBy(owner)
                .contactName("Nguyễn Văn An")
                .contactEmail("review@example.test")
                .checkInDate(LocalDate.of(2026, 11, 7))
                .checkOutDate(LocalDate.of(2026, 11, 9))
                .status(BookingOrderStatus.CONFIRMED)
                .accessTokenHash(tokens.hash(TOKEN))
                .idempotencyKey(UUID.randomUUID().toString())
                .requestHash(tokens.hash("test"))
                .expiresAt(NOW)
                .build());
    }

    private Booking booking(RoomType roomType, BookingOrder order) {
        Booking booking = Booking.builder()
                .bookingCode("REVIEW-" + UUID.randomUUID())
                .bookedBy(owner)
                .roomType(roomType)
                .checkInDate(LocalDate.of(2026, 11, 7))
                .checkOutDate(LocalDate.of(2026, 11, 9))
                .status(BookingStatus.CHECKED_OUT)
                .checkedInAt(NOW.minus(Duration.ofDays(2)))
                .checkedOutAt(NOW.minus(Duration.ofHours(1)))
                .build();
        if (order != null)
            booking.assignOrder(order, bookings.findIdsByOrderId(order.getId()).size() + 1);
        return bookings.saveAndFlush(booking);
    }

    @Test
    void guestBookingRemainsIneligibleAfterLoginEvenWithMatchingEmail() {
        BookingOrder order = guestOrder();
        Booking stay = booking(type, order);
        assertThatThrownBy(() -> service.eligibility(stay.getId())).isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.create(stay.getId(), new CreateReviewRequest(5, null)))
                .isInstanceOf(BookingNotFoundException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.create(stay.getId(), new CreateReviewRequest(5, null)))
                .isInstanceOf(ReviewException.class);
        assertThat(service.summary(type.getId()).totalReviews()).isZero();
    }
}
