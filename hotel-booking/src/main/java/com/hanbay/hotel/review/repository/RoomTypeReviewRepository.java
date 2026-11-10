package com.hanbay.hotel.review.repository;

import com.hanbay.hotel.review.domain.RoomTypeReview;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTypeReviewRepository
        extends JpaRepository<RoomTypeReview, UUID>, JpaSpecificationExecutor<RoomTypeReview>, RoomTypeReviewQueries {
    Optional<RoomTypeReview> findByBookingId(UUID bookingId);

    Optional<RoomTypeReview> findByOrderIdAndRoomTypeId(UUID orderId, UUID roomTypeId);

    @EntityGraph(attributePaths = "roomType")
    @Query("select r from RoomTypeReview r where r.deletedAt is null "
            + "and (:roomTypeId is null or r.roomType.id = :roomTypeId) "
            + "and (:rating is null or r.rating = :rating)")
    Page<RoomTypeReview> search(
            @Param("roomTypeId") UUID roomTypeId, @Param("rating") Integer rating, Pageable pageable);

    @EntityGraph(attributePaths = {"roomType", "booking", "order", "deletedBy", "repliedBy"})
    @Override
    Page<RoomTypeReview> findAll(Specification<RoomTypeReview> specification, Pageable pageable);

    @Query("select r.id as reviewId, b.bookingCode as bookingCode, room.roomNumber as roomNumber, "
            + "b.checkInDate as checkInDate, b.checkOutDate as checkOutDate, "
            + "b.checkedInAt as checkedInAt, b.checkedOutAt as checkedOutAt "
            + "from RoomTypeReview r join Booking b on "
            + "(r.order is null and b.id = r.booking.id) or "
            + "(r.order is not null and b.order.id = r.order.id and b.roomType.id = r.roomType.id) "
            + "left join b.assignedRoom room where r.id in :reviewIds "
            + "order by r.id, b.orderRoomNumber, b.bookingCode")
    List<ManagedStay> findManagedStays(@Param("reviewIds") Collection<UUID> reviewIds);

    @Query("select avg(r.rating) as averageRating, count(r) as totalReviews from RoomTypeReview r "
            + "where r.roomType.id = :roomTypeId and r.deletedAt is null")
    RatingSummary summarize(@Param("roomTypeId") UUID roomTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomTypeReview r where r.id = :id")
    Optional<RoomTypeReview> findByIdForUpdate(@Param("id") UUID id);

    interface RatingSummary {
        Double getAverageRating();

        long getTotalReviews();
    }

    interface ManagedStay {
        UUID getReviewId();

        String getBookingCode();

        String getRoomNumber();

        LocalDate getCheckInDate();

        LocalDate getCheckOutDate();

        Instant getCheckedInAt();

        Instant getCheckedOutAt();
    }
}
