package com.hanbay.hotel.booking.controller;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.common.response.ApiResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix:/api/v1}/stay-policy")
@RequiredArgsConstructor
public class StayPolicyController {
    private final HotelStayPolicy policy;

    @Value("${payment.expiration-minutes:15}")
    private int paymentHoldMinutes;

    @GetMapping
    public ApiResponse<StayPolicyResponse> getPolicy() {
        var settings = policy.getProperties();
        var now = policy.now();
        return ApiResponse.success(
                "Chính sách thời gian lưu trú",
                new StayPolicyResponse(
                        settings.getZone(),
                        settings.getCheckInTime(),
                        settings.getCheckOutTime(),
                        settings.getSameDayBookingCutoff(),
                        settings.getLateArrivalCutoff(),
                        paymentHoldMinutes,
                        now.toLocalDate(),
                        now.toInstant()));
    }

    public record StayPolicyResponse(
            String zone,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            LocalTime sameDayBookingCutoff,
            LocalTime lateArrivalCutoff,
            int paymentHoldMinutes,
            LocalDate hotelToday,
            Instant serverNow) {}
}
