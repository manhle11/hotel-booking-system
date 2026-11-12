package com.hanbay.hotel.seed.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@Order(0)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hotel.demo-catalog.enabled", havingValue = "true")
public class RoomCatalogInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // An explicitly configured layout is user data, even if the catalog still matches a legacy demo.
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM hotel_floor_plans)", Boolean.class)))
            return;
        jdbc.queryForObject("SELECT initialize_hanbay_room_demo_catalog()", Boolean.class);
    }
}
