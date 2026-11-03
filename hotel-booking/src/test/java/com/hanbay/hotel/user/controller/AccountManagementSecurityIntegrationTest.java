package com.hanbay.hotel.user.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import com.hanbay.hotel.user.service.AccountManagementService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountManagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class AccountManagementSecurityIntegrationTest {
    private static final String ID = "96a8f221-c24d-4a52-a91d-b110fbde8c0e";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtService jwt;

    @MockitoBean
    private AccountManagementService service;

    @Test
    void anonymousCannotReadAccounts() throws Exception {
        mvc.perform(get("/api/v1/admin/accounts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "RECEPTIONIST", "HOUSEKEEPER", "GUEST"})
    void nonAdminCannotUseAnyAccountEndpoint(String role) throws Exception {
        authenticate(role);
        for (var request : List.of(
                get("/api/v1/admin/accounts"),
                get("/api/v1/admin/roles"),
                post("/api/v1/admin/accounts"),
                put("/api/v1/admin/accounts/" + ID),
                patch("/api/v1/admin/accounts/" + ID + "/status"),
                put("/api/v1/admin/accounts/" + ID + "/roles"))) {
            mvc.perform(request.header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminCanReadAccountsAndRoleCatalog() throws Exception {
        authenticate("ADMIN");
        when(service.search(0, 10, null, null, null))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));
        mvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateEditBlockAndAssignRoles() throws Exception {
        authenticate("ADMIN");
        mvc.perform(
                        post("/api/v1/admin/accounts")
                                .header("Authorization", "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fullName\":\"Nhân viên mới\",\"email\":\"staff@example.com\",\"password\":\"Password123\",\"roles\":[\"MANAGER\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/admin/accounts/" + ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tên mới\",\"phone\":null}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/accounts/" + ID + "/status")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/accounts/" + ID + "/roles")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"roles\":null}",
                "{\"roles\":[null]}",
                "{\"roles\":[\"UNKNOWN\"]}",
                "{\"roles\":[\"MANAGER\",\"RECEPTIONIST\"]}",
                "{\"roles\":[\"ADMIN\",\"MANAGER\"]}"
            })
    void invalidRolesCannotReachService(String body) throws Exception {
        authenticate("ADMIN");
        mvc.perform(put("/api/v1/admin/accounts/" + ID + "/roles")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"MANAGER\",\"HOUSEKEEPER\"]", "[\"ADMIN\",\"RECEPTIONIST\"]"})
    void cannotCreateAccountWithMultipleRoles(String roles) throws Exception {
        authenticate("ADMIN");
        mvc.perform(post("/api/v1/admin/accounts")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Nhân viên\",\"email\":\"staff@example.com\",\"password\":\"Password123\",\"roles\":"
                                        + roles + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.roles").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ADMIN,MANAGER,RECEPTIONIST,HOUSEKEEPER"})
    void adminCannotUseOldHotelOperationsEvenWithLegacyExtraRoles(String roles) throws Exception {
        authenticate(roles.split(","));
        for (String path : List.of(
                "/api/v1/reports/dashboard",
                "/api/v1/management/rooms",
                "/api/v1/management/room-types",
                "/api/v1/management/add-ons",
                "/api/v1/management/price-rules",
                "/api/v1/front-desk/arrivals/today",
                "/api/v1/front-desk/departures/today",
                "/api/v1/payment-monitoring/payments",
                "/api/v1/payment-monitoring/webhook-events",
                "/api/v1/payment-monitoring/refunds",
                "/api/v1/housekeeping/rooms",
                "/actuator/metrics")) {
            mvc.perform(get(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/bookings/" + ID + "/check-out").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payments/requests").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private void authenticate(String... roles) {
        when(jwt.validate("token")).thenReturn(Optional.of(new JwtService.ValidatedJwt(ID, List.of(roles))));
    }
}
