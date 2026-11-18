package com.hanbay.hotel.room;

import static org.assertj.core.api.Assertions.*;

import com.hanbay.hotel.room.domain.RoomViewType;
import com.hanbay.hotel.room.dto.RoomTypeBedResponse;
import com.hanbay.hotel.room.service.*;
import com.hanbay.hotel.seed.config.RoomCatalogInitializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RoomCatalogResetIntegrationTest {
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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RoomTypeService catalog;

    @Autowired
    private RoomTypeManagementService management;

    @Autowired
    private AvailableRoomTypeSearchService search;

    private RoomCatalogInitializer initializer;

    private String script;

    @BeforeEach
    void reset() throws Exception {
        initializer = new RoomCatalogInitializer(jdbc);
        script = Files.readString(Path.of("../scripts/reset-room-catalog.sql"));
        execute(script);
    }

    @Test
    void policyQaSeedAddsReusableScenariosWithoutChangingExistingData() throws Exception {
        jdbc.update(
                "INSERT INTO users(email,full_name,password_hash,status) VALUES ('qa-policy@example.test','Policy QA','test-hash','ACTIVE') ON CONFLICT(email) DO NOTHING");
        var before = snapshots(List.of("users", "roles", "user_roles", "flyway_schema_history"));
        String seed = Files.readString(Path.of("../scripts/seed-booking-policy-qa.sql"));

        execute(seed);

        assertThat(snapshots(before.keySet())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_types", Integer.class))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(43);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE booking_code LIKE 'QP%-%'", Integer.class))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject(
                        "SELECT sum(amount) FROM payment_refunds WHERE idempotency_key LIKE 'qa-refund:%'", Long.class))
                .isEqualTo(2_100_000L);
        assertThat(jdbc.queryForObject(
                        "SELECT paid_amount FROM bookings WHERE booking_code LIKE 'QP%-EARLY'", Long.class))
                .isEqualTo(300_000L);

        execute(seed);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE booking_code LIKE 'QP%-%'", Integer.class))
                .isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(51);
        assertThat(snapshots(before.keySet())).isEqualTo(before);
    }

    @Test
    void exactlyMatchesEightTypesBedsAndThirtyFivePhysicalRooms() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_categories", Integer.class))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_types", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(35);
        assertThat(jdbc.queryForList(
                        """
                SELECT rt.code || ':' || rt.bed_type || ':' || count(r.id)
                FROM room_types rt JOIN rooms r ON r.room_type_id=rt.id
                GROUP BY rt.code,rt.bed_type ORDER BY rt.code
                """,
                        String.class))
                .containsExactly(
                        "DELUXE_KING:1 KING:5",
                        "DELUXE_TWIN:2 SINGLE:5",
                        "FAMILY_DOUBLE_QUEEN:2 QUEEN:4",
                        "STANDARD_QUEEN:1 QUEEN:6",
                        "SUITE_KING:1 KING:2",
                        "SUPERIOR_QUEEN:1 QUEEN:5",
                        "SUPERIOR_TWIN:2 SINGLE:5",
                        "TRIPLE_MIXED:1 QUEEN + 1 SINGLE:3");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT room_number) FROM rooms", Integer.class))
                .isEqualTo(35);
        for (var item : catalog.getCatalog(0, 100, null, "name", "asc").content()) {
            var detail = catalog.getDetail(item.id());
            assertThat(detail.beds()).isEqualTo(item.beds()).isNotEmpty();
            assertThat(detail.beds()).extracting(RoomTypeBedResponse::bedType).doesNotHaveDuplicates();
            assertThat(detail.images()).hasSize(3).doesNotHaveDuplicates();
            assertThat(detail.amenities()).hasSizeGreaterThanOrEqualTo(11);
            assertThat(detail.category()).isNotNull().isEqualTo(item.category());
            assertThat(detail.areaM2()).isPositive();
            assertThat(detail.viewType()).isNotNull();
            assertThat(detail.hasBalcony()).isNotNull();
            assertThat(detail.highFloor()).isNotNull();
        }
    }

    @Test
    void realSearchUsesThirtyFivePhysicalRoomsAndDoesNotMultiplyMixedBeds() {
        LocalDate from = LocalDate.now().plusDays(10);
        var result = search.search(from, from.plusDays(2), 1, 0, null, 0, 100);
        assertThat(result.content()).hasSize(8);
        assertThat(result.content().stream()
                        .mapToLong(room -> room.availableRooms())
                        .sum())
                .isEqualTo(35);
        var triple = result.content().stream()
                .filter(room -> room.code().equals("TRIPLE_MIXED"))
                .findFirst()
                .orElseThrow();
        assertThat(triple.availableRooms()).isEqualTo(3);
        assertThat(triple.beds()).hasSize(2);
        jdbc.update(
                """
                INSERT INTO bookings(booking_code,room_type_id,check_in_date,check_out_date,total_amount,status,created_at)
                VALUES ('RESET-INVENTORY',?,?,?,1000000,'CONFIRMED',CURRENT_TIMESTAMP)
                """,
                triple.id(),
                from,
                from.plusDays(2));
        var reserved = search.search(from, from.plusDays(2), 1, 0, triple.id(), 0, 100)
                .content()
                .get(0);
        assertThat(reserved.availableRooms()).isEqualTo(2);
    }

    @Test
    void separatesPhysicalAmenitiesFeaturesAndPaidServices() {
        assertThat(jdbc.queryForList("SELECT name FROM amenities", String.class))
                .contains("Wi-Fi tốc độ cao", "TV", "Điều hòa", "Tủ lạnh mini", "Minibar")
                .doesNotContain("Ban công riêng", "Tầng cao", "Tầm nhìn toàn cảnh", "Giường King-size", "Dịch vụ 24/7");
        assertThat(jdbc.queryForList("SELECT code FROM add_on_services ORDER BY code", String.class))
                .containsExactly(
                        "AIRPORT_DROPOFF", "AIRPORT_PICKUP", "BREAKFAST_BUFFET", "EXTRA_MEAL", "ROOM_DECORATION");
        var deluxe = catalog.getCatalog(0, 100, "Deluxe King", "name", "asc")
                .content()
                .get(0);
        var detail = catalog.getDetail(deluxe.id());
        assertThat(detail.viewType()).isEqualTo(RoomViewType.SEA);
        assertThat(detail.hasBalcony()).isTrue();
        assertThat(detail.highFloor()).isTrue();
        assertThat(detail.amenities())
                .extracting(amenity -> amenity.name())
                .doesNotContain("Ban công riêng", "Giường King-size");
    }

    @Test
    void filtersFeaturesBeforePaginationAndAllowsFamilyWithTwoChildren() {
        var from = LocalDate.now().plusDays(10);
        var sea = search.search(from, from.plusDays(2), 1, 0, null, 0, 2, RoomViewType.SEA, true, true);
        assertThat(sea.totalElements()).isEqualTo(4);
        assertThat(sea.content()).hasSize(2);
        var low = search.search(from, from.plusDays(2), 1, 0, null, 0, 100, null, false, false);
        assertThat(low.content())
                .extracting("code")
                .containsExactlyInAnyOrder("STANDARD_QUEEN", "SUPERIOR_QUEEN", "SUPERIOR_TWIN");
        var family = search.search(from, from.plusDays(2), 2, 2, null, 0, 100);
        assertThat(family.content()).extracting("code").containsExactly("FAMILY_DOUBLE_QUEEN");
        assertThat(search.search(from, from.plusDays(2), 4, 2, null, 0, 100).content())
                .isEmpty();
    }

    @Test
    void resetRemovesTransactionsButPreservesAccountsRolesTokensAndMigrationHistory() throws Exception {
        UUID owner = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,email,full_name,password_hash,status) VALUES (?,?,'Reset fixture','test-hash','ACTIVE')",
                owner,
                owner + "@example.test");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='MANAGER'", owner);
        jdbc.update(
                "INSERT INTO refresh_tokens(user_id,token_hash,expired_at) VALUES (?, ?,CURRENT_TIMESTAMP + INTERVAL '1 day')",
                owner,
                owner.toString());
        UUID type = jdbc.queryForObject("SELECT id FROM room_types WHERE code='SUITE_KING'", UUID.class);
        UUID booking = UUID.randomUUID(),
                payment = UUID.randomUUID(),
                transaction = UUID.randomUUID(),
                request = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO bookings(id,booking_code,room_type_id,assigned_room_id,check_in_date,check_out_date,total_amount,status,created_at)
                SELECT ?,'WIPE-BOOKING',?,id,CURRENT_DATE+10,CURRENT_DATE+12,1000000,'CONFIRMED',CURRENT_TIMESTAMP
                FROM rooms WHERE room_type_id=? LIMIT 1
                """,
                booking,
                type,
                type);
        jdbc.update(
                "INSERT INTO payments(id,booking_id,payment_code,amount,status,created_at,expires_at) VALUES (?,?,'WIPE-PAYMENT',1000000,'SUCCESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '15 minutes')",
                payment,
                booking);
        jdbc.update(
                "INSERT INTO payment_transactions(id,payment_id,type,status,amount,received_at,created_at) VALUES (?,?,'REFUND','SUCCESS',1000000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                transaction,
                payment);
        jdbc.update(
                "INSERT INTO payment_refunds(payment_id,transaction_id,amount,idempotency_key,requested_at,created_at,updated_at) VALUES (?,?,1000000,'WIPE-REFUND',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                payment,
                transaction);
        jdbc.update(
                "INSERT INTO payment_allocations(id,payment_id,booking_id,amount) VALUES (gen_random_uuid(),?,?,1000000)",
                payment,
                booking);
        jdbc.update(
                "INSERT INTO payment_webhook_events(payment_id,event_id,deduplication_key,deduplication_strategy,payload,signature_valid,received_at,created_at) VALUES (?,'WIPE-EVENT','WIPE-EVENT','EVENT_ID','{}',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                payment);
        jdbc.update(
                "INSERT INTO maintenance_requests(id,room_id,title,created_by) SELECT ?,id,'WIPE-MAINTENANCE',? FROM rooms WHERE room_type_id=? LIMIT 1",
                request,
                owner,
                type);
        jdbc.update(
                "INSERT INTO maintenance_costs(request_id,amount,expense_date,note,revision,recorded_by,recorded_at) VALUES (?,100,CURRENT_DATE,'Fixture',1,?,CURRENT_TIMESTAMP)",
                request,
                owner);
        jdbc.update(
                "INSERT INTO maintenance_cost_history(request_id,amount,expense_date,note,revision,recorded_by,recorded_at) VALUES (?,100,CURRENT_DATE,'Fixture',1,?,CURRENT_TIMESTAMP)",
                request,
                owner);
        insertReviewFixture(owner, type, false);
        insertReviewFixture(owner, type, true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_type_reviews", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM room_type_reviews WHERE deleted_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
        var protectedBefore = snapshots(
                List.of("users", "roles", "user_roles", "refresh_tokens", "flyway_schema_history", "holiday_calendar"));

        execute(script);

        assertThat(snapshots(protectedBefore.keySet())).isEqualTo(protectedBefore);
        for (String table : List.of(
                "room_type_reviews",
                "bookings",
                "booking_orders",
                "payments",
                "payment_transactions",
                "payment_refunds",
                "payment_allocations",
                "payment_webhook_events",
                "maintenance_requests",
                "maintenance_costs",
                "maintenance_cost_history",
                "price_rules")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                    .as(table)
                    .isZero();
        }
    }

    @Test
    void repeatedResetAndApplicationStartupDoNotReintroduceLegacyInventory() throws Exception {
        var expectedIds = jdbc.queryForList("SELECT id::text FROM rooms ORDER BY id", String.class);
        execute(script);
        // IDs and configurations are stable; timestamps change on an explicit reset.
        assertThat(jdbc.queryForList("SELECT room_number FROM rooms ORDER BY room_number", String.class))
                .hasSize(35);
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(35);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_types", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM room_types WHERE code IN ('HANBAY-OCEAN-SUITE','HB_FAMILY_SUITE','JUNIOR_SUITE')",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForList("SELECT id::text FROM rooms ORDER BY id", String.class))
                .isEqualTo(expectedIds);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM add_on_services", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void invalidReplacementRollsBackTheEntireReset() {
        UUID owner = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,email,full_name,status) VALUES (?,?,'Rollback fixture','ACTIVE')",
                owner,
                owner + "@example.test");
        UUID type = jdbc.queryForObject("SELECT id FROM room_types WHERE code='SUITE_KING'", UUID.class);
        insertReviewFixture(owner, type, false);
        insertReviewFixture(owner, type, true);
        var before = snapshots(List.of(
                "room_types", "room_type_beds", "rooms", "amenities", "bookings", "room_type_reviews", "users"));
        String invalid = script.replace(
                "SELECT seed_hanbay_room_demo_catalog();", "SELECT seed_hanbay_room_demo_catalog(); SELECT 1/0;");
        assertThatThrownBy(() -> execute(invalid)).isInstanceOf(SQLException.class);
        assertThat(snapshots(before.keySet())).isEqualTo(before);
    }

    private void insertReviewFixture(UUID owner, UUID type, boolean deleted) {
        UUID booking = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO bookings(id,booking_code,booked_by,room_type_id,check_in_date,check_out_date,
                    total_amount,paid_amount,status,checked_in_at,checked_out_at,created_at)
                VALUES (?,?,?,?,CURRENT_DATE-3,CURRENT_DATE-1,1000000,1000000,'CHECKED_OUT',
                    CURRENT_TIMESTAMP-INTERVAL '3 days',CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP)
                """,
                booking,
                "RESET-REVIEW-" + booking,
                owner,
                type);
        jdbc.update(
                """
                INSERT INTO room_type_reviews(id,booking_id,room_type_id,reviewer_id,reviewer_name,rating,
                    comment,hotel_reply,replied_at,replied_by,deleted_at,deleted_by,delete_reason)
                VALUES (?,?,?,?,'Reset fixture',?,'Review fixture','Reply fixture',CURRENT_TIMESTAMP,?,?,?,?)
                """,
                UUID.randomUUID(),
                booking,
                type,
                owner,
                deleted ? 1 : 5,
                owner,
                deleted ? Timestamp.from(Instant.now()) : null,
                deleted ? owner : null,
                deleted ? "Deleted fixture" : null);
    }

    private Map<String, List<String>> snapshots(Collection<String> tables) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String table : tables)
            values.put(
                    table,
                    jdbc.queryForList(
                            "SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY to_jsonb(t)::text", String.class));
        return values;
    }

    private void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
