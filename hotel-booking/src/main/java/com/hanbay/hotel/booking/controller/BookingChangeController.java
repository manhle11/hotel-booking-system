package com.hanbay.hotel.booking.controller;

import com.hanbay.hotel.booking.domain.BookingChangeRequest;
import com.hanbay.hotel.booking.dto.request.BookingChangeCommand;
import com.hanbay.hotel.booking.dto.response.BookingChangeResponse;
import com.hanbay.hotel.booking.service.BookingChangeService;
import com.hanbay.hotel.common.response.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
public class BookingChangeController {
    private final BookingChangeService service;

    @PostMapping("/bookings/{code}/customer-change-requests")
    public ApiResponse<BookingChangeResponse> customerCreate(
            @PathVariable String code,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token,
            @Valid @RequestBody BookingChangeCommand.Create command) {
        return ApiResponse.success(service.customerCreate(code, token, command));
    }

    @GetMapping("/bookings/{code}/customer-change-requests")
    public ApiResponse<List<BookingChangeResponse>> customerHistory(
            @PathVariable String code,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token) {
        return ApiResponse.success(service.customerHistory(code, token));
    }

    @GetMapping("/bookings/{code}/customer-change-requests/{id}/quote")
    public ApiResponse<BookingChangeResponse.Quote> customerQuote(
            @PathVariable String code,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token) {
        return ApiResponse.success(service.customerQuote(code, token, id));
    }

    public record Consent(@jakarta.validation.constraints.NotBlank String quoteToken) {}

    @PostMapping("/bookings/{code}/customer-change-requests/{id}/consent")
    public ApiResponse<BookingChangeResponse> consent(
            @PathVariable String code,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token,
            @Valid @RequestBody Consent command) {
        return ApiResponse.success(service.customerConsent(code, token, id, command.quoteToken()));
    }

    @GetMapping("/bookings/{code}/cancellation-quote")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    public ApiResponse<BookingChangeResponse.Quote> cancellationQuote(@PathVariable String code) {
        return ApiResponse.success(service.cancellationQuote(code));
    }

    @PostMapping("/bookings/{code}/change-requests")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    public ApiResponse<BookingChangeResponse> create(
            @PathVariable String code, @Valid @RequestBody BookingChangeCommand.Create command) {
        return ApiResponse.success(
                "Đã xử lý xác nhận hủy hoặc ghi nhận đề nghị đổi của khách", service.create(code, command));
    }

    @GetMapping("/bookings/{code}/change-requests")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    public ApiResponse<List<BookingChangeResponse>> history(@PathVariable String code) {
        return ApiResponse.success(service.history(code));
    }

    @GetMapping("/front-desk/booking-change-requests")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    public ApiResponse<PageResponse<BookingChangeResponse>> list(
            @RequestParam(required = false) BookingChangeRequest.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(status, page, size));
    }

    @GetMapping("/front-desk/booking-change-requests/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    public ApiResponse<BookingChangeResponse.Quote> quote(@PathVariable UUID id) {
        return ApiResponse.success(service.quote(id));
    }

    @PostMapping("/front-desk/booking-change-requests/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<BookingChangeResponse> approve(
            @PathVariable UUID id, @Valid @RequestBody BookingChangeCommand.Approve command) {
        return ApiResponse.success("Đã duyệt và áp dụng yêu cầu", service.approve(id, command));
    }

    public record Services(
            boolean dropAll,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String note) {}

    @PostMapping("/front-desk/booking-change-requests/{id}/exception")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<BookingChangeResponse.Quote> exception(
            @PathVariable UUID id, @Valid @RequestBody BookingChangeCommand.Reject command) {
        return ApiResponse.success(service.allowException(id, command.note().trim()));
    }

    @PostMapping("/front-desk/booking-change-requests/{id}/services")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<BookingChangeResponse.Quote> services(
            @PathVariable UUID id, @Valid @RequestBody Services command) {
        return ApiResponse.success(
                service.changeServices(id, command.dropAll(), command.note().trim()));
    }

    @PostMapping("/front-desk/booking-change-requests/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<BookingChangeResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody BookingChangeCommand.Reject command) {
        return ApiResponse.success("Đã từ chối yêu cầu; giữ nguyên booking", service.reject(id, command));
    }
}
