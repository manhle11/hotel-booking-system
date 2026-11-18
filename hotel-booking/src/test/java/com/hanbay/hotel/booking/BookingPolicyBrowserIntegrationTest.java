package com.hanbay.hotel.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import com.hanbay.hotel.authentication.repository.RoleRepository;
import com.hanbay.hotel.authentication.service.AccessTokenRevocationService;
import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Set;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "booking.expiration.cron=-",
            "app.cors.allowed-origins=http://127.0.0.1:5182",
            "debug=false",
            "spring.jpa.show-sql=false",
            "logging.level.root=WARN"
        })
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "booking.policy.e2e", matches = "true")
class BookingPolicyBrowserIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:pg17").withDatabaseName("hotelbooking_db");

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
    JwtService jwt;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AuthenticationRateLimitService limits;

    @MockitoBean
    AccessTokenRevocationService revocations;

    @Test
    void staffClosesBookingAndCollectsFeeWhileOwnerCanCreateQrWithoutReopeningStay() throws Exception {
        User guest = users.saveAndFlush(User.builder()
                .fullName("Khách QA")
                .email("qa-policy@example.test")
                .build());
        User receptionist = users.saveAndFlush(User.builder()
                .fullName("Lễ tân QA")
                .email("policy-staff@example.test")
                .roles(Set.of(roles.findByCode(RoleCode.RECEPTIONIST).orElseThrow()))
                .build());
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("../scripts/seed-booking-policy-qa.sql")));
        }
        String debt = jdbc.queryForObject(
                "SELECT booking_code FROM bookings WHERE booking_code LIKE 'QP%-DEBT'", String.class);
        String noShow = jdbc.queryForObject(
                "SELECT booking_code FROM bookings WHERE booking_code LIKE 'QP%-NOSHOW'", String.class);
        ProcessBuilder command = new ProcessBuilder("node", "scripts/booking-policy-live-e2e.mjs")
                .directory(Path.of("../client").toFile())
                .inheritIO();
        command.environment()
                .putAll(Map.of(
                        "POLICY_TEST_BACKEND", "http://127.0.0.1:" + port,
                        "POLICY_TEST_DEBT", debt,
                        "POLICY_TEST_NOSHOW", noShow,
                        "POLICY_TEST_GUEST_ID", guest.getId().toString(),
                        "POLICY_TEST_GUEST_TOKEN", jwt.generateAccessToken(guest),
                        "POLICY_TEST_STAFF_ID", receptionist.getId().toString(),
                        "POLICY_TEST_STAFF_TOKEN", jwt.generateAccessToken(receptionist)));
        Process process = command.start();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS))
                throw new AssertionError("Booking policy browser test timed out");
            assertThat(process.exitValue()).isZero();
            assertThat(jdbc.queryForObject(
                            "SELECT status::text FROM bookings WHERE booking_code=?", String.class, debt))
                    .isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT paid_amount FROM bookings WHERE booking_code=?", Long.class, debt))
                    .isEqualTo(800_000L);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE b.booking_code=? AND p.idempotency_key LIKE 'policy-fee-cash:%'",
                            Integer.class, debt))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                            "SELECT status::text FROM bookings WHERE booking_code=?", String.class, noShow))
                    .isEqualTo("NO_SHOW");
            assertThat(jdbc.queryForObject(
                            "SELECT sum(r.amount) FROM payment_refunds r JOIN bookings b ON b.id=r.source_booking_id WHERE b.booking_code=?",
                            Long.class,
                            noShow))
                    .isEqualTo(1_600_000L);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
