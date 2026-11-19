package com.hanbay.hotel.payment.controller;

import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.domain.WebhookProcessingStatus;
import com.hanbay.hotel.payment.dto.request.ConfirmRefundRequest;
import com.hanbay.hotel.payment.dto.response.MonitoredPaymentResponse;
import com.hanbay.hotel.payment.dto.response.MonitoredWebhookEventResponse;
import com.hanbay.hotel.payment.dto.response.PaymentMonitoringSummaryResponse;
import com.hanbay.hotel.payment.dto.response.RefundRequestResponse;
import com.hanbay.hotel.payment.dto.response.WebhookEventDetailResponse;
import com.hanbay.hotel.payment.service.PaymentMonitoringService;
import com.hanbay.hotel.payment.service.RefundManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/payment-monitoring")
@RequiredArgsConstructor
@Tag(name = "Giám sát thanh toán", description = "API giám sát thanh toán và webhook dành cho quản trị")
public class PaymentMonitoringController {

    private final PaymentMonitoringService paymentMonitoringService;
    private final RefundManagementService refundManagementService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Lấy số liệu tổng quan thanh toán và webhook")
    public ApiResponse<PaymentMonitoringSummaryResponse> getSummary() {
        return ApiResponse.success(paymentMonitoringService.getSummary());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Tra cứu danh sách payment")
    public ApiResponse<PageResponse<MonitoredPaymentResponse>> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String sort) {
        return ApiResponse.success(
                sort == null
                        ? paymentMonitoringService.getPayments(page, size, keyword, status)
                        : paymentMonitoringService.getPayments(page, size, keyword, status, sort));
    }

    @GetMapping("/webhook-events")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Tra cứu danh sách webhook event không bao gồm raw payload")
    public ApiResponse<PageResponse<MonitoredWebhookEventResponse>> getWebhookEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) WebhookProcessingStatus status,
            @RequestParam(required = false) String sort) {
        return ApiResponse.success(
                sort == null
                        ? paymentMonitoringService.getWebhookEvents(page, size, keyword, status)
                        : paymentMonitoringService.getWebhookEvents(page, size, keyword, status, sort));
    }

    @GetMapping("/webhook-events/{eventId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xem chi tiết và raw payload của webhook event")
    public ApiResponse<WebhookEventDetailResponse> getWebhookEvent(@PathVariable UUID eventId) {
        return ApiResponse.success(paymentMonitoringService.getWebhookEvent(eventId));
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Lấy danh sách yêu cầu hoàn tiền đang chờ")
    public ApiResponse<PageResponse<RefundRequestResponse>> getPendingRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.success(
                sort == null
                        ? refundManagementService.getPendingRefunds(page, size)
                        : refundManagementService.getPendingRefunds(page, size, sort));
    }

    @org.springframework.web.bind.annotation.PostMapping("/refunds/{refundId}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xác nhận đã hoàn tiền cho khách")
    public ApiResponse<RefundRequestResponse> confirmRefund(
            @PathVariable UUID refundId, @RequestBody @Valid ConfirmRefundRequest request) {
        return ApiResponse.success(
                "Xác nhận hoàn tiền thành công", refundManagementService.confirmRefund(refundId, request));
    }

    @org.springframework.web.bind.annotation.PostMapping("/refunds/{refundId}/destination")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<RefundRequestResponse> supplyDestination(
            @PathVariable UUID refundId,
            @RequestBody @Valid com.hanbay.hotel.payment.dto.request.RefundInformationRequest request) {
        return ApiResponse.success(
                "Đã bổ sung tài khoản nhận hoàn", refundManagementService.supplyDestination(refundId, request));
    }
}
