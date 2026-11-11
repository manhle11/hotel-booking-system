package com.hanbay.hotel.review.service;

import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingOrder;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.exception.BookingNotFoundException;
import com.hanbay.hotel.booking.repository.BookingOrderRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.review.domain.RoomTypeReview;
import com.hanbay.hotel.review.dto.*;
import com.hanbay.hotel.review.exception.ReviewException;
import com.hanbay.hotel.review.repository.ManagedReviewSpecifications;
import com.hanbay.hotel.review.repository.RoomTypeReviewRepository;
import com.hanbay.hotel.room.domain.RoomTypeStatus;
import com.hanbay.hotel.room.exception.RoomTypeNotFoundException;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomTypeReviewService {
    private final RoomTypeReviewRepository reviews;
    private final BookingRepository bookings;
    private final BookingOrderRepository orders;
    private final RoomTypeRepository roomTypes;
    private final UserRepository users;
    private final CurrentUserProvider currentUser;
    private final Clock clock;
    private final HotelStayProperties stayProperties;

    public PageResponse<ReviewResponse> publicReviews(UUID roomTypeId, Integer rating, int page, int size) {
        requireActiveRoomType(roomTypeId);
        return search(roomTypeId, rating, page, size);
    }

    public ReviewSummaryResponse summary(UUID roomTypeId) {
        requireActiveRoomType(roomTypeId);
        var summary = reviews.summarize(roomTypeId);
        Double average = summary.getAverageRating();
        return new ReviewSummaryResponse(
                average == null ? null : Math.round(average * 10.0) / 10.0, summary.getTotalReviews());
    }

    @PreAuthorize("hasRole('MANAGER')")
    public PageResponse<ManagedReviewResponse> manage(
            UUID roomTypeId, Integer rating, boolean deleted, int page, int size) {
        return manage(new ManagedReviewFilter(roomTypeId, rating, deleted, null, null, null, null), page, size);
    }

    @PreAuthorize("hasRole('MANAGER')")
    public PageResponse<ManagedReviewResponse> manage(ManagedReviewFilter filter, int page, int size) {
        validateSearch(filter.rating(), page, size);
        Sort sort = Sort.by(Sort.Direction.DESC, filter.deleted() ? "deletedAt" : "createdAt", "id");
        var specification = ManagedReviewSpecifications.matching(filter, stayProperties.zoneId());
        var result = reviews.findAll(specification, PageRequest.of(page, size, sort));
        if (page > 0 && result.isEmpty()) {
            int last = Math.max(0, result.getTotalPages() - 1);
            result = reviews.findAll(specification, PageRequest.of(last, size, sort));
        }
        Map<UUID, List<ReviewStayResponse>> stays = result.isEmpty()
                ? Map.of()
                : reviews
                        .findManagedStays(
                                result.stream().map(RoomTypeReview::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                RoomTypeReviewRepository.ManagedStay::getReviewId,
                                Collectors.mapping(
                                        stay -> new ReviewStayResponse(
                                                stay.getBookingCode(),
                                                stay.getRoomNumber(),
                                                stay.getCheckInDate(),
                                                stay.getCheckOutDate(),
                                                stay.getCheckedInAt(),
                                                stay.getCheckedOutAt()),
                                        Collectors.toList())));
        return PageResponse.from(
                result.map(review -> toManagedResponse(review, stays.getOrDefault(review.getId(), List.of()))));
    }

    @PreAuthorize("hasRole('MANAGER')")
    public ManagedReviewSummaryResponse managedSummary(ManagedReviewFilter filter) {
        return reviews.summarizeManaged(ManagedReviewSpecifications.matching(filter, stayProperties.zoneId()));
    }

    public ReviewEligibilityResponse eligibility(UUID bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow(RoomTypeReviewService::bookingNotFound);
        ensureOwner(booking);
        return eligibility(booking);
    }

    @Transactional
    public ReviewResponse create(UUID bookingId, CreateReviewRequest request) {
        Booking candidate = bookings.findById(bookingId).orElseThrow(RoomTypeReviewService::bookingNotFound);
        ensureOwner(candidate);
        // Serialize reviews of different bookings in the same order before checking per-type uniqueness.
        if (candidate.getOrder() != null) {
            orders.findByIdForUpdate(candidate.getOrder().getId()).orElseThrow(RoomTypeReviewService::bookingNotFound);
        }
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow(RoomTypeReviewService::bookingNotFound);
        ensureOwner(booking);
        ReviewEligibilityResponse eligibility = eligibility(booking);
        if (!eligibility.canReview()) {
            throw new ReviewException(eligibility.reasonCode(), eligibility.message(), HttpStatus.CONFLICT);
        }
        User owner = booking.getOrder() == null
                ? booking.getBookedBy()
                : booking.getOrder().getBookedBy();
        String name = owner.getFullName();
        String comment = request.comment() == null ? null : request.comment().strip();
        RoomTypeReview review = reviews.saveAndFlush(RoomTypeReview.builder()
                .booking(booking)
                .order(booking.getOrder())
                .roomType(booking.getRoomType())
                .reviewer(owner)
                .reviewerName(name == null || name.isBlank() ? "Khách" : name)
                .rating(request.rating())
                .comment(comment == null || comment.isEmpty() ? null : comment)
                .build());
        return toResponse(review);
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public void delete(UUID reviewId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new ReviewException(
                    "REVIEW_DELETE_REASON_REQUIRED",
                    "Vui lòng nhập lý do xóa (tối đa 500 ký tự)",
                    HttpStatus.BAD_REQUEST);
        RoomTypeReview review = reviews.findByIdForUpdate(reviewId)
                .orElseThrow(
                        () -> new ReviewException("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá", HttpStatus.NOT_FOUND));
        UUID managerId = currentUser
                .currentUserId()
                .orElseThrow(() ->
                        new ReviewException("REVIEW_FORBIDDEN", "Không có quyền xóa đánh giá", HttpStatus.FORBIDDEN));
        review.delete(users.getReferenceById(managerId), clock.instant(), reason.strip());
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public ReviewResponse reply(UUID reviewId, String reply) {
        if (reply == null || reply.isBlank() || reply.length() > 1000)
            throw new ReviewException(
                    "REVIEW_REPLY_INVALID", "Phản hồi phải có từ 1 đến 1.000 ký tự", HttpStatus.BAD_REQUEST);
        RoomTypeReview review = editableReview(reviewId);
        UUID manager = currentUser.currentUserId().orElseThrow(RoomTypeReviewService::bookingNotFound);
        review.reply(users.getReferenceById(manager), clock.instant(), reply.strip());
        return toResponse(review);
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public void removeReply(UUID reviewId) {
        editableReview(reviewId).removeReply();
    }

    private RoomTypeReview editableReview(UUID id) {
        RoomTypeReview review = reviews.findByIdForUpdate(id)
                .orElseThrow(
                        () -> new ReviewException("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá", HttpStatus.NOT_FOUND));
        if (review.getDeletedAt() != null)
            throw new ReviewException("REVIEW_REMOVED", "Không thể phản hồi đánh giá đã xóa", HttpStatus.CONFLICT);
        return review;
    }

    private ReviewEligibilityResponse eligibility(Booking booking) {
        Instant checkedOutAt = booking.getOrder() == null
                ? booking.getCheckedOutAt()
                : bookings.findLatestCheckOutForReview(
                        booking.getOrder().getId(), booking.getRoomType().getId());
        Instant deadline = checkedOutAt == null ? null : checkedOutAt.plus(Duration.ofDays(30));
        Optional<RoomTypeReview> existing = booking.getOrder() == null
                ? reviews.findByBookingId(booking.getId())
                : reviews.findByOrderIdAndRoomTypeId(
                        booking.getOrder().getId(), booking.getRoomType().getId());
        if (existing.isPresent()) {
            RoomTypeReview review = existing.get();
            return review.getDeletedAt() == null
                    ? new ReviewEligibilityResponse(
                            false,
                            "REVIEW_ALREADY_EXISTS",
                            "Bạn đã đánh giá hạng phòng này trong lần lưu trú này.",
                            deadline,
                            toResponse(review))
                    : new ReviewEligibilityResponse(
                            false,
                            "REVIEW_REMOVED",
                            "Đánh giá của lần lưu trú này đã được khách sạn xóa.",
                            deadline,
                            null);
        }
        if (booking.getStatus() != BookingStatus.CHECKED_OUT || deadline == null) {
            return new ReviewEligibilityResponse(
                    false, "REVIEW_STAY_NOT_COMPLETED", "Bạn có thể đánh giá sau khi trả phòng.", deadline, null);
        }
        if (clock.instant().isAfter(deadline)) {
            return new ReviewEligibilityResponse(
                    false,
                    "REVIEW_WINDOW_EXPIRED",
                    "Đã hết thời hạn đánh giá 30 ngày sau khi trả phòng.",
                    deadline,
                    null);
        }
        return new ReviewEligibilityResponse(
                true, null, "Bạn có thể chia sẻ trải nghiệm về hạng phòng này.", deadline, null);
    }

    private void ensureOwner(Booking booking) {
        UUID userId = currentUser
                .currentUserId()
                .orElseThrow(() -> new ReviewException(
                        "REVIEW_LOGIN_REQUIRED",
                        "Vui lòng đăng nhập để đánh giá lần lưu trú của bạn",
                        HttpStatus.UNAUTHORIZED));
        BookingOrder order = booking.getOrder();
        User owner = order == null ? booking.getBookedBy() : order.getBookedBy();
        if (owner == null || !owner.getId().equals(userId)) throw bookingNotFound();
    }

    private PageResponse<ReviewResponse> search(UUID roomTypeId, Integer rating, int page, int size) {
        validateSearch(rating, page, size);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var result = reviews.search(roomTypeId, rating, PageRequest.of(page, size, sort));
        if (page > 0 && result.isEmpty()) {
            int last = Math.max(0, result.getTotalPages() - 1);
            result = reviews.search(roomTypeId, rating, PageRequest.of(last, size, sort));
        }
        return PageResponse.from(result.map(this::toResponse));
    }

    private void validateSearch(Integer rating, int page, int size) {
        PaginationValidator.validate(page, size, 100);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new ReviewException("REVIEW_INVALID_RATING", "Số sao phải từ 1 đến 5", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireActiveRoomType(UUID id) {
        roomTypes
                .findById(id)
                .filter(type -> type.getStatus() == RoomTypeStatus.ACTIVE)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng đang hoạt động"));
    }

    private ReviewResponse toResponse(RoomTypeReview review) {
        String[] parts = review.getReviewerName().strip().split("\\s+");
        String maskedName = parts.length > 1 ? parts[0] + " ***" : parts[0].substring(0, 1) + "***";
        return new ReviewResponse(
                review.getId(),
                review.getRoomType().getId(),
                review.getRoomType().getName(),
                maskedName,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getHotelReply(),
                review.getRepliedAt());
    }

    private ManagedReviewResponse toManagedResponse(RoomTypeReview review, List<ReviewStayResponse> stays) {
        Booking booking = review.getBooking();
        return new ManagedReviewResponse(
                toResponse(review),
                booking.getBookingCode(),
                review.getOrder() == null ? null : review.getOrder().getOrderCode(),
                review.getReviewerName(),
                stays,
                review.getDeletedAt(),
                review.getDeletedBy() == null ? null : review.getDeletedBy().getFullName(),
                review.getDeleteReason(),
                review.getRepliedBy() == null ? null : review.getRepliedBy().getFullName());
    }

    private static BookingNotFoundException bookingNotFound() {
        return new BookingNotFoundException("Không tìm thấy đặt phòng hoặc bạn không có quyền truy cập");
    }
}
