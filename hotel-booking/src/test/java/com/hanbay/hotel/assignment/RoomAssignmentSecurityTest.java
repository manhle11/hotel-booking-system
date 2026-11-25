package com.hanbay.hotel.assignment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomAssignmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class RoomAssignmentSecurityTest {
    private static final String BASE = "/api/v1/front-desk/room-assignments";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtService jwt;

    @MockitoBean
    RoomAssignmentService service;

    private void authenticate(String role) {
        when(jwt.validate("staff"))
                .thenReturn(Optional.of(
                        new JwtService.ValidatedJwt(UUID.randomUUID().toString(), List.of(role))));
    }

    @Test
    void calendarRequiresStaffAndDoesNotGrantAdminOperationalAccess() throws Exception {
        mvc.perform(get(BASE).param("from", "2026-11-24").param("to", "2026-12-24"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "ADMIN", "HOUSEKEEPER")) {
            authenticate(role);
            mvc.perform(get(BASE)
                            .param("from", "2026-11-24")
                            .param("to", "2026-12-24")
                            .header("Authorization", "Bearer staff"))
                    .andExpect(status().isForbidden());
        }
        for (String role : List.of("RECEPTIONIST", "MANAGER")) {
            authenticate(role);
            mvc.perform(get(BASE)
                            .param("from", "2026-11-24")
                            .param("to", "2026-12-24")
                            .header("Authorization", "Bearer staff"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void plansAndMutationsRequireStaff() throws Exception {
        authenticate("CUSTOMER");
        mvc.perform(post(BASE + "/plans")
                        .header("Authorization", "Bearer staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomTypeId\":\"" + UUID.randomUUID()
                                + "\",\"from\":\"2026-11-24\",\"to\":\"2026-12-24\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/history")
                        .param("roomTypeId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadsAreRejectedBeforePlanningOrWriting() throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/plans")
                        .header("Authorization", "Bearer staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/plans/" + UUID.randomUUID() + "/apply")
                        .header("Authorization", "Bearer staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/suggested-room")
                        .param("bookingCode", "X".repeat(51))
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stalePlansReturnAConflictWithTheActionableErrorCode() throws Exception {
        authenticate("RECEPTIONIST");
        when(service.apply(any(), anyString())).thenThrow(AssignmentException.stale());
        mvc.perform(post(BASE + "/plans/" + UUID.randomUUID() + "/apply")
                        .header("Authorization", "Bearer staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"old-version\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_STALE"));
    }
}
