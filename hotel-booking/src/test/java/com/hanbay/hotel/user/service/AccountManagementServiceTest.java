package com.hanbay.hotel.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.authentication.domain.entity.Role;
import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import com.hanbay.hotel.authentication.domain.enums.UserStatus;
import com.hanbay.hotel.authentication.exception.EmailAlreadyExistsException;
import com.hanbay.hotel.authentication.exception.PhoneAlreadyExistsException;
import com.hanbay.hotel.authentication.repository.RefreshTokenRepository;
import com.hanbay.hotel.authentication.repository.RoleRepository;
import com.hanbay.hotel.authentication.service.AccessTokenRevocationService;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.dto.CreateAccountRequest;
import com.hanbay.hotel.user.dto.UpdateAccountRequest;
import com.hanbay.hotel.user.exception.AccountManagementException;
import com.hanbay.hotel.user.exception.UserNotFoundException;
import com.hanbay.hotel.user.mapper.UserMapper;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {
    private static final UUID ID = UUID.fromString("96a8f221-c24d-4a52-a91d-b110fbde8c0e");
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Mock
    private UserRepository users;

    @Mock
    private RoleRepository roles;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private CurrentUserProvider currentUser;

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private AccessTokenRevocationService accessTokens;

    private AccountManagementService service;

    @BeforeEach
    void setUp() {
        service = new AccountManagementService(
                users,
                roles,
                Mappers.getMapper(UserMapper.class),
                encoder,
                currentUser,
                refreshTokens,
                accessTokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAccountWithNormalizedIdentityAndHashedPassword() {
        when(roles.findByCode(RoleCode.MANAGER)).thenReturn(Optional.of(role(RoleCode.MANAGER)));
        when(encoder.encode("Password123")).thenReturn("encoded-password");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.create(new CreateAccountRequest(
                "  Nhân viên mới  ", "Staff@Example.com", null, "Password123", Set.of(RoleCode.MANAGER)));
        assertThat(response.email()).isEqualTo("staff@example.com");
        assertThat(response.fullName()).isEqualTo("Nhân viên mới");
        assertThat(response.roles()).containsExactly("MANAGER");
        verify(users).saveAndFlush(org.mockito.ArgumentMatchers.argThat(user -> "encoded-password"
                .equals(user.getPasswordHash())));
    }

    @Test
    void createsGuestWithoutAnyStaffRole() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.create(new CreateAccountRequest(
                                "Khách mới", "guest@example.com", null, "Password123", Set.of()))
                        .roles())
                .isEmpty();
    }

    @Test
    void duplicateEmailCannotCreateAccount() {
        when(users.existsByEmailIgnoreCase("staff@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                        new CreateAccountRequest("Nhân viên", "staff@example.com", null, "Password123", Set.of())))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void unicodePasswordMustRespectBcryptByteLimit() {
        assertThatThrownBy(() -> service.create(
                        new CreateAccountRequest("Nhân viên", "staff@example.com", null, "ộ".repeat(25), Set.of())))
                .isInstanceOf(RequestValidationException.class);
        verifyNoInteractions(encoder);
    }

    @Test
    void blockingRevokesAccessAndRefreshTokens() {
        User user = mutableAccount(UserStatus.ACTIVE, RoleCode.RECEPTIONIST);
        assertThat(service.updateStatus(ID, UserStatus.LOCKED).status()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(refreshTokens).revokeActiveTokensByUserId(ID, NOW);
        verify(accessTokens).revokeAll(ID);
    }

    @Test
    void unlockingAlsoRevokesEarlierSessions() {
        mutableAccount(UserStatus.LOCKED);
        assertThat(service.updateStatus(ID, UserStatus.ACTIVE).status()).isEqualTo(UserStatus.ACTIVE);
        verify(accessTokens).revokeAll(ID);
    }

    @Test
    void selfBlockingIsRejected() {
        when(roles.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(role(RoleCode.ADMIN)));
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(account(UserStatus.ACTIVE, RoleCode.ADMIN)));
        when(currentUser.currentUserId()).thenReturn(Optional.of(ID));
        assertThatThrownBy(() -> service.updateStatus(ID, UserStatus.LOCKED))
                .isInstanceOf(AccountManagementException.class)
                .hasMessageContaining("chính mình");
        verifyNoInteractions(accessTokens, refreshTokens);
    }

    @Test
    void selfDemotionIsRejected() {
        when(roles.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(role(RoleCode.ADMIN)));
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(account(UserStatus.ACTIVE, RoleCode.ADMIN)));
        when(currentUser.currentUserId()).thenReturn(Optional.of(ID));
        assertThatThrownBy(() -> service.updateRoles(ID, Set.of())).isInstanceOf(AccountManagementException.class);
        verifyNoInteractions(accessTokens, refreshTokens);
    }

    @Test
    void lastActiveAdminCannotBeBlockedOrDemoted() {
        when(roles.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(role(RoleCode.ADMIN)));
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(account(UserStatus.ACTIVE, RoleCode.ADMIN)));
        when(users.countByStatusAndRoles_Code(UserStatus.ACTIVE, RoleCode.ADMIN))
                .thenReturn(1L);
        assertThatThrownBy(() -> service.updateStatus(ID, UserStatus.LOCKED))
                .isInstanceOf(AccountManagementException.class)
                .hasMessageContaining("ít nhất một");
        assertThatThrownBy(() -> service.updateRoles(ID, Set.of())).isInstanceOf(AccountManagementException.class);
        verifyNoInteractions(accessTokens, refreshTokens);
    }

    @Test
    void roleRemovalMakesAccountGuestAndRevokesSessions() {
        mutableAccount(UserStatus.ACTIVE, RoleCode.HOUSEKEEPER);
        assertThat(service.updateRoles(ID, Set.of()).roles()).isEmpty();
        verify(refreshTokens).revokeActiveTokensByUserId(ID, NOW);
        verify(accessTokens).revokeAll(ID);
    }

    @Test
    void roleAssignmentRevokesSessions() {
        mutableAccount(UserStatus.ACTIVE);
        when(roles.findByCode(RoleCode.MANAGER)).thenReturn(Optional.of(role(RoleCode.MANAGER)));
        assertThat(service.updateRoles(ID, Set.of(RoleCode.MANAGER)).roles()).containsExactly("MANAGER");
        verify(accessTokens).revokeAll(ID);
    }

    @Test
    void staffRolesCannotBeCombinedWhenUpdatingAccount() {
        when(roles.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(role(RoleCode.ADMIN)));
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        assertThatThrownBy(() -> service.updateRoles(ID, Set.of(RoleCode.MANAGER, RoleCode.RECEPTIONIST)))
                .isInstanceOf(RequestValidationException.class)
                .hasMessageContaining("một vai trò");
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(accessTokens, refreshTokens);
    }

    @Test
    void staffRolesCannotBeCombinedWhenCreatingAccount() {
        assertThatThrownBy(() -> service.create(new CreateAccountRequest(
                        "Nhân viên",
                        "staff@example.com",
                        null,
                        "Password123",
                        Set.of(RoleCode.MANAGER, RoleCode.HOUSEKEEPER))))
                .isInstanceOf(RequestValidationException.class)
                .hasMessageContaining("một vai trò");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotBeCombinedWithOperationalRoles() {
        when(roles.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(role(RoleCode.ADMIN)));
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        assertThatThrownBy(() -> service.updateRoles(ID, Set.of(RoleCode.ADMIN, RoleCode.MANAGER)))
                .isInstanceOf(RequestValidationException.class);
        verifyNoInteractions(accessTokens, refreshTokens);
    }

    @Test
    void duplicatePhoneCannotUpdateAccount() {
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        when(users.existsByPhoneAndIdNot("0900000000", ID)).thenReturn(true);
        assertThatThrownBy(() -> service.update(ID, new UpdateAccountRequest("Tên mới", "0900000000")))
                .isInstanceOf(PhoneAlreadyExistsException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void missingAccountReturnsNotFound() {
        assertThatThrownBy(() -> service.update(ID, new UpdateAccountRequest("Tên mới", null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void rejectsInvalidFiltersAndUnboundedPages() {
        assertThatThrownBy(() -> service.search(-1, 10, null, null, null))
                .isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> service.search(0, 101, null, null, null))
                .isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> service.search(0, 10, null, null, "UNKNOWN"))
                .isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> service.search(0, 10, "a".repeat(151), null, null))
                .isInstanceOf(RequestValidationException.class);
        verifyNoInteractions(users);
    }

    private User mutableAccount(UserStatus status, RoleCode... codes) {
        User user = account(status, codes);
        when(roles.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(role(RoleCode.ADMIN)));
        when(users.findAccountForUpdate(ID)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);
        return user;
    }

    private User account(UserStatus status, RoleCode... codes) {
        Set<Role> assigned = new LinkedHashSet<>();
        for (RoleCode code : codes) assigned.add(role(code));
        return User.builder()
                .id(ID)
                .fullName("Người dùng")
                .email("user@example.com")
                .status(status)
                .roles(assigned)
                .build();
    }

    private Role role(RoleCode code) {
        Role role = new Role();
        role.setId((long) code.ordinal() + 1);
        role.setCode(code);
        role.setName(code.name());
        return role;
    }
}
