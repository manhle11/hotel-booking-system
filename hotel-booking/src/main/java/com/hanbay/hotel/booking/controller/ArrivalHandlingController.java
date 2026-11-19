package com.hanbay.hotel.booking.controller;

import com.hanbay.hotel.booking.dto.request.ArrivalHandlingRequest;
import com.hanbay.hotel.booking.dto.response.ArrivalHandlingResponse;
import com.hanbay.hotel.booking.service.ArrivalHandlingService;
import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.payment.dto.request.RefundInformationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/bookings/{bookingCode}")
@RequiredArgsConstructor
public class ArrivalHandlingController {
    private final ArrivalHandlingService service;

    @GetMapping("/arrival-handling")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> preview(@PathVariable String bookingCode) {
        return ApiResponse.success(service.preview(bookingCode));
    }

    @PostMapping("/arrival-contact")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> contact(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.Contact request) {
        return ApiResponse.success(service.contact(bookingCode, request));
    }

    @PostMapping("/arrival-hold")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> hold(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.Hold request) {
        return ApiResponse.success(service.hold(bookingCode, request));
    }

    @PostMapping("/arrival-estimate")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> estimate(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.Estimate request) {
        return ApiResponse.success(service.estimate(bookingCode, request));
    }

    @PostMapping("/arrival-contact-result")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> contactResult(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.StructuredContact request) {
        return ApiResponse.success(service.structuredContact(bookingCode, request));
    }

    @PostMapping("/arrival-hold/decision")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> decideHold(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.HoldDecision request) {
        return ApiResponse.success(service.decideHold(bookingCode, request));
    }

    @PostMapping("/no-show")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> noShow(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.NoShow request) {
        return ApiResponse.success(service.markNoShow(bookingCode, request));
    }

    @PostMapping("/arrival-hold/release")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> releaseHold(
            @PathVariable String bookingCode, @Valid @RequestBody ArrivalHandlingRequest.Contact request) {
        return ApiResponse.success(service.releaseHold(bookingCode, request));
    }

    public record FeeReduction(
            @tools.jackson.databind.annotation.JsonDeserialize(
                            using = com.hanbay.hotel.booking.dto.request.CheckOutAmountDeserializer.class)
                    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long feeAmount,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {}

    @PostMapping("/policy-fee-reduction")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> reduceFee(
            @PathVariable String bookingCode, @Valid @RequestBody FeeReduction command) {
        return ApiResponse.success(service.reduceFee(
                bookingCode, command.feeAmount(), command.reason().trim()));
    }

    public record FeeCollection(
            @jakarta.validation.constraints.NotNull java.util.UUID requestId,
            @tools.jackson.databind.annotation.JsonDeserialize(
                            using = com.hanbay.hotel.booking.dto.request.CheckOutAmountDeserializer.class)
                    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive @jakarta.validation.constraints.Max(9_999_999_999L) Long amount,
            @tools.jackson.databind.annotation.JsonDeserialize(
                            using = com.hanbay.hotel.booking.dto.request.CheckOutAmountDeserializer.class)
                    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedOutstandingAmount,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String note) {}

    @PostMapping("/policy-fee-collection")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ApiResponse<ArrivalHandlingResponse> collectFee(
            @PathVariable String bookingCode, @Valid @RequestBody FeeCollection command) {
        return ApiResponse.success(service.collectFee(
                bookingCode,
                command.requestId(),
                command.amount(),
                command.expectedOutstandingAmount(),
                command.note().trim()));
    }

    @GetMapping("/booking-policy")
    public ApiResponse<ArrivalHandlingService.PolicySummary> policy(
            @PathVariable String bookingCode,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token) {
        return ApiResponse.success(service.customerPolicy(bookingCode, token));
    }

    @GetMapping("/arrival-settlement")
    public ApiResponse<ArrivalHandlingResponse> settlement(
            @PathVariable String bookingCode,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token) {
        return ApiResponse.success(service.customerSettlement(bookingCode, token));
    }

    @PostMapping("/arrival-refund-information")
    public ApiResponse<ArrivalHandlingResponse> refundInformation(
            @PathVariable String bookingCode,
            @RequestHeader(value = "X-Booking-Access-Token", required = false) String token,
            @Valid @RequestBody RefundInformationRequest request) {
        return ApiResponse.success(service.supplyRefundInformation(bookingCode, token, request));
    }
}
