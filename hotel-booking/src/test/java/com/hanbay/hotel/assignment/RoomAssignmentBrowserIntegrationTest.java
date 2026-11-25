package com.hanbay.hotel.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import com.hanbay.hotel.authentication.repository.RoleRepository;
import com.hanbay.hotel.authentication.service.AccessTokenRevocationService;
import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingGuest;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.repository.*;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "booking.expiration.cron=-", "app.cors.allowed-origins=http://127.0.0.1:5183", "debug=false",
            "spring.jpa.show-sql=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN"
        })
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "assignment.e2e", matches = "true")
class RoomAssignmentBrowserIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    RoomTypeRepository types;

    @Autowired
    RoomRepository rooms;

    @Autowired
    BookingRepository bookings;

    @Autowired
    RoomAssignmentService assignments;

    @Autowired
    JwtService jwt;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    AuthenticationRateLimitService limits;

    @MockitoBean
    AccessTokenRevocationService revocations;

    @Test
    void receptionistCanReviewApplyAndLockWhileManagerCanUnlockWithAReason() throws Exception {
        User staff = users.saveAndFlush(User.builder()
                .fullName("Lễ tân kiểm thử")
                .email(UUID.randomUUID() + "@example.test")
                .roles(Set.of(roles.findByCode(RoleCode.RECEPTIONIST).orElseThrow()))
                .build());
        User manager = users.saveAndFlush(User.builder()
                .fullName("Quản lý kiểm thử")
                .email(UUID.randomUUID() + "@example.test")
                .roles(Set.of(roles.findByCode(RoleCode.MANAGER).orElseThrow()))
                .build());
        RoomType type = types.saveAndFlush(RoomType.builder()
                .code("E2E_ASSIGN_" + UUID.randomUUID())
                .name("Deluxe kiểm thử gán phòng")
                .basePrice(800000)
                .capacityAdults(2)
                .capacityChildren(0)
                .maxGuests(2)
                .build());
        Room one = rooms.saveAndFlush(Room.builder()
                .roomType(type)
                .roomNumber("E2E-201")
                .floor(2)
                .status(RoomStatus.AVAILABLE)
                .build());
        rooms.saveAndFlush(Room.builder()
                .roomType(type)
                .roomNumber("E2E-202")
                .floor(2)
                .status(RoomStatus.DIRTY)
                .build());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Booking a = booking(type, "E2E-A", "Nguyễn Minh Anh", today.plusDays(3), today.plusDays(5));
        Booking b = booking(type, "E2E-B", "Trần Hải Nam", today.plusDays(5), today.plusDays(7));
        booking(type, "E2E-C", "Lê Thu Hà", today.plusDays(4), today.plusDays(6));
        String version =
                assignments.calendar(today, today.plusDays(60), type.getId()).version();
        assignments.assign(
                a.getId(), one.getId(), true, version, "Giữ phòng theo yêu cầu khách", today, today.plusDays(60));
        var httpRequest = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port + "/api/v1/front-desk/room-assignments/plans"))
                .header("Authorization", "Bearer " + jwt.generateAccessToken(staff))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("roomTypeId", type.getId(), "from", today, "to", today.plusDays(60)))))
                .build();
        var response = java.net.http.HttpClient.newHttpClient()
                .send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("Direct HTTP planning response: %s", response.body())
                .isEqualTo(200);
        Process process = new ProcessBuilder("node", "scripts/room-assignment-live-e2e.mjs")
                .directory(Path.of("../client").toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("backend", "http://127.0.0.1:" + port);
        input.put("typeId", type.getId());
        input.put("typeName", type.getName());
        input.put("staffToken", jwt.generateAccessToken(staff));
        input.put("managerToken", jwt.generateAccessToken(manager));
        input.put("bookingA", a.getId());
        input.put("bookingB", b.getId());
        input.put("today", today);
        input.put("firstRoom", one.getId());
        try (var stdin = process.getOutputStream()) {
            stdin.write(json.writeValueAsBytes(input));
        }
        try {
            assertThat(process.waitFor(150, TimeUnit.SECONDS))
                    .as("Browser workflow timed out")
                    .isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(bookings.findById(b.getId()).orElseThrow().getAssignedRoom())
                    .isNull();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private Booking booking(RoomType type, String code, String guestName, LocalDate from, LocalDate to) {
        Booking booking = Booking.builder()
                .bookingCode(code)
                .roomType(type)
                .checkInDate(from)
                .checkOutDate(to)
                .adultCount(1)
                .status(BookingStatus.CONFIRMED)
                .totalAmount(1600000)
                .paidAmount(1600000)
                .build();
        booking.addGuest(BookingGuest.builder()
                .fullName(guestName)
                .email("guest@example.test")
                .isPrimary(true)
                .build());
        return bookings.saveAndFlush(booking);
    }
}
