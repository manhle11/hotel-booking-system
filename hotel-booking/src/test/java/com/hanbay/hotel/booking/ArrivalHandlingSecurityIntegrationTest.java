package com.hanbay.hotel.booking;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.authentication.service.impl.JwtService.ValidatedJwt;
import com.hanbay.hotel.booking.controller.ArrivalHandlingController;
import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import com.hanbay.hotel.booking.service.ArrivalHandlingService;
import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import com.hanbay.hotel.frontdesk.controller.ArrivalWorkController;
import com.hanbay.hotel.frontdesk.service.ArrivalWorkService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

@WebMvcTest(controllers = {ArrivalHandlingController.class, ArrivalWorkController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {"app.cors.allowed-origins=http://localhost:5173", "debug=false"})
class ArrivalHandlingSecurityIntegrationTest {
    private static final String BASE = "/api/v1/bookings/BK-TEST";
    private static final String BODY =
            """
            {"previewToken":"snapshot", "feeAmount":800000,"reason":"Khách không đến", "contactNote":"Đã gọi điện xác nhận"}
            """;
    private static final String COLLECTION =
            """
            {"requestId":"c3a92cf7-0413-4a75-b8c2-5b53d67139b8","amount":500000,"expectedOutstandingAmount":500000,"note":"Đã nhận tiền mặt"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtService jwt;

    @MockitoBean
    ArrivalHandlingService service;

    @MockitoBean
    ArrivalWorkService work;

    @MockitoBean(name = "jpaMappingContext")
    JpaMetamodelMappingContext jpa;

    @Test
    void anonymousCannotInspectOrResolveStaffCases() throws Exception {
        mvc.perform(get(BASE + "/arrival-handling")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/no-show")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void onlyFrontDeskCanRecordValidatedFeeCollection(String role) throws Exception {
        authenticate(role);
        mvc.perform(post(BASE + "/policy-fee-collection")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COLLECTION))
                .andExpect(status().isOk());
        verify(service).collectFee(eq("BK-TEST"), any(), eq(500000L), eq(500000L), eq("Đã nhận tiền mặt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "ADMIN", "HOUSEKEEPER"})
    void customerAndOtherStaffCannotRecordCashCollection(String role) throws Exception {
        authenticate(role);
        mvc.perform(post(BASE + "/policy-fee-collection")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COLLECTION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "1.5", "\"500000\""})
    void feeCollectionRejectsInvalidMoneyBeforeService(String amount) throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/policy-fee-collection")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COLLECTION.replace("\"amount\":500000", "\"amount\":" + amount)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "HOUSEKEEPER", "ADMIN"})
    void unrelatedRolesCannotResolveOrInspect(String role) throws Exception {
        authenticate(role);
        mvc.perform(get(BASE + "/arrival-handling").header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/no-show")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void staffCanPreviewAndSubmitValidatedDecisions(String role) throws Exception {
        authenticate(role);
        mvc.perform(get(BASE + "/arrival-handling").header("Authorization", "Bearer test"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/no-show")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
        verify(service).markNoShow(eq("BK-TEST"), any());
    }

    @Test
    void releasingAnAgreementRequiresManager() throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/arrival-hold/release")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Đã thông báo cho khách\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void frontDeskCanReadGroupedWorkAndUpdateStructuredArrivalInformation(String role) throws Exception {
        authenticate(role);
        mvc.perform(get("/api/v1/front-desk/arrivals/work")
                        .param("group", "ACTION_REQUIRED")
                        .param("reason", "PAST_ETA")
                        .header("Authorization", "Bearer test"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/arrival-estimate")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedAt\":\"2026-11-12T12:00:00Z\",\"note\":\"Khách hẹn\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/arrival-contact-result")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"PHONE\",\"outcome\":\"NO_ANSWER\",\"note\":\"Gọi lần 1\"}"))
                .andExpect(status().isOk());
        verify(service).estimate(eq("BK-TEST"), any());
        verify(service).structuredContact(eq("BK-TEST"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "ADMIN", "HOUSEKEEPER"})
    void otherRolesCannotReadWorkOrUpdateEta(String role) throws Exception {
        authenticate(role);
        mvc.perform(get("/api/v1/front-desk/arrivals/work").header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/arrival-estimate")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedAt\":\"2026-11-12T12:00:00Z\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, work);
    }

    @Test
    void frontDeskCanReadResolvedHistoryWithIsoDateRange() throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(get("/api/v1/front-desk/arrivals/work")
                        .param("group", "RESOLVED")
                        .param("resolvedFrom", "2026-11-01")
                        .param("resolvedTo", "2026-11-15")
                        .param("page", "1")
                        .header("Authorization", "Bearer test"))
                .andExpect(status().isOk());
        verify(work)
                .list(
                        ArrivalProgress.Group.RESOLVED,
                        null,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 15),
                        1,
                        20);
        mvc.perform(get("/api/v1/front-desk/arrivals/work")
                        .param("group", "RESOLVED")
                        .param("resolvedFrom", "01/11/2026")
                        .header("Authorization", "Bearer test"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void holdDecisionsRequireManagerAndValidatedBody() throws Exception {
        String body = "{\"requestedUntil\":\"2026-11-13T02:00:00Z\",\"approve\":false,\"note\":\"Không chấp thuận\"}";
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/arrival-hold/decision")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        authenticate("MANAGER");
        mvc.perform(post(BASE + "/arrival-hold/decision")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(service).decideHold(eq("BK-TEST"), any());
    }

    @Test
    void rejectsUnknownWorkFiltersAndIncompleteContactResults() throws Exception {
        authenticate("MANAGER");
        mvc.perform(get("/api/v1/front-desk/arrivals/work")
                        .param("group", "UNKNOWN")
                        .header("Authorization", "Bearer test"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/arrival-contact-result")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"PHONE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/arrival-hold/decision")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedUntil\":\"2026-11-13T02:00:00Z\",\"approve\":true,\"note\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, work);
    }

    @Test
    void blankContactAndNegativeFeesAreRejectedBeforeService() throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/no-show")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewToken\":\"s\",\"feeAmount\":-1,\"reason\":\"r\",\"contactNote\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void guestTokenIsPassedToOwnerValidationForAnonymousSettlement() throws Exception {
        mvc.perform(get(BASE + "/arrival-settlement").header("X-Booking-Access-Token", "guest-token"))
                .andExpect(status().isOk());
        verify(service).customerSettlement("BK-TEST", "guest-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.5", "\"800000\"", "10000000000"})
    void rejectsFractionalStringOrOutOfRangeCurrency(String amount) throws Exception {
        authenticate("MANAGER");
        mvc.perform(post(BASE + "/no-show")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("800000", amount)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[\"\"]", "[null]"})
    void scopeSelectionMustContainValidBookingCodes(String codes) throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(post(BASE + "/arrival-estimate")
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedAt\":\"2026-11-13T01:00:00Z\",\"bookingCodes\":" + codes + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void receptionCanSubmitExplicitCodesWithoutLosingTheScopeInJsonBinding() throws Exception {
        authenticate("RECEPTIONIST");
        mvc.perform(
                        post(BASE + "/arrival-estimate")
                                .header("Authorization", "Bearer test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"expectedAt\":\"2026-11-13T01:00:00Z\",\"bookingCodes\":[\"BK-TEST\",\"BK-SECOND\"]}"))
                .andExpect(status().isOk());
        verify(service).estimate(eq("BK-TEST"), argThat(r -> r.bookingCodes().equals(List.of("BK-TEST", "BK-SECOND"))));
    }

    private void authenticate(String role) {
        when(jwt.validate("test")).thenReturn(Optional.of(new ValidatedJwt("staff-id", List.of(role))));
    }
}
