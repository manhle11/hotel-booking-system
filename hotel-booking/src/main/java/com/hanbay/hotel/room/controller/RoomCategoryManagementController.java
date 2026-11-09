package com.hanbay.hotel.room.controller;

import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.room.dto.RoomCategoryResponse;
import com.hanbay.hotel.room.dto.request.RoomCategoryWriteRequest;
import com.hanbay.hotel.room.service.RoomCategoryManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix:/api/v1}/management/room-categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class RoomCategoryManagementController {
    private final RoomCategoryManagementService service;

    @GetMapping
    public ApiResponse<List<RoomCategoryResponse>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    public ApiResponse<RoomCategoryResponse> create(@Valid @RequestBody RoomCategoryWriteRequest request) {
        return ApiResponse.success(service.save(null, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomCategoryResponse> update(
            @PathVariable UUID id, @Valid @RequestBody RoomCategoryWriteRequest request) {
        return ApiResponse.success(service.save(id, request));
    }
}
