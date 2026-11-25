package com.hanbay.hotel.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class RoomAssignmentMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Test
    void upgradingExistingDataImportsActualStaysAndMaintenanceWithoutChangingBookingHistory() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("202611181700")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID type = UUID.randomUUID(), occupied = UUID.randomUUID(), maintained = UUID.randomUUID();
        UUID staying = UUID.randomUUID(), historical = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO room_types(id, code, name, base_price, max_guests, capacity_adults, capacity_children, created_at)
                VALUES (?, 'MIGRATION-ASSIGNMENT', 'Migration fixture', 800000, 2, 2, 0, CURRENT_TIMESTAMP)
                """,
                type);
        jdbc.update(
                "INSERT INTO rooms(id, room_type_id, room_number, status, created_at) VALUES (?, ?, 'MIGRATION-201', 'OCCUPIED', CURRENT_TIMESTAMP)",
                occupied,
                type);
        jdbc.update(
                "INSERT INTO rooms(id, room_type_id, room_number, status, created_at) VALUES (?, ?, 'MIGRATION-202', 'MAINTENANCE', CURRENT_TIMESTAMP)",
                maintained,
                type);
        jdbc.update(
                """
                INSERT INTO bookings(id, booking_code, room_type_id, assigned_room_id, check_in_date, check_out_date, status, total_amount, created_at)
                VALUES (?, 'MIGRATION-STAY', ?, ?, '2026-11-24', '2026-11-27', 'CHECKED_IN', 1600000, CURRENT_TIMESTAMP)
                """,
                staying,
                type,
                occupied);
        jdbc.update(
                """
                INSERT INTO bookings(id, booking_code, room_type_id, assigned_room_id, check_in_date, check_out_date, status, total_amount, created_at)
                VALUES (?, 'MIGRATION-HISTORY', ?, ?, '2026-11-20', '2026-11-22', 'CHECKED_OUT', 1600000, CURRENT_TIMESTAMP)
                """,
                historical,
                type,
                occupied);
        var migration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM room_allocations WHERE booking_id = ? AND active", String.class, staying))
                .isEqualTo("OCCUPIED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM room_allocations WHERE booking_id = ?", Integer.class, historical))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM room_allocations WHERE room_id = ? AND booking_id IS NULL AND active AND check_out IS NULL",
                        Integer.class,
                        maintained))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT assigned_room_id FROM bookings WHERE id = ?", UUID.class, historical))
                .isEqualTo(occupied);
        assertThat(jdbc.queryForObject("SELECT status::text FROM rooms WHERE id = ?", String.class, occupied))
                .isEqualTo("OCCUPIED");
        assertThat(migration.migrate().migrationsExecuted).isZero();
    }
}
