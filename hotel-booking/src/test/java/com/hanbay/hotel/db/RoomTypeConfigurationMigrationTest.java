package com.hanbay.hotel.db;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class RoomTypeConfigurationMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @BeforeEach
    void clean() {
        flyway(null).clean();
    }

    @Test
    void upgradesLegacyDataWithoutGuessingBedsOrChangingInventoryBookingsAndCapacity() throws Exception {
        flyway("202611061800").migrate();
        UUID legacy = UUID.randomUUID();
        execute(
                """
                INSERT INTO room_types (id, code, name, base_price, max_guests, capacity_adults,
                    capacity_children, bed_type, status, created_at)
                VALUES (?, 'AMBIGUOUS', 'King or Twin', 1000000, 3, 2, 1, 'King hoặc Twin', 'ACTIVE', CURRENT_TIMESTAMP)
                """,
                legacy);
        execute(
                """
                INSERT INTO bookings (booking_code, room_type_id, check_in_date, check_out_date,
                    adult_count, child_count, status, total_amount, created_at)
                VALUES ('LEGACY-BEDS', ?, DATE '2027-01-01', DATE '2027-01-03', 2, 1, 'CONFIRMED', 2000000, CURRENT_TIMESTAMP)
                """,
                legacy);
        execute("UPDATE room_types SET bed_type = NULL WHERE code = 'DELUXE'");
        var typesBefore = rows("SELECT to_jsonb(t)::text FROM room_types t ORDER BY id");
        var roomsBefore = rows("SELECT to_jsonb(r)::text FROM rooms r ORDER BY id");
        var bookingsBefore = rows("SELECT to_jsonb(b)::text FROM bookings b ORDER BY id");
        var pricingBefore = rows("SELECT to_jsonb(p)::text FROM price_rules p ORDER BY id");

        flyway("202611071000").migrate();

        assertThat(rows("SELECT (to_jsonb(t) - 'category_id')::text FROM room_types t ORDER BY id"))
                .isEqualTo(typesBefore);
        assertThat(rows("SELECT to_jsonb(r)::text FROM rooms r ORDER BY id")).isEqualTo(roomsBefore);
        assertThat(rows("SELECT to_jsonb(b)::text FROM bookings b ORDER BY id")).isEqualTo(bookingsBefore);
        assertThat(rows("SELECT to_jsonb(p)::text FROM price_rules p ORDER BY id"))
                .isEqualTo(pricingBefore);
        assertThat(rows("SELECT count(*)::text FROM room_type_beds")).containsExactly("0");
        assertThat(rows("SELECT count(*)::text FROM room_types WHERE category_id IS NOT NULL"))
                .containsExactly("0");
        assertThat(rows("SELECT code FROM room_categories ORDER BY code"))
                .containsExactly("DELUXE", "FAMILY", "STANDARD", "SUITE", "SUPERIOR", "TRIPLE");
        assertThat(flyway("202611071000").migrate().migrationsExecuted).isZero();
    }

    @Test
    void freshSchemaEnforcesBedUniquenessPositiveQuantityAndForeignKeys() throws Exception {
        flyway(null).migrate();
        UUID type = UUID.fromString("10000000-0000-0000-0000-000000000001");
        execute("INSERT INTO room_type_beds(room_type_id, bed_type, quantity) VALUES (?, 'KING', 1)", type);
        assertThatThrownBy(() -> execute(
                        "INSERT INTO room_type_beds(room_type_id, bed_type, quantity) VALUES (?, 'KING', 2)", type))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23505");
        assertThatThrownBy(() -> execute(
                        "INSERT INTO room_type_beds(room_type_id, bed_type, quantity) VALUES (?, 'SINGLE', 0)", type))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23514");
        assertThatThrownBy(() -> execute(
                        "INSERT INTO room_type_beds(room_type_id, bed_type, quantity) VALUES (?, 'SINGLE', -1)", type))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(
                        "INSERT INTO room_type_beds(room_type_id, bed_type, quantity) VALUES (?, 'SINGLE', 1)",
                        UUID.randomUUID()))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23503");
        assertThatThrownBy(() -> execute("UPDATE room_types SET category_id = ? WHERE id = ?", UUID.randomUUID(), type))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23503");
        // Extensibility is a code release, without changing a PostgreSQL enum or this schema.
        execute("INSERT INTO room_type_beds(room_type_id, bed_type, quantity) VALUES (?, 'FUTURE_BED_KIND', 1)", type);
    }

    private List<String> rows(String sql) throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (result.next()) rows.add(result.getString(1));
            return rows;
        }
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (var connection = connection();
                var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            statement.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false);
        if (target != null) config.target(target);
        return config.load();
    }
}
