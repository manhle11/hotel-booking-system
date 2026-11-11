package com.hanbay.hotel.review;

import static org.assertj.core.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class ReviewDemoSeedIntegrationTest {
    private static final Map<String, UUID> IDS = Map.of(
            "customer", UUID.fromString("2b513a5a-8e19-5c7c-84c7-cd13df6cd541"),
            "other", UUID.fromString("18a1fe26-902a-5663-b6ef-b4e9a08d750a"),
            "manager", UUID.fromString("4c7b258b-5794-5279-9b26-0d5cf65ac636"));

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:pg17").withDatabaseName("hotelbooking_db");

    private String seed;

    @BeforeEach
    void migrateIsolatedDatabase() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        execute(
                """
                INSERT INTO room_types (code, name, base_price, max_guests, capacity_adults, created_at)
                VALUES ('DELUXE_TWIN', 'Deluxe Twin', 1000000, 2, 2, now()),
                       ('DELUXE_KING', 'Deluxe King', 1000000, 2, 2, now())
                ON CONFLICT (code) DO NOTHING;
                """);
        seed = Files.readString(Path.of("../scripts/seed-room-type-reviews-demo.sql"));
    }

    @Test
    void freshSeedUsesValidAccountUuidsAndRepeatedRunsPreserveTestResults() throws Exception {
        execute(seed);
        assertDemoCounts();
        for (var entry : IDS.entrySet()) {
            UUID id = UUID.fromString(
                    text("SELECT id::text FROM users WHERE email = 'review." + entry.getKey() + "@example.test'"));
            assertThat(id).isEqualTo(entry.getValue());
            assertThat(id.version()).isEqualTo(5);
            assertThat(id.variant()).isEqualTo(2);
        }
        execute(
                """
                UPDATE room_type_reviews SET comment = 'Kết quả kiểm thử được giữ lại'
                WHERE booking_id = (SELECT id FROM bookings WHERE booking_code = 'RV-DEMO-09');
                """);
        execute(seed);
        assertDemoCounts();
        assertThat(
                        text(
                                """
                SELECT comment FROM room_type_reviews
                WHERE booking_id = (SELECT id FROM bookings WHERE booking_code = 'RV-DEMO-09')
                """))
                .isEqualTo("Kết quả kiểm thử được giữ lại");
    }

    @Test
    void repairsLegacyIdsAndPreservesProfilesOwnershipRepliesDeletionAuditAndTokens() throws Exception {
        execute(legacySeed());
        execute(
                """
                UPDATE users SET full_name = 'Tên khách đã chỉnh sửa', phone = '0900000099',
                    avatar_url = '/demo-avatar.png' WHERE email = 'review.customer@example.test';
                INSERT INTO refresh_tokens(user_id, token_hash, expired_at)
                SELECT id, 'demo-refresh-token', now() + interval '1 day'
                FROM users WHERE email = 'review.customer@example.test';
                UPDATE room_type_reviews SET comment = 'Nhận xét đã chỉnh sửa'
                WHERE booking_id = (SELECT id FROM bookings WHERE booking_code = 'RV-DEMO-09');
                """);
        execute(seed);
        assertDemoCounts();
        for (var entry : IDS.entrySet()) {
            assertThat(text("SELECT id::text FROM users WHERE email = 'review." + entry.getKey() + "@example.test'"))
                    .isEqualTo(entry.getValue().toString());
            assertThat(count("SELECT count(*) FROM users WHERE id = '" + legacyId(entry.getKey()) + "'"))
                    .isZero();
        }
        String customer = IDS.get("customer").toString();
        String manager = IDS.get("manager").toString();
        assertThat(count("SELECT count(*) FROM bookings WHERE booked_by = '" + customer + "'"))
                .isEqualTo(19);
        assertThat(count("SELECT count(*) FROM booking_orders WHERE booked_by = '" + customer + "'"))
                .isEqualTo(17);
        assertThat(count("SELECT count(*) FROM room_type_reviews WHERE reviewer_id = '" + customer + "'"))
                .isEqualTo(8);
        assertThat(count("SELECT count(*) FROM room_type_reviews WHERE deleted_by = '" + manager + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM room_type_reviews WHERE replied_by = '" + manager + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_roles WHERE user_id = '" + manager + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = '" + customer + "'"))
                .isEqualTo(1);
        assertThat(text("SELECT full_name || '|' || phone || '|' || avatar_url FROM users WHERE id = '" + customer
                        + "'"))
                .isEqualTo("Tên khách đã chỉnh sửa|0900000099|/demo-avatar.png");
        assertThat(
                        text(
                                """
                SELECT comment FROM room_type_reviews
                WHERE booking_id = (SELECT id FROM bookings WHERE booking_code = 'RV-DEMO-09')
                """))
                .isEqualTo("Nhận xét đã chỉnh sửa");
        execute(seed);
        assertDemoCounts();
    }

    @Test
    void unexpectedLegacyIdentityRollsBackEveryRepair() throws Exception {
        execute(legacySeed());
        execute("UPDATE users SET email = 'different@example.test' WHERE email = 'review.other@example.test'");
        assertThatThrownBy(() -> execute(seed))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Unexpected legacy demo identity");
        assertThat(text("SELECT id::text FROM users WHERE email = 'review.customer@example.test'"))
                .isEqualTo(legacyId("customer").toString());
        assertThat(count("SELECT count(*) FROM users WHERE id = '" + IDS.get("customer") + "'"))
                .isZero();
        assertDemoCounts();
    }

    private void assertDemoCounts() throws SQLException {
        assertThat(count("SELECT count(*) FROM bookings WHERE booking_code LIKE 'RV-DEMO-%'"))
                .isEqualTo(21);
        assertThat(count("SELECT count(*) FROM booking_orders WHERE order_code LIKE 'RV-ORDER-%'"))
                .isEqualTo(19);
        assertThat(count("SELECT count(*) FROM room_type_reviews")).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM room_type_reviews WHERE deleted_at IS NULL"))
                .isEqualTo(7);
    }

    private String legacySeed() throws Exception {
        String legacy = seed;
        for (var entry : IDS.entrySet()) {
            legacy = legacy.replace(
                    entry.getValue().toString(), legacyId(entry.getKey()).toString());
        }
        return legacy;
    }

    private UUID legacyId(String key) throws Exception {
        byte[] hash = MessageDigest.getInstance("MD5")
                .digest(("hanbay-review-demo-user-" + key).getBytes(StandardCharsets.UTF_8));
        ByteBuffer bytes = ByteBuffer.wrap(hash);
        return new UUID(bytes.getLong(), bytes.getLong());
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String sql) throws SQLException {
        return Long.parseLong(text(sql));
    }

    private String text(String sql) throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
