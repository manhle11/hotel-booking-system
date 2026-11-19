package com.hanbay.hotel.frontdesk.controller;

import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import com.hanbay.hotel.common.response.*;
import com.hanbay.hotel.frontdesk.dto.response.ArrivalWorkItem;
import com.hanbay.hotel.frontdesk.service.ArrivalWorkService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/front-desk/arrivals")
public class ArrivalWorkController {
    private final ArrivalWorkService service;

    @GetMapping("/work")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    public ApiResponse<PageResponse<ArrivalWorkItem>> list(
            @RequestParam(defaultValue = "WAITING") ArrivalProgress.Group group,
            @RequestParam(required = false) ArrivalProgress.Reason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate resolvedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate resolvedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(group, reason, resolvedFrom, resolvedTo, page, size));
    }
}
