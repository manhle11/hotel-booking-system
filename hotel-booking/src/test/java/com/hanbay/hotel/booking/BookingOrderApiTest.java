package com.hanbay.hotel.booking;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.authentication.service.impl.JwtService;
import com.hanbay.hotel.booking.controller.BookingOrderController;
import com.hanbay.hotel.booking.exception.BookingNotFoundException;
import com.hanbay.hotel.booking.service.BookingOrderService;
import com.hanbay.hotel.common.config.SecurityConfig;
import com.hanbay.hotel.common.security.JwtAccessDeniedHandler;
import com.hanbay.hotel.common.security.JwtAuthenticationEntryPoint;
import java.util.Collections;
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

@WebMvcTest(controllers = BookingOrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class BookingOrderApiTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    BookingOrderService service;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    AuthenticationRateLimitService rateLimits;

    @Test
    void anonymousGuestCanSubmitOneOrderWithTwoRoomsAndCheckoutHeaders() throws Exception {
        mvc.perform(post("/api/v1/booking-orders")
                        .header("Idempotency-Key", "test-request-key-123456")
                        .header("X-Booking-Access-Token", "private-order-access-token-at-least-32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(room() + "," + room())))
                .andExpect(status().isCreated());
        verify(service)
                .create(
                        argThat(request -> request.rooms().size() == 2),
                        eq("test-request-key-123456"),
                        eq("private-order-access-token-at-least-32"));
    }

    @Test
    void expiredAccessTokenCannotSilentlyCreateAnAnonymousOrder() throws Exception {
        when(jwtService.validate("expired-access-token")).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/booking-orders")
                        .header("Authorization", "Bearer expired-access-token")
                        .header("Idempotency-Key", "test-request-key-123456")
                        .header("X-Booking-Access-Token", "private-order-access-token-at-least-32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(room())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void refreshedAccessTokenAllowsCreatingAnAuthenticatedOrder() throws Exception {
        authenticate();
        mvc.perform(post("/api/v1/booking-orders")
                        .header("Authorization", "Bearer valid-customer")
                        .header("Idempotency-Key", "test-request-key-123456")
                        .header("X-Booking-Access-Token", "private-order-access-token-at-least-32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(room())))
                .andExpect(status().isCreated());
        verify(service).create(any(), eq("test-request-key-123456"), anyString());
    }

    @Test
    void claimRequiresAuthenticationAndAnOrderAccessToken() throws Exception {
        UUID id = UUID.randomUUID();
        String token = "private-order-access-token-at-least-32";
        mvc.perform(post("/api/v1/booking-orders/{id}/claim", id).header("X-Booking-Access-Token", token))
                .andExpect(status().isUnauthorized());
        authenticate();
        mvc.perform(post("/api/v1/booking-orders/{id}/claim", id).header("Authorization", "Bearer valid-customer"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(post("/api/v1/booking-orders/{id}/claim", id)
                        .header("Authorization", "Bearer valid-customer")
                        .header("X-Booking-Access-Token", token))
                .andExpect(status().isOk());
        verify(service).claim(id, token);
    }

    private void authenticate() {
        when(jwtService.validate("valid-customer"))
                .thenReturn(Optional.of(
                        new JwtService.ValidatedJwt(UUID.randomUUID().toString(), List.of())));
    }

    @Test
    void historyRequiresAuthenticationWhileOrderDetailsRequireServiceAuthorization() throws Exception {
        mvc.perform(get("/api/v1/booking-orders/my-orders")).andExpect(status().isUnauthorized());
        when(service.get(any(), nullable(String.class))).thenThrow(new BookingNotFoundException("Không tìm thấy đơn"));
        mvc.perform(get("/api/v1/booking-orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyOrdersAndMoreThanTenRoomsBeforeCallingTheService() throws Exception {
        for (String rooms : new String[] {"", String.join(",", Collections.nCopies(11, room()))}) {
            mvc.perform(post("/api/v1/booking-orders")
                            .header("Idempotency-Key", "test-request-key-123456")
                            .header("X-Booking-Access-Token", "private-order-access-token-at-least-32")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(rooms)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidOccupancyAndMissingCheckoutHeaders() throws Exception {
        mvc.perform(post("/api/v1/booking-orders")
                        .header("Idempotency-Key", "test-request-key-123456")
                        .header("X-Booking-Access-Token", "private-order-access-token-at-least-32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(room().replace("\"adults\":1", "\"adults\":0"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/booking-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(room())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private String body(String rooms) {
        return """
                {"checkInDate":"2026-11-10","checkOutDate":"2026-11-12",
                 "guestInfo":{"fullName":"Test guest","email":"guest@example.test","identityNumber":"TEST"},
                 "rooms":[%s]}
                """
                .formatted(rooms);
    }

    private String room() {
        return """
                {"roomTypeId":"11111111-1111-4111-8111-111111111111","adults":1,"children":0,"addOns":[]}
                """
                .trim();
    }
}
