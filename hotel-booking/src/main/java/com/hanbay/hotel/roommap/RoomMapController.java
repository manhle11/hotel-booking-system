package com.hanbay.hotel.roommap;

import com.hanbay.hotel.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix:/api/v1}")
@RequiredArgsConstructor
public class RoomMapController {
    private final RoomMapService service;

    @GetMapping("/hotel-map")
    public ApiResponse<RoomMapDto.PublicMap> publicMap() {
        return ApiResponse.success(service.publicMap());
    }

    @GetMapping("/front-desk/room-map")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public ResponseEntity<ApiResponse<RoomMapDto.StaffMap>> staffMap(@RequestParam(required = false) Integer floor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.staffMap(floor)));
    }

    @GetMapping("/management/floor-plans/{floorNumber}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<RoomMapDto.StaffFloor>> management(@PathVariable int floorNumber) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.managementFloor(floorNumber)));
    }

    @PutMapping("/management/floor-plans/{floorNumber}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<RoomMapDto.StaffFloor> publish(
            @PathVariable int floorNumber, @Valid @RequestBody RoomMapDto.WritePlan request) {
        return ApiResponse.success(service.publish(floorNumber, request));
    }
}
