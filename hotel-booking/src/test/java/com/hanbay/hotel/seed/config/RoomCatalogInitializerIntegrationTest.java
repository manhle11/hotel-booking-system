package com.hanbay.hotel.seed.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "hotel.demo-catalog.enabled=true")
@ActiveProfiles("local")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RoomCatalogInitializerIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RoomCatalogInitializer initializer;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void freshLocalStartupProducesCanonicalCatalogAndIsIdempotent() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_types", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(35);
        var ids = jdbc.queryForList("SELECT id FROM rooms ORDER BY id");
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForList("SELECT id FROM rooms ORDER BY id")).isEqualTo(ids);
    }

    @Test
    void deliberatelyRemovedInventoryIsNeverInventedOnRestart() {
        jdbc.update("DELETE FROM rooms WHERE room_number='201'");
        initializer.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class))
                .isEqualTo(34);
    }

    @Test
    void removedOrCustomizedServicesAreNotRecreatedByStartup() {
        jdbc.update("DELETE FROM add_on_services WHERE code='EXTRA_MEAL'");
        jdbc.update("UPDATE add_on_services SET price=123456 WHERE code='BREAKFAST_BUFFET'");
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM add_on_services", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT price FROM add_on_services WHERE code='BREAKFAST_BUFFET'", Long.class))
                .isEqualTo(123456L);
    }
}
