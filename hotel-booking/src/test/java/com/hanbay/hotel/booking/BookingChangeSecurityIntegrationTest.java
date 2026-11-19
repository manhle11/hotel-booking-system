package com.hanbay.hotel.booking;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.authentication.service.impl.JwtService.ValidatedJwt;
import com.hanbay.hotel.booking.controller.BookingChangeController;
import com.hanbay.hotel.booking.service.BookingChangeService;
import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import java.util.*;
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

@WebMvcTest(
        controllers = {
            BookingChangeController.class,
            com.hanbay.hotel.booking.controller.BookingChangeSettlementController.class
        })
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {"app.cors.allowed-origins=http://localhost:5173", "debug=false"})
class BookingChangeSecurityIntegrationTest {
    private static final String BOOKING = "/api/v1/bookings/BK-TEST/change-requests";
    private static final String BASE = "/api/v1/front-desk/booking-change-requests";
    private static final UUID ID = UUID.randomUUID();
    private static final String CREATE = "{\"type\":\"CANCELLATION\",\"reason\":\"Khách gọi báo hủy\"}";
    private static final String APPROVE =
            "{\"quoteToken\":\"snapshot\",\"feeAmount\":0,\"note\":\"Đã xem điều kiện hủy\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtService jwt;

    @MockitoBean
    BookingChangeService service;

    @MockitoBean
    com.hanbay.hotel.booking.service.BookingChangeSettlementService settlements;

    @MockitoBean(name = "jpaMappingContext")
    JpaMetamodelMappingContext jpa;

    @Test
    void anonymousCannotInspectOrCreate() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/settlements")).andExpect(status().isUnauthorized());
        mvc.perform(post(BOOKING).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "HOUSEKEEPER", "ADMIN"})
    void unrelatedRolesCannotInspectCreateOrApprove(String role) throws Exception {
        authenticate(role);
        mvc.perform(get(BASE + "/settlements").header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + ID + "/settlement").header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE).header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        mvc.perform(post(BOOKING)
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/approve")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void frontDeskCanRecordAndInspectRequests(String role) throws Exception {
        authenticate(role);
        mvc.perform(get(BASE + "/settlements").header("Authorization", "Bearer test"))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + ID + "/settlement").header("Authorization", "Bearer test"))
                .andExpect(status().isOk());
        verify(settlements).list("", 0, 20);
        verify(settlements).get(ID);
        mvc.perform(post(BOOKING)
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + ID).header("Authorization", "Bearer test")).andExpect(status().isOk());
        verify(service).create(eq("BK-TEST"), any());
        verify(service).quote(ID);
    }

    @Test
    void onlyManagerCanApproveOrReject() throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/" + ID + "/approve")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/reject")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Chưa thống nhất\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        authenticate("MANAGER");
        mvc.perform(post(BASE + "/" + ID + "/approve")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE))
                .andExpect(status().isOk());
        verify(service).approve(eq(ID), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.5", "\"800000\"", "10000000000"})
    void rejectsInvalidMoney(String amount) throws Exception {
        authenticate("MANAGER");
        mvc.perform(post(BASE + "/" + ID + "/approve")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE.replace("\"feeAmount\":0", "\"feeAmount\":" + amount)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void requestAndDecisionNeedAnExplicitReason() throws Exception {
        authenticate("MANAGER");
        mvc.perform(post(BOOKING)
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CANCELLATION\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + ID + "/approve")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE.replace("Đã xem điều kiện hủy", " ")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private void authenticate(String role) {
        when(jwt.validate("test")).thenReturn(Optional.of(new ValidatedJwt("staff-id", List.of(role))));
    }
}
