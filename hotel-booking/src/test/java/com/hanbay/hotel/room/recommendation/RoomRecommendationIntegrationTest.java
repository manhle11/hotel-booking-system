package com.hanbay.hotel.room.recommendation;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"booking.expiration.cron=-", "logging.level.root=WARN"})
@AutoConfigureMockMvc
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RoomRecommendationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired
    RoomRecommendationService service;

    @Autowired
    RoomTypeRepository types;

    @Autowired
    RoomRepository rooms;

    @Autowired
    BookingRepository bookings;

    @Autowired
    AmenityRepository amenities;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    Clock clock;

    @MockitoBean
    JwtService jwt;

    @MockitoBean
    AuthenticationRateLimitService rateLimit;

    private final LocalDate in = LocalDate.of(2026, 12, 1);
    private RoomType type;
    private String marker;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        marker = "VERIFIED-" + UUID.randomUUID();
        UUID amenityId = UUID.randomUUID();
        jdbc.update("INSERT INTO amenities(id,name,icon) VALUES (?,?,?)", amenityId, marker, "wifi");
        type = RoomType.builder()
                .code(marker)
                .name(marker)
                .basePrice(1700000)
                .maxGuests(3)
                .capacityAdults(2)
                .capacityChildren(1)
                .areaM2(BigDecimal.valueOf(30))
                .viewType(RoomViewType.CITY)
                .build();
        type.replaceBeds(Map.of(BedType.SINGLE, 2));
        type.getAmenities().add(amenities.findById(amenityId).orElseThrow());
        types.saveAndFlush(type);
        for (int i = 0; i < 2; i++)
            rooms.saveAndFlush(Room.builder()
                    .roomType(type)
                    .roomNumber("REC-" + UUID.randomUUID().toString().substring(0, 12))
                    .floor(2)
                    .status(RoomStatus.AVAILABLE)
                    .build());
        when(jwt.validate("guest"))
                .thenReturn(Optional.of(
                        new JwtService.ValidatedJwt(UUID.randomUUID().toString(), List.of())));
    }

    private Criteria criteria() {
        return Criteria.builder()
                .checkIn(in)
                .checkOut(in.plusDays(2))
                .adults(2)
                .children(0)
                .requiredAmenities(List.of(marker))
                .requiredBeds(List.of(new Bed(BedType.SINGLE, 2)))
                .build();
    }

    private List<Long> counts() {
        return List.of(
                jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class),
                jdbc.queryForObject("SELECT count(*) FROM room_allocations", Long.class),
                jdbc.queryForObject("SELECT count(*) FROM room_assignment_plans", Long.class));
    }

    @Test
    void usesRealCatalogPricesAndNightsWithoutWritingBookingsOrAllocations() {
        bookings.saveAndFlush(Booking.builder()
                .bookingCode("REC-" + UUID.randomUUID())
                .roomType(type)
                .checkInDate(in)
                .checkOutDate(in.plusDays(1))
                .status(BookingStatus.CONFIRMED)
                .adultCount(1)
                .build());
        var before = counts();
        var result = service.recommend(criteria());
        assertThat(result.status()).isEqualTo(Status.RECOMMENDED);
        assertThat(result.recommendations()).singleElement().satisfies(r -> {
            assertThat(r.room().id()).isEqualTo(type.getId());
            assertThat(r.room().roomTotal()).isEqualTo(3400000);
            assertThat(r.room().availableRooms()).isEqualTo(1);
            assertThat(r.room().nightlyPrices()).hasSize(2);
            assertThat(r.amenities()).extracting(a -> a.name()).contains(marker);
        });
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void endpointRequiresLoginAndReturnsClarificationsAndValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/room-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/room-recommendations")
                        .header("Authorization", "Bearer guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"));
        mvc.perform(post("/api/v1/room-recommendations")
                        .header("Authorization", "Bearer guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adults\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM_VALIDATION_FAILED"));
    }

    @Test
    void engineContractReturnsStructuredCardsFromTheSameEngine() throws Exception {
        String body = new tools.jackson.databind.ObjectMapper().writeValueAsString(criteria());
        mvc.perform(post("/api/v1/room-recommendations")
                        .header("Authorization", "Bearer guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data.recommendations[0].room.roomTotal").value(3400000))
                .andExpect(jsonPath("$.data.recommendations[0].room.beds[0].quantity")
                        .value(2));
    }
}
