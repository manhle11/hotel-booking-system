package com.hanbay.hotel.booking.controller;

import com.hanbay.hotel.booking.dto.request.CancelBookingRequest;
import com.hanbay.hotel.booking.dto.request.CreateBookingOrderRequest;
import com.hanbay.hotel.booking.dto.response.BookingOrderResponse;
import com.hanbay.hotel.booking.dto.response.CancellationEligibilityResponse;
import com.hanbay.hotel.booking.service.BookingOrderService;
import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.common.response.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/booking-orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Đơn đặt nhiều phòng")
public class BookingOrderController {
    private final BookingOrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingOrderResponse> create(
            @RequestBody @Valid CreateBookingOrderRequest request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{16,120}") String key,
            @RequestHeader("X-Booking-Access-Token") @Pattern(regexp = "[A-Za-z0-9_-]{32,120}") String token) {
        return ApiResponse.success("Đã giữ toàn bộ phòng trong đơn", service.create(request, key, token));
    }

    @GetMapping("/my-orders")
    public ApiResponse<PageResponse<BookingOrderResponse>> history(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lịch sử đơn đặt phòng", service.history(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingOrderResponse> get(
            @PathVariable UUID id, @RequestHeader(value = "X-Booking-Access-Token", required = false) String token) {
        return ApiResponse.success("Chi tiết đơn đặt phòng", service.get(id, token));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<BookingOrderResponse> claim(
            @PathVariable UUID id,
            @RequestHeader("X-Booking-Access-Token") @Pattern(regexp = "[A-Za-z0-9_-]{32,120}") String token) {
        return ApiResponse.success("Đã lưu đơn vào lịch sử tài khoản", service.claim(id, token));
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<BookingOrderResponse> payment(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token,
            @Valid @RequestBody(required = false)
                    com.hanbay.hotel.booking.dto.request.ArrivalHandlingRequest.Acceptance acceptance) {
        return ApiResponse.success(
                "Thông tin thanh toán toàn đơn",
                service.createPayment(id, token, acceptance == null ? null : acceptance.arrivalPolicyVersion()));
    }

    @GetMapping("/{id}/cancellation-eligibility")
    public ApiResponse<CancellationEligibilityResponse> eligibility(
            @PathVariable UUID id, @RequestHeader(value = "X-Booking-Access-Token", required = false) String token) {
        return ApiResponse.success("Điều kiện hủy toàn đơn", service.cancellationEligibility(id, token));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingOrderResponse> cancel(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token,
            @RequestBody @Valid CancelBookingRequest request) {
        return ApiResponse.success("Đã hủy toàn bộ phòng trong đơn", service.cancel(id, token, request));
    }

    @PostMapping("/{id}/refund-information")
    public ApiResponse<BookingOrderResponse> refundInformation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token,
            @RequestBody @Valid com.hanbay.hotel.payment.dto.request.RefundInformationRequest request) {
        return ApiResponse.success(
                "Đã lưu tài khoản nhận hoàn tiền", service.supplyRefundInformation(id, token, request));
    }
}
