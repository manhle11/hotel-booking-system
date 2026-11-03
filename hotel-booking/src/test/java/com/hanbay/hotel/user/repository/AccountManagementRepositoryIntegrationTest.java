package com.hanbay.hotel.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import com.hanbay.hotel.authentication.domain.enums.UserStatus;
import com.hanbay.hotel.authentication.repository.RoleRepository;
import com.hanbay.hotel.authentication.service.AccessTokenRevocationService;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.mapper.UserMapperImpl;
import com.hanbay.hotel.user.service.AccountManagementService;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@Import({AccountManagementService.class, UserMapperImpl.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AccountManagementRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("hotelbooking_test")
            .withUsername("hotelbooking_test")
            .withPassword("hotelbooking_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private AccountManagementService service;

    @MockitoBean
    private PasswordEncoder encoder;

    @MockitoBean
    private CurrentUserProvider currentUser;

    @MockitoBean
    private AccessTokenRevocationService accessTokens;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        save("Query Staff", "query-staff@example.com", "0900000091", UserStatus.ACTIVE, RoleCode.MANAGER);
        save("Query Guest", "query-guest@example.com", "0900000092", UserStatus.LOCKED, null);
        save("Query %literal", "query-literal@example.com", "0900000093", UserStatus.ACTIVE, null);
    }

    @Test
    void searchesNameEmailAndPhoneCaseInsensitively() {
        assertThat(service.search(0, 10, "QUERY STAFF", null, null).totalElements())
                .isEqualTo(1);
        assertThat(service.search(0, 10, "QUERY-STAFF@", null, null)
                        .content()
                        .get(0)
                        .email())
                .isEqualTo("query-staff@example.com");
        assertThat(service.search(0, 10, "0900000091", null, null).totalElements())
                .isEqualTo(1);
    }

    @Test
    void combinesRoleAndStatusFilters() {
        assertThat(service.search(0, 10, "Query", UserStatus.ACTIVE, "MANAGER").totalElements())
                .isEqualTo(1);
        assertThat(service.search(0, 10, "Query", UserStatus.LOCKED, "MANAGER").content())
                .isEmpty();
        assertThat(service.search(0, 10, "Query", UserStatus.LOCKED, "GUEST")
                        .content()
                        .get(0)
                        .email())
                .isEqualTo("query-guest@example.com");
        assertThat(service.search(0, 10, "Query", null, "GUEST").totalElements())
                .isEqualTo(2);
    }

    @Test
    void escapesSearchWildcards() {
        assertThat(service.search(0, 10, "%literal", null, null).totalElements())
                .isEqualTo(1);
        assertThat(service.search(0, 10, "_", null, null).totalElements()).isZero();
    }

    @Test
    void paginationHasAccurateTotals() {
        var first = service.search(0, 2, "Query", null, null);
        var second = service.search(1, 2, "Query", null, null);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).hasSize(2);
        assertThat(second.content()).hasSize(1);
        assertThat(second.content().get(0).id())
                .isNotIn(first.content().stream().map(item -> item.id()).toList());
    }

    @Test
    void locksAdminRoleAndAccountWithoutFetchingNullableJoins() {
        assertThat(roles.findByCodeForUpdate(RoleCode.ADMIN)).isPresent();
        User user = users.findByEmailIgnoreCase("query-staff@example.com").orElseThrow();
        assertThat(users.findAccountForUpdate(user.getId())).isPresent();
        long before = users.countByStatusAndRoles_Code(UserStatus.ACTIVE, RoleCode.ADMIN);
        save("Query Admin", "query-admin@example.com", "0900000094", UserStatus.ACTIVE, RoleCode.ADMIN);
        assertThat(users.countByStatusAndRoles_Code(UserStatus.ACTIVE, RoleCode.ADMIN))
                .isEqualTo(before + 1);
    }

    @Test
    void replacesExistingRoleWithoutViolatingSingleRoleConstraint() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-03T10:00:00Z"));
        User user = users.findByEmailIgnoreCase("query-staff@example.com").orElseThrow();
        assertThat(service.updateRoles(user.getId(), Set.of(RoleCode.RECEPTIONIST))
                        .roles())
                .containsExactly("RECEPTIONIST");
        assertThat(users.findByEmailIgnoreCase(user.getEmail()).orElseThrow().getRoles())
                .extracting(role -> role.getCode())
                .containsExactly(RoleCode.RECEPTIONIST);
        assertThat(service.updateRoles(user.getId(), Set.of()).roles()).isEmpty();
        assertThat(service.updateRoles(user.getId(), Set.of(RoleCode.HOUSEKEEPER))
                        .roles())
                .containsExactly("HOUSEKEEPER");
    }

    @Test
    void roleCatalogDescribesAdminAccountScope() {
        assertThat(service.roles())
                .filteredOn(role -> role.code() == RoleCode.ADMIN)
                .singleElement()
                .satisfies(role -> assertThat(role.description()).contains("tài khoản"));
    }

    private void save(String name, String email, String phone, UserStatus status, RoleCode code) {
        users.saveAndFlush(User.builder()
                .fullName(name)
                .email(email)
                .phone(phone)
                .passwordHash("test-hash")
                .status(status)
                .roles(code == null ? Set.of() : Set.of(roles.findByCode(code).orElseThrow()))
                .build());
    }
}
