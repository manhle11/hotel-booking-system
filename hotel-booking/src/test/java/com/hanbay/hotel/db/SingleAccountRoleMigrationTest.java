package com.hanbay.hotel.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class SingleAccountRoleMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("single_role_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migrationKeepsOneRoleRevokesAffectedSessionsAndEnforcesConstraint() throws Exception {
        flyway("202611032300").migrate();
        UUID admin = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID receptionist = UUID.randomUUID();
        UUID housekeeper = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        try (var connection = connection()) {
            seed(connection, admin, "ADMIN", "MANAGER", "HOUSEKEEPER");
            seed(connection, manager, "MANAGER", "RECEPTIONIST", "HOUSEKEEPER");
            seed(connection, receptionist, "RECEPTIONIST", "HOUSEKEEPER");
            seed(connection, housekeeper, "HOUSEKEEPER");
            seed(connection, guest);
        }

        flyway().migrate();

        assertRole(admin, "ADMIN");
        assertRole(manager, "MANAGER");
        assertRole(receptionist, "RECEPTIONIST");
        assertRole(housekeeper, "HOUSEKEEPER");
        assertRole(guest, null);
        for (UUID id : new UUID[] {admin, manager, receptionist}) assertRefreshRevoked(id, true);
        assertRefreshRevoked(housekeeper, false);
        assertRefreshRevoked(guest, false);
        try (var connection = connection();
                var statement = connection.prepareStatement(
                        "INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'MANAGER'")) {
            statement.setObject(1, housekeeper);
            assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .satisfies(error ->
                            assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
        }
    }

    private void seed(Connection connection, UUID id, String... roles) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (id, email, full_name) VALUES (?, ?, 'Migration test')")) {
            statement.setObject(1, id);
            statement.setString(2, id + "@example.com");
            statement.executeUpdate();
        }
        for (String role : roles) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?")) {
                statement.setObject(1, id);
                statement.setString(2, role);
                statement.executeUpdate();
            }
        }
        try (var statement = connection.prepareStatement(
                "INSERT INTO refresh_tokens (user_id, token_hash, expired_at) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day')")) {
            statement.setObject(1, id);
            statement.setString(2, id.toString());
            statement.executeUpdate();
        }
    }

    private void assertRole(UUID user, String expected) throws SQLException {
        try (var connection = connection();
                var statement = connection.prepareStatement(
                        "SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?")) {
            statement.setObject(1, user);
            try (var result = statement.executeQuery()) {
                if (expected != null) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(expected);
                }
                assertThat(result.next()).isFalse();
            }
        }
    }

    private void assertRefreshRevoked(UUID user, boolean revoked) throws SQLException {
        try (var connection = connection();
                var statement = connection.prepareStatement(
                        "SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE user_id = ?")) {
            statement.setObject(1, user);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isEqualTo(revoked);
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway(String... target) {
        var configuration =
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        if (target.length > 0) configuration.target(target[0]);
        return configuration.load();
    }
}
