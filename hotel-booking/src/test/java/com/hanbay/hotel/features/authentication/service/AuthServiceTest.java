package com.hanbay.hotel.features.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.authentication.config.LoginSecurityProperties;
import com.hanbay.hotel.authentication.domain.entity.Role;
import com.hanbay.hotel.authentication.domain.enums.UserStatus;
import com.hanbay.hotel.authentication.dto.request.ForgotPasswordRequest;
import com.hanbay.hotel.authentication.dto.request.LoginRequest;
import com.hanbay.hotel.authentication.dto.response.ForgotPasswordResponse;
import com.hanbay.hotel.authentication.dto.response.LoginResponse;
import com.hanbay.hotel.authentication.exception.InvalidCredentialsException;
import com.hanbay.hotel.authentication.mapper.AuthMapper;
import com.hanbay.hotel.authentication.service.AuthService;
import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.authentication.service.EmailService;
import com.hanbay.hotel.authentication.service.PasswordResetTokenService;
import com.hanbay.hotel.authentication.service.RefreshTokenService;
import com.hanbay.hotel.authentication.service.impl.AuthServiceImpl;
import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.common.config.JwtProperties;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import com.hanbay.hotel.user.service.UserService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CLIENT_IP = "192.0.2.10";
    private static final String VALID_LOGIN_INPUT = String.join("-", "fixture", "valid", "input");
    private static final String INVALID_LOGIN_INPUT = String.join("-", "fixture", "invalid", "input");

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordResetTokenService passwordResetTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthenticationRateLimitService rateLimitService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties jwtProperties = new JwtProperties("hotel-booking-system", "test-secret-123", 3600, 604800);
        authService = new AuthServiceImpl(
                userService,
                userRepository,
                passwordEncoder,
                Mappers.getMapper(AuthMapper.class),
                jwtService,
                refreshTokenService,
                jwtProperties,
                passwordResetTokenService,
                emailService,
                rateLimitService,
                new LoginSecurityProperties(30, 60, 5, 900));
    }

    @Test
    void forgotPasswordShouldNormalizeEmailAndSendInstructionsForActiveUser() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "ADMIN");
        when(userRepository.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenService.issueToken(user)).thenReturn("raw-reset-token");

        ForgotPasswordResponse response = authService.forgotPassword(new ForgotPasswordRequest(" Guest@Example.COM "));

        assertThat(response.status()).isEqualTo("RESET_INSTRUCTIONS_ACCEPTED");
        verify(emailService).sendPasswordResetEmail(user.getEmail(), user.getFullName(), "raw-reset-token");
    }

    @Test
    void forgotPasswordShouldReturnSameResponseWhenEmailDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = authService.forgotPassword(new ForgotPasswordRequest("unknown@example.com"));

        assertThat(response.status()).isEqualTo("RESET_INSTRUCTIONS_ACCEPTED");
        verifyNoInteractions(passwordResetTokenService, emailService);
    }

    @Test
    void forgotPasswordShouldReturnGenericResponseWhenTokenPersistenceFails() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "ADMIN");
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenService.issueToken(user)).thenThrow(new RuntimeException("database unavailable"));

        ForgotPasswordResponse response = authService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));

        assertThat(response.status()).isEqualTo("RESET_INSTRUCTIONS_ACCEPTED");
        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPasswordShouldReturnGenericResponseWhenEmailSendingFails() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "ADMIN");
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenService.issueToken(user)).thenReturn("raw-reset-token");
        doThrow(new RuntimeException("mail server unavailable"))
                .when(emailService)
                .sendPasswordResetEmail(user.getEmail(), user.getFullName(), "raw-reset-token");

        ForgotPasswordResponse response = authService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));

        assertThat(response.status()).isEqualTo("RESET_INSTRUCTIONS_ACCEPTED");
        verify(passwordResetTokenService).revokeToken("raw-reset-token");
    }

    @Test
    void loginShouldReturnTokenAndSingleRoleWhenCredentialsAreValid() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "MANAGER");
        LoginRequest request = new LoginRequest(user.getEmail(), VALID_LOGIN_INPUT);

        when(userService.findByIdentifier(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("mock-jwt-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("mock-refresh-token");

        LoginResponse response = authService.login(request, CLIENT_IP);

        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.user().roles()).containsExactly("MANAGER");
        verify(rateLimitService).clear(eq("login-account-ip"), anyString());
    }

    @Test
    void loginShouldThrowWhenPasswordIsInvalid() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "MANAGER");
        when(userService.findByIdentifier(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), INVALID_LOGIN_INPUT), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(rateLimitService).check(eq("login-account-ip"), anyString(), eq(5), eq(Duration.ofMinutes(15)));
    }

    @Test
    void loginShouldAllowAnActiveAdminAccount() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "ADMIN");
        when(userService.findByIdentifier(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("admin-jwt-token");

        LoginResponse response = authService.login(new LoginRequest(user.getEmail(), VALID_LOGIN_INPUT), CLIENT_IP);

        assertThat(response.accessToken()).isEqualTo("admin-jwt-token");
        assertThat(response.user().roles()).containsExactly("ADMIN");
    }

    @Test
    void loginShouldThrowWhenUserIsNotActive() {
        User user = buildUser(UserStatus.LOCKED, VALID_LOGIN_INPUT, "MANAGER");
        when(userService.findByIdentifier(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), VALID_LOGIN_INPUT), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).generateAccessToken(user);
    }

    @Test
    void loginShouldFindUserByPhoneWhenEmailLookupDoesNotMatch() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "MANAGER");
        user.setPhone("0900000001");

        when(userService.findByIdentifier(user.getPhone())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("phone-login-token");

        LoginResponse response = authService.login(new LoginRequest(user.getPhone(), VALID_LOGIN_INPUT), CLIENT_IP);

        assertThat(response.accessToken()).isEqualTo("phone-login-token");
        assertThat(response.user().id()).isEqualTo(user.getId().toString());
    }

    @Test
    void loginShouldThrowWhenUserDoesNotExist() {
        String identifier = "unknown@example.com";
        when(userService.findByIdentifier(identifier)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(identifier, VALID_LOGIN_INPUT), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(rateLimitService).check(eq("login-account-ip"), anyString(), eq(5), eq(Duration.ofMinutes(15)));
        verify(jwtService, never()).generateAccessToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginFailuresForTheSameAccountShouldUseDifferentKeysForDifferentIps() {
        User user = buildUser(UserStatus.ACTIVE, VALID_LOGIN_INPUT, "MANAGER");
        when(userService.findByIdentifier(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(
                        () -> authService.login(new LoginRequest(user.getEmail(), INVALID_LOGIN_INPUT), "192.0.2.10"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() ->
                        authService.login(new LoginRequest(user.getEmail(), INVALID_LOGIN_INPUT), "198.51.100.20"))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<String> identityCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService, times(2))
                .check(eq("login-account-ip"), identityCaptor.capture(), eq(5), eq(Duration.ofMinutes(15)));
        assertThat(identityCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void loginShouldThrowWhenUserIsInactive() {
        User user = buildUser(UserStatus.INACTIVE, VALID_LOGIN_INPUT, "MANAGER");
        when(userService.findByIdentifier(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), VALID_LOGIN_INPUT), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).generateAccessToken(user);
    }

    private User buildUser(UserStatus status, String rawPassword, String... roleCodes) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("manager@example.com");
        user.setFullName("Hotel Manager");
        user.setStatus(status);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(
                java.util.Arrays.stream(roleCodes).map(this::buildRole).collect(java.util.stream.Collectors.toSet()));
        return user;
    }

    private Role buildRole(String code) {
        Role role = new Role();
        role.setCode(com.hanbay.hotel.authentication.domain.enums.RoleCode.valueOf(code));
        role.setName(code);
        return role;
    }
}
