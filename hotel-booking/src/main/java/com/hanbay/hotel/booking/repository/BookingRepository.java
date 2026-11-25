package com.hanbay.hotel.booking.repository;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("select b.roomType.id from Booking b where b.bookingCode = :code")
    Optional<UUID> findRoomTypeIdByBookingCode(@Param("code") String code);

    @Query("select b.order.id from Booking b where b.bookingCode = :code")
    Optional<UUID> findOrderIdForArrival(@Param("code") String code);

    @Query("select b.order.id from Booking b where b.id = :bookingId")
    Optional<UUID> findOrderIdByBookingId(@Param("bookingId") UUID bookingId);

    boolean existsByAssignedRoomId(UUID roomId);

    @Query("select max(b.checkedOutAt) from Booking b where b.order.id = :orderId "
            + "and b.roomType.id = :roomTypeId and b.status = 'CHECKED_OUT'")
    Instant findLatestCheckOutForReview(@Param("orderId") UUID orderId, @Param("roomTypeId") UUID roomTypeId);

    @Query(
            value =
                    """
            SELECT EXISTS (SELECT 1 FROM bookings b WHERE b.room_type_id = :roomTypeId
              AND (b.status = 'CHECKED_IN' OR (b.check_out_date >= :today
                AND (b.status = 'CONFIRMED' OR (b.status = 'PENDING' AND b.expired_at > :now)))))
            """,
            nativeQuery = true)
    boolean hasOutstandingDemandForRoomType(
            @Param("roomTypeId") UUID roomTypeId, @Param("today") LocalDate today, @Param("now") Instant now);

    @Query("select b.id from Booking b where b.order.id = :orderId order by b.id")
    java.util.List<UUID> findIdsByOrderId(@Param("orderId") UUID orderId);

    @Query("select distinct b.roomType.id from Booking b where b.order.id = :orderId")
    java.util.List<UUID> findRoomTypeIdsByOrderId(@Param("orderId") UUID orderId);

    @Query(
            value =
                    """
            WITH stay_nights AS (
                SELECT generate_series(CAST(:checkIn AS date), CAST(:checkOut AS date) - 1,
                    INTERVAL '1 day')::date AS stay_date
            ), nightly_demand AS (
                SELECT b.room_type_id, n.stay_date, COUNT(*) AS booking_count
                FROM stay_nights n
                JOIN bookings b ON b.check_in_date <= n.stay_date
                    AND (b.check_out_date > n.stay_date
                        OR (b.status = 'CHECKED_IN' AND b.check_out_date <= :overdueThrough))
                WHERE b.room_type_id IN (:roomTypeIds)
                    AND (b.status IN ('CONFIRMED', 'CHECKED_IN')
                        OR (b.status = 'PENDING' AND b.expired_at > :now))
                    AND (CAST(:excludedBookingId AS uuid) IS NULL
                        OR b.id <> CAST(:excludedBookingId AS uuid))
                GROUP BY b.room_type_id, n.stay_date
            )
            SELECT room_type_id AS "roomTypeId", MAX(booking_count) AS "bookingCount"
            FROM nightly_demand GROUP BY room_type_id
            """,
            nativeQuery = true)
    Collection<RoomTypeBookingCount> countPeakActiveBookingsByRoomTypeIds(
            @Param("roomTypeIds") Collection<UUID> roomTypeIds,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("overdueThrough") LocalDate overdueThrough,
            @Param("now") Instant now,
            @Param("excludedBookingId") UUID excludedBookingId);

    @Query(
            value =
                    """
            WITH stay_nights AS (
                SELECT generate_series(CAST(:checkIn AS date), CAST(:checkOut AS date) - 1, INTERVAL '1 day')::date AS stay_date
            ), nightly_demand AS (
                SELECT b.room_type_id, n.stay_date, COUNT(*) AS booking_count
                FROM stay_nights n JOIN bookings b ON b.check_in_date <= n.stay_date
                    AND (b.check_out_date > n.stay_date OR (b.status = 'CHECKED_IN' AND b.check_out_date <= :overdueThrough))
                WHERE b.room_type_id IN (:roomTypeIds)
                    AND (b.status IN ('CONFIRMED', 'CHECKED_IN') OR (b.status = 'PENDING' AND b.expired_at > :now))
                    AND (b.order_id IS NULL OR b.order_id <> :excludedOrderId)
                GROUP BY b.room_type_id, n.stay_date
            ) SELECT room_type_id AS "roomTypeId", MAX(booking_count) AS "bookingCount"
              FROM nightly_demand GROUP BY room_type_id
            """,
            nativeQuery = true)
    Collection<RoomTypeBookingCount> countPeakExcludingOrder(
            @Param("roomTypeIds") Collection<UUID> roomTypeIds,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("overdueThrough") LocalDate overdueThrough,
            @Param("now") Instant now,
            @Param("excludedOrderId") UUID excludedOrderId);

    @Query(
            """
            SELECT b FROM Booking b WHERE b.status = :status
            AND (b.checkInDate = :today OR (:includeYesterday = true AND b.checkInDate = :yesterday
                AND b.checkOutDate >= :today))
            """)
    @EntityGraph(attributePaths = "roomType")
    Page<Booking> findArrivalsIncludingLate(
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("yesterday") LocalDate yesterday,
            @Param("includeYesterday") boolean includeYesterday,
            Pageable pageable);

    @Query(
            """
            SELECT b FROM Booking b WHERE b.status = :status AND b.checkInDate < :today
            AND NOT (:includeYesterday = true AND b.checkInDate = :yesterday AND b.checkOutDate >= :today)
            """)
    @EntityGraph(attributePaths = "roomType")
    Page<Booking> findOverdueArrivals(
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("yesterday") LocalDate yesterday,
            @Param("includeYesterday") boolean includeYesterday,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT booking
            FROM Booking booking
            WHERE booking.bookingCode = :bookingCode
            """)
    Optional<Booking> findByBookingCodeForUpdate(@Param("bookingCode") String bookingCode);

    @EntityGraph(attributePaths = "roomType")
    Optional<Booking> findByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = "bookedBy")
    Optional<Booking> findByBookingCodeAndBookedById(String bookingCode, UUID userId);

    boolean existsByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = "roomType")
    @Query("select b from Booking b where b.bookedBy.id = :userId and b.order is null")
    Page<Booking> findAllByBookedById(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "roomType")
    @Query("select b from Booking b where b.bookedBy.id = :userId and b.status = :status and b.order is null")
    Page<Booking> findAllByBookedByIdAndStatus(
            @Param("userId") UUID userId, @Param("status") BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "roomType")
    Page<Booking> findAllByCheckInDateAndStatus(LocalDate checkInDate, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "roomType")
    Page<Booking> findAllByCheckOutDateLessThanEqualAndStatus(
            LocalDate checkOutDate, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "roomType")
    Page<Booking> findAllByStatus(BookingStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    java.util.Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    java.util.Optional<Booking> findByIdWithAssignedRoomForUpdate(@Param("id") UUID id);

    @Query(
            """
            select count(b) from Booking b
            where b.assignedRoom.id = :roomId and b.id <> :excludedBookingId
              and b.checkInDate < :checkOut and b.checkOutDate > :checkIn
              and b.status in :statuses
            """)
    long countAssignedRoomConflicts(
            @Param("roomId") UUID roomId,
            @Param("excludedBookingId") UUID excludedBookingId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<BookingStatus> statuses);

    @Query(
            value =
                    """
            SELECT COUNT(*) FROM room_allocations a WHERE a.room_id = :roomId AND a.active
            AND (a.booking_id IS NULL OR a.booking_id <> :excludedBookingId)
            AND daterange(a.check_in, a.check_out, '[)') && daterange(CAST(:checkIn AS date), CAST(:checkOut AS date), '[)')
            """,
            nativeQuery = true)
    long countPlannedRoomConflicts(
            @Param("roomId") UUID roomId,
            @Param("excludedBookingId") UUID excludedBookingId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);

    @Query(value = "SELECT room_id FROM room_allocations WHERE booking_id = :bookingId AND active", nativeQuery = true)
    Optional<UUID> findActivePlannedRoomId(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT b
            FROM Booking b
            WHERE b.status = :status
              AND b.order IS NULL
              AND b.expiredAt IS NOT NULL
              AND b.expiredAt <= :now
            """)
    Collection<Booking> findExpiredBookingsForUpdate(@Param("status") BookingStatus status, @Param("now") Instant now);

    interface RoomTypeBookingCount {
        UUID getRoomTypeId();

        long getBookingCount();
    }
}
