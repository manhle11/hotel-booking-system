package com.hanbay.hotel.review.controller;

import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.review.dto.*;
import com.hanbay.hotel.review.service.RoomTypeReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix:/api/v1}")
@RequiredArgsConstructor
@Tag(name = "Đánh giá hạng phòng")
public class RoomTypeReviewController {
    private final RoomTypeReviewService service;

    @GetMapping("/room-types/{id}/reviews")
    @Operation(summary = "Xem đánh giá công khai của hạng phòng")
    public ApiResponse<PageResponse<ReviewResponse>> list(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        return ApiResponse.success(service.publicReviews(id, rating, page, size));
    }

    @GetMapping("/room-types/{id}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> summary(@PathVariable UUID id) {
        return ApiResponse.success(service.summary(id));
    }

    @GetMapping("/bookings/{bookingId}/review-eligibility")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiểm tra quyền đánh giá của tài khoản sở hữu đặt phòng")
    public ApiResponse<ReviewEligibilityResponse> eligibility(@PathVariable UUID bookingId) {
        return ApiResponse.success(service.eligibility(bookingId));
    }

    @PostMapping("/bookings/{bookingId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tài khoản sở hữu đánh giá hạng phòng trong 30 ngày sau khi trả phòng")
    public ApiResponse<ReviewResponse> create(
            @PathVariable UUID bookingId, @RequestBody @Valid CreateReviewRequest request) {
        return ApiResponse.success("Đã gửi đánh giá", service.create(bookingId, request));
    }

    @GetMapping("/management/reviews")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<PageResponse<ManagedReviewResponse>> manage(
            @RequestParam(required = false) UUID roomTypeId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(required = false) Boolean replied,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.manage(
                new ManagedReviewFilter(roomTypeId, rating, deleted, replied, search, fromDate, toDate), page, size));
    }

    @GetMapping("/management/reviews/summary")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Thống kê toàn bộ đánh giá theo cùng bộ lọc của danh sách")
    public ApiResponse<ManagedReviewSummaryResponse> managedSummary(
            @RequestParam(required = false) UUID roomTypeId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(required = false) Boolean replied,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(service.managedSummary(
                new ManagedReviewFilter(roomTypeId, rating, deleted, replied, search, fromDate, toDate)));
    }

    @DeleteMapping("/management/reviews/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Manager xóa đánh giá khỏi danh sách công khai và điểm trung bình")
    public void delete(@PathVariable UUID id, @Valid @RequestBody DeleteReviewRequest request) {
        service.delete(id, request.reason());
    }

    @PutMapping("/management/reviews/{id}/reply")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ReviewResponse> reply(@PathVariable UUID id, @Valid @RequestBody ReplyReviewRequest request) {
        return ApiResponse.success(service.reply(id, request.reply()));
    }

    @DeleteMapping("/management/reviews/{id}/reply")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReply(@PathVariable UUID id) {
        service.removeReply(id);
    }
}
