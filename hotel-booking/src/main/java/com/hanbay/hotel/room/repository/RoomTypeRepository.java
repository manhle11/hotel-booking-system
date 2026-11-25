package com.hanbay.hotel.room.repository;

import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.domain.RoomTypeStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

    @Query(
            "SELECT DISTINCT rt FROM RoomType rt LEFT JOIN FETCH rt.amenities WHERE rt.id IN :ids AND rt.status = :status")
    List<RoomType> findRecommendationDetails(@Param("ids") List<UUID> ids, @Param("status") RoomTypeStatus status);

    // All statuses count: completed/cancelled bookings also carry historical room information.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM bookings WHERE room_type_id = :id)", nativeQuery = true)
    boolean hasBookingHistory(@Param("id") UUID id);

    @Query(
            value =
                    """
            SELECT EXISTS (SELECT 1 FROM bookings b WHERE b.room_type_id = :id
              AND (b.status = 'CHECKED_IN' OR (b.check_out_date >= :today
                AND (b.status = 'CONFIRMED' OR (b.status = 'PENDING'
                  AND (b.expired_at IS NULL OR b.expired_at > :now)))))
              AND (b.adult_count > :adults OR b.child_count > :children
                OR b.adult_count + b.child_count > :total))
            """,
            nativeQuery = true)
    boolean hasBookingsExceedingCapacity(
            @Param("id") UUID id,
            @Param("adults") int adults,
            @Param("children") int children,
            @Param("total") int total,
            @Param("today") LocalDate today,
            @Param("now") Instant now);

    /*
     * develop / HAN-21
     * Lấy danh sách loại phòng đang active.
     */
    List<RoomType> findAllByStatusOrderByNameAsc(RoomTypeStatus status);

    Optional<RoomType> findByIdAndStatus(UUID id, RoomTypeStatus status);

    /*
     * develop
     * Dùng khi cần lock RoomType để update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RoomType rt WHERE rt.id = :id")
    Optional<RoomType> findByIdForUpdate(@Param("id") UUID id);

    /*
     * HAN-22
     * Lấy chi tiết RoomType cùng images và amenities.
     */
    @Query(
            """
            SELECT DISTINCT roomType
            FROM RoomType roomType
            LEFT JOIN FETCH roomType.roomTypeImages
            LEFT JOIN FETCH roomType.amenities
            WHERE roomType.id = :id
              AND roomType.status = :status
            """)
    Optional<RoomType> findDetailByIdAndStatus(@Param("id") UUID id, @Param("status") RoomTypeStatus status);

    @Query(
            """
            SELECT DISTINCT roomType
            FROM RoomType roomType
            LEFT JOIN FETCH roomType.roomTypeImages
            LEFT JOIN FETCH roomType.amenities
            WHERE LOWER(roomType.name) = LOWER(:name)
              AND roomType.status = :status
            """)
    Optional<RoomType> findDetailByNameIgnoreCaseAndStatus(
            @Param("name") String name, @Param("status") RoomTypeStatus status);

    /*
     * develop / HAN-21
     * Search + pagination danh sách loại phòng.
     */
    Page<RoomType> findByStatusAndNameContainingIgnoreCase(RoomTypeStatus status, String name, Pageable pageable);
}
