package com.hanbay.hotel.roommap;

import static org.mockito.Mockito.*;

import com.hanbay.hotel.seed.config.RoomCatalogInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FloorPlanBootstrapSafetyTest {
    @Test
    void configuredLayoutPreventsAutomaticDemoReplacement() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM hotel_floor_plans)", Boolean.class))
                .thenReturn(true);
        new RoomCatalogInitializer(jdbc).run(null);
        verify(jdbc, never()).queryForObject("SELECT initialize_hanbay_room_demo_catalog()", Boolean.class);
    }
}
