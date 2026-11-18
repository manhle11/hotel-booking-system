package com.hanbay.hotel.booking.controller;

import com.hanbay.hotel.booking.dto.response.BookingChangeSettlementResponse;
import com.hanbay.hotel.booking.service.BookingChangeSettlementService;
import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.common.response.PageResponse;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/front-desk/booking-change-requests")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
public class BookingChangeSettlementController {
    private final BookingChangeSettlementService service;

    @GetMapping("/settlements")
    public ApiResponse<PageResponse<BookingChangeSettlementResponse>> list(
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(keyword, page, size));
    }

    @GetMapping("/{id}/settlement")
    public ApiResponse<BookingChangeSettlementResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }
}
