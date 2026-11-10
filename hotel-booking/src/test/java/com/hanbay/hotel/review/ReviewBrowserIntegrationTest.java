package com.hanbay.hotel.review;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import com.hanbay.hotel.authentication.repository.RoleRepository;
import com.hanbay.hotel.authentication.service.AccessTokenRevocationService;
import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.booking.service.BookingAccessTokenService;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "booking.expiration.cron=-", "app.cors.allowed-origins=http://127.0.0.1:5181", "debug=false",
            "spring.jpa.show-sql=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN"
        })
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "review.e2e", matches = "true")
class ReviewBrowserIntegrationTest {
    @Container
    static final org.testcontainers.postgresql.PostgreSQLContainer POSTGRES =
            new org.testcontainers.postgresql.PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    RoomTypeRepository roomTypes;

    @Autowired
    BookingOrderRepository orders;

    @Autowired
    BookingRepository bookings;

    @Autowired
    BookingAccessTokenService tokens;

    @Autowired
    JwtService jwt;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AuthenticationRateLimitService limits;

    @MockitoBean
    AccessTokenRevocationService revocations;

    @Test
    void onlySignedInOwnerCanReviewAndManagerCanReplyAndDelete() throws Exception {
        User manager = users.saveAndFlush(User.builder()
                .fullName("Quản lý kiểm thử")
                .email(UUID.randomUUID() + "@example.test")
                .roles(Set.of(roles.findByCode(RoleCode.MANAGER).orElseThrow()))
                .build());
        User customer = users.saveAndFlush(User.builder()
                .fullName("Trần Minh")
                .email("customer-" + UUID.randomUUID() + "@example.test")
                .build());
        RoomType type = roomTypes.saveAndFlush(RoomType.builder()
                .code("LIVE_" + UUID.randomUUID().toString().replace("-", ""))
                .name("Deluxe Twin")
                .description("Không gian yên tĩnh với hai giường đơn và tiện nghi hiện đại.")
                .basePrice(1000000)
                .maxGuests(2)
                .capacityAdults(2)
                .capacityChildren(0)
                .build());
        String guestToken = tokens.generate();
        BookingOrder owned = order(customer, type, tokens.generate());
        BookingOrder guest = order(null, type, guestToken);
        UUID guestBooking = bookings.findIdsByOrderId(guest.getId()).get(0);
        ProcessBuilder command = new ProcessBuilder("node", "scripts/review-live-e2e.mjs")
                .directory(Path.of("../client").toFile())
                .inheritIO();
        command.environment()
                .putAll(Map.of(
                        "REVIEW_TEST_BACKEND", "http://127.0.0.1:" + port,
                        "REVIEW_TEST_ORDER_ID", owned.getId().toString(),
                        "REVIEW_TEST_GUEST_ORDER", guest.getId().toString(),
                        "REVIEW_TEST_GUEST_BOOKING", guestBooking.toString(),
                        "REVIEW_TEST_GUEST_TOKEN", guestToken,
                        "REVIEW_TEST_TYPE", type.getId().toString(),
                        "REVIEW_TEST_CUSTOMER_ID", customer.getId().toString(),
                        "REVIEW_TEST_CUSTOMER_TOKEN", jwt.generateAccessToken(customer),
                        "REVIEW_TEST_MANAGER_TOKEN", jwt.generateAccessToken(manager)));
        Process process = command.start();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new AssertionError("Browser test timed out");
            org.assertj.core.api.Assertions.assertThat(process.exitValue()).isZero();
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                            "select count(*) from information_schema.tables where table_name='review_access_challenges'",
                            Integer.class))
                    .isZero();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private BookingOrder order(User customer, RoomType type, String accessToken) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        BookingOrder order = orders.saveAndFlush(BookingOrder.builder()
                .orderCode("ORD-" + UUID.randomUUID())
                .bookedBy(customer)
                .contactName("Trần Minh")
                .contactEmail(customer == null ? "not-real@example.test" : customer.getEmail())
                .checkInDate(today.minusDays(2))
                .checkOutDate(today)
                .status(BookingOrderStatus.CONFIRMED)
                .accessTokenHash(tokens.hash(accessToken))
                .idempotencyKey(UUID.randomUUID().toString())
                .requestHash(tokens.hash("live-test"))
                .expiresAt(now.minusSeconds(86400))
                .build());
        Booking booking = Booking.builder()
                .bookingCode("LIVE-" + UUID.randomUUID())
                .bookedBy(customer)
                .roomType(type)
                .checkInDate(today.minusDays(2))
                .checkOutDate(today)
                .status(BookingStatus.CHECKED_OUT)
                .checkedInAt(now.minusSeconds(172800))
                .checkedOutAt(now.minusSeconds(3600))
                .build();
        booking.assignOrder(order, 1);
        bookings.saveAndFlush(booking);
        return order;
    }
}
