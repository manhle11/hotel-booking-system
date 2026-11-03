package com.hanbay.hotel.seed.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RoomDataInitializerIntegrationTest {

    private static final UUID OCEAN_SUITE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired
    private RoomDataInitializer initializer;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void removeOceanSuiteInventory() {
        jdbcTemplate.update("DELETE FROM rooms WHERE room_type_id = ?", OCEAN_SUITE_ID);
    }

    @Test
    void preservesConfiguredInventoryOnRepeatedStartup() {
        var roomType = roomTypeRepository.findById(OCEAN_SUITE_ID).orElseThrow();
        Room configuredRoom = roomRepository.saveAndFlush(Room.builder()
                .roomType(roomType)
                .roomNumber("1301")
                .floor(13)
                .status(RoomStatus.AVAILABLE)
                .build());

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.queryForList("SELECT id FROM rooms WHERE room_type_id = ?", UUID.class, OCEAN_SUITE_ID))
                .containsExactly(configuredRoom.getId());
    }

    @Test
    void seedsMissingOceanSuiteInventoryOnlyOnce() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.queryForList(
                        "SELECT room_number FROM rooms WHERE room_type_id = ? ORDER BY room_number",
                        String.class,
                        OCEAN_SUITE_ID))
                .containsExactly("OS701", "OS702", "OS801");
    }
}
