package com.hanbay.hotel.review;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import com.hanbay.hotel.review.controller.RoomTypeReviewController;
import com.hanbay.hotel.review.dto.ManagedReviewFilter;
import com.hanbay.hotel.review.dto.ManagedReviewSummaryResponse;
import com.hanbay.hotel.review.dto.ReviewSummaryResponse;
import com.hanbay.hotel.review.service.RoomTypeReviewService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomTypeReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(
        properties = {"app.cors.allowed-origins=http://localhost:5173", "debug=false", "logging.level.root=WARN"})
class RoomTypeReviewSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    RoomTypeReviewService service;

    private final UUID id = UUID.randomUUID();

    @Test
    void publicCanReadReviewsAndSummary() throws Exception {
        when(service.publicReviews(id, null, 0, 6)).thenReturn(new PageResponse<>(List.of(), 0, 6, 0, 0, true, true));
        when(service.summary(id)).thenReturn(new ReviewSummaryResponse(null, 0));
        mvc.perform(get("/api/v1/room-types/{id}/reviews", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/room-types/{id}/reviews/summary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(0));
    }

    @Test
    @WithMockUser
    void invalidReviewBodyIsRejectedBeforeService() throws Exception {
        for (String body : List.of(
                "{\"rating\":1.5}",
                "{\"rating\":\"5\"}",
                "{}",
                "{\"rating\":0}",
                "{\"rating\":6}",
                "{\"rating\":5,\"comment\":\"" + "x".repeat(1001) + "\"}")) {
            mvc.perform(post("/api/v1/bookings/{id}/reviews", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCannotManageOrDelete() throws Exception {
        mvc.perform(get("/api/v1/management/reviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/management/reviews/summary")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/management/reviews/{id}", id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void customerCannotManageOrDelete() throws Exception {
        forbidden();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotManageOrDelete() throws Exception {
        forbidden();
    }

    @Test
    @WithMockUser(roles = "HOUSEKEEPER")
    void housekeeperCannotManageOrDelete() throws Exception {
        forbidden();
    }

    @Test
    @WithMockUser(roles = {"ADMIN", "MANAGER"})
    void accountAdminCannotUseOperationalManagement() throws Exception {
        forbidden();
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCanFilterAndDelete() throws Exception {
        mvc.perform(get("/api/v1/management/reviews")
                        .param("roomTypeId", id.toString())
                        .param("rating", "1"))
                .andExpect(status().isOk());
        verify(service).manage(new ManagedReviewFilter(id, 1, false, null, null, null, null), 0, 20);
        mvc.perform(delete("/api/v1/management/reviews/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isNoContent());
        verify(service).delete(id, "Spam");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerListAndSummaryAcceptTheSameFilters() throws Exception {
        var filter = new ManagedReviewFilter(
                id, 2, true, false, "Nguyễn", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9));
        when(service.managedSummary(filter)).thenReturn(new ManagedReviewSummaryResponse(30, 30, 0, 30));
        for (String path : List.of("/api/v1/management/reviews", "/api/v1/management/reviews/summary")) {
            mvc.perform(get(path)
                            .param("roomTypeId", id.toString())
                            .param("rating", "2")
                            .param("deleted", "true")
                            .param("replied", "false")
                            .param("search", " Nguyễn ")
                            .param("fromDate", "2026-11-01")
                            .param("toDate", "2026-11-09"))
                    .andExpect(status().isOk());
        }
        verify(service).manage(filter, 0, 20);
        verify(service).managedSummary(filter);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void invalidFiltersAreRejectedForListAndSummaryBeforeService() throws Exception {
        for (String path : List.of("/api/v1/management/reviews", "/api/v1/management/reviews/summary")) {
            mvc.perform(get(path).param("fromDate", "2026-11-10").param("toDate", "2026-11-09"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(path).param("fromDate", "not-a-date")).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("search", "x".repeat(101))).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("replied", "invalid")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCannotUseEitherBookingTokenOrRetiredReviewToken() throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/reviews", id)
                        .header("X-Booking-Access-Token", "previous-valid-token")
                        .header("X-Review-Access-Token", "retired-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/{id}/review-eligibility", id)
                        .header("X-Booking-Access-Token", "previous-valid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void signedInCustomerCanReachOwnershipValidation() throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/reviews", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isCreated());
        verify(service).create(eq(id), any());
        mvc.perform(get("/api/v1/bookings/{id}/review-eligibility", id)).andExpect(status().isOk());
        verify(service).eligibility(id);
    }

    private void forbidden() throws Exception {
        mvc.perform(get("/api/v1/management/reviews")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/management/reviews/summary")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/management/reviews/{id}", id)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
