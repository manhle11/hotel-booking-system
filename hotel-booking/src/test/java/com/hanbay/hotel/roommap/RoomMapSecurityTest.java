package com.hanbay.hotel.roommap;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomMapController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class RoomMapSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    RoomMapService service;

    @MockitoBean
    JwtService jwt;

    @MockitoBean(name = "jpaMappingContext")
    JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void anonymousCanReadPublicMapButNotOperationalMap() throws Exception {
        when(service.publicMap()).thenReturn(new RoomMapDto.PublicMap(List.of()));
        mvc.perform(get("/api/v1/hotel-map")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/front-desk/room-map")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void allowedStaffCanReadWithoutSharedHttpCaching(String role) throws Exception {
        when(service.staffMap(null)).thenReturn(new RoomMapDto.StaffMap(List.of(), Instant.now()));
        mvc.perform(get("/api/v1/front-desk/room-map").with(user("staff").roles(role)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOUSEKEEPER", "ADMIN", "GUEST"})
    void unrelatedRolesCannotReadOperations(String role) throws Exception {
        mvc.perform(get("/api/v1/front-desk/room-map").with(user("staff").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void receptionistCannotPublishAndManagerCannotUseCheckInRoleImplicitly() throws Exception {
        mvc.perform(put("/api/v1/management/floor-plans/3")
                        .with(user("staff").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings/BOOKING/check-in")
                        .with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void managerCanPublishAndInvalidGeometryIsValidatedBeforeService() throws Exception {
        mvc.perform(put("/api/v1/management/floor-plans/3")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());
        verify(service).publish(eq(3), any());
        clearInvocations(service);
        mvc.perform(put("/api/v1/management/floor-plans/3")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("24", "-1")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private String validBody() {
        return """
                {"name":"Tầng 3","gridColumns":24,"gridRows":16,"expectedVersion":0,"elements":[]}
                """;
    }
}
