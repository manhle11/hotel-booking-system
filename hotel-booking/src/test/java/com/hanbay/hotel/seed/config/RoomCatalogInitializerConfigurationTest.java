package com.hanbay.hotel.seed.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class RoomCatalogInitializerConfigurationTest {
    private ApplicationContextRunner context(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yaml", "spring.profiles.active=" + profile)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withUserConfiguration(RoomCatalogInitializer.class);
    }

    @Test
    void trackedConfigurationEnablesLocalBootstrapWithoutPrivateLocalYaml() {
        context("local").run(ctx -> assertThat(ctx).hasSingleBean(RoomCatalogInitializer.class));
    }

    @Test
    void productionNeverInitializesDemoRooms() {
        context("prod").run(ctx -> assertThat(ctx).doesNotHaveBean(RoomCatalogInitializer.class));
    }

    @Test
    void localBootstrapCanBeDisabledExplicitly() {
        context("local").withPropertyValues("hotel.demo-catalog.enabled=false").run(ctx -> assertThat(ctx)
                .doesNotHaveBean(RoomCatalogInitializer.class));
    }
}
