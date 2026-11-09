package com.hanbay.hotel.db;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RoomCatalogBootstrapMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @BeforeEach
    void fresh() {
        var f = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load();
        f.clean();
        f.migrate();
    }

    private String query(String sql) throws Exception {
        try (var c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            r.next();
            return r.getString(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (var c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    void upgradesOnlyPristineSeedThenRepeatedBootstrapPreservesAllRows() throws Exception {
        assertThat(query("SELECT count(*) FROM room_types")).isEqualTo("18");
        assertThat(query("SELECT initialize_hanbay_room_demo_catalog()")).isEqualTo("t");
        assertThat(query("SELECT count(*) FROM room_types")).isEqualTo("8");
        assertThat(query("SELECT count(*) FROM rooms")).isEqualTo("35");
        assertThat(query("SELECT capacity_children FROM room_types WHERE code='FAMILY_DOUBLE_QUEEN'"))
                .isEqualTo("2");
        assertThat(query("SELECT quantity_unit FROM add_on_services WHERE code='BREAKFAST_BUFFET'"))
                .isEqualTo("SERVING");
        var before = query("SELECT md5(string_agg(to_jsonb(r)::text,',' ORDER BY id)) FROM rooms r");
        assertThat(query("SELECT initialize_hanbay_room_demo_catalog()")).isEqualTo("f");
        assertThat(query("SELECT md5(string_agg(to_jsonb(r)::text,',' ORDER BY id)) FROM rooms r"))
                .isEqualTo(before);
    }

    @Test
    void preservesAnyManagerEditEvenWithoutBookings() throws Exception {
        execute("UPDATE room_types SET base_price=1234567 WHERE code='SUPERIOR'");
        assertThat(query("SELECT initialize_hanbay_room_demo_catalog()")).isEqualTo("f");
        assertThat(query("SELECT base_price FROM room_types WHERE code='SUPERIOR'"))
                .isEqualTo("1234567");
        assertThat(query("SELECT count(*) FROM rooms")).isEqualTo("9");
    }

    @Test
    void preservesAnExistingBookingAndItsRoomType() throws Exception {
        execute(
                "INSERT INTO bookings(booking_code,room_type_id,check_in_date,check_out_date,status,total_amount,created_at) VALUES ('PRESERVE','10000000-0000-0000-0000-000000000001','2027-01-01','2027-01-02','CONFIRMED',1000000,CURRENT_TIMESTAMP)");
        assertThat(query("SELECT initialize_hanbay_room_demo_catalog()")).isEqualTo("f");
        assertThat(query("SELECT count(*) FROM bookings")).isEqualTo("1");
        assertThat(query("SELECT count(*) FROM room_types")).isEqualTo("18");
    }

    @Test
    void preservesPhysicalInventoryEdits() throws Exception {
        execute("UPDATE rooms SET status='MAINTENANCE' WHERE room_number='S101'");
        assertThat(query("SELECT initialize_hanbay_room_demo_catalog()")).isEqualTo("f");
        assertThat(query("SELECT status FROM rooms WHERE room_number='S101'")).isEqualTo("MAINTENANCE");
    }
}
