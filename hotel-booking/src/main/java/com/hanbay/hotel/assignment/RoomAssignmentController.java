package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;

import com.hanbay.hotel.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("${api.prefix}/front-desk/room-assignments")
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
@RequiredArgsConstructor
public class RoomAssignmentController {
    private final RoomAssignmentService service;

    public record Suggest(@NotNull UUID roomTypeId, @NotNull LocalDate from, @NotNull LocalDate to) {}

    public record Apply(@NotBlank @Size(max = 64) String expectedVersion) {}

    public record Assign(
            UUID roomId,
            boolean locked,
            @NotBlank @Size(max = 64) String expectedVersion,
            @Size(max = 500) String reason,
            @NotNull LocalDate from,
            @NotNull LocalDate to) {}

    @GetMapping
    public ApiResponse<CalendarView> calendar(
            @RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) UUID roomTypeId) {
        return ApiResponse.success(service.calendar(from, to, roomTypeId));
    }

    @PostMapping("/plans")
    public ApiResponse<Plan> suggest(@Valid @RequestBody Suggest request) {
        return ApiResponse.success(service.suggest(request.roomTypeId(), request.from(), request.to()));
    }

    @PostMapping("/plans/{id}/apply")
    public ApiResponse<Plan> apply(@PathVariable UUID id, @Valid @RequestBody Apply request) {
        return ApiResponse.success(service.apply(id, request.expectedVersion()));
    }

    @PutMapping("/bookings/{id}")
    public ApiResponse<CalendarView> assign(@PathVariable UUID id, @Valid @RequestBody Assign request) {
        return ApiResponse.success(service.assign(
                id,
                request.roomId(),
                request.locked(),
                request.expectedVersion(),
                request.reason(),
                request.from(),
                request.to()));
    }

    @GetMapping("/history")
    public ApiResponse<List<History>> history(@RequestParam UUID roomTypeId) {
        return ApiResponse.success(service.history(roomTypeId));
    }

    @GetMapping("/plans/latest")
    public ApiResponse<Plan> latest(
            @RequestParam UUID roomTypeId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ApiResponse.success(service.latest(roomTypeId, from, to));
    }

    @GetMapping("/suggested-room")
    public ApiResponse<SuggestedRoom> suggestedRoom(@RequestParam @NotBlank @Size(max = 50) String bookingCode) {
        return ApiResponse.success(service.suggestedRoom(bookingCode));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<Plan> plan(@PathVariable UUID id) {
        return ApiResponse.success(service.plan(id));
    }
}
