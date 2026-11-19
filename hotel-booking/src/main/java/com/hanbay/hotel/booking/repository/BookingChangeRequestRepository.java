package com.hanbay.hotel.booking.repository;

import com.hanbay.hotel.booking.domain.BookingChangeRequest;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingChangeRequestRepository extends JpaRepository<BookingChangeRequest, UUID> {
    java.util.List<BookingChangeRequest> findAllByScopeKeyInAndStatus(
            java.util.Collection<String> keys, BookingChangeRequest.Status status);

    Optional<BookingChangeRequest> findByScopeKeyAndStatus(String scopeKey, BookingChangeRequest.Status status);

    boolean existsByScopeKeyAndStatus(String scopeKey, BookingChangeRequest.Status status);

    Page<BookingChangeRequest> findAllByStatus(BookingChangeRequest.Status status, Pageable pageable);

    List<BookingChangeRequest> findAllByScopeKeyOrderByRequestedAtDesc(String scopeKey);

    @Query("select r.booking.bookingCode from BookingChangeRequest r where r.id = :id")
    Optional<String> findBookingCodeById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BookingChangeRequest r where r.id = :id")
    Optional<BookingChangeRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            "select r.id from BookingChangeRequest r join r.booking b left join b.order o where r.type = 'RESCHEDULE' and r.status = 'APPROVED' "
                    + "and (:keyword = '' or lower(b.bookingCode) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(o.orderCode, '')) like lower(concat('%', :keyword, '%')) "
                    + "or exists (select room.id from Booking room where room.order = o and lower(room.bookingCode) like lower(concat('%', :keyword, '%')))) "
                    + "and not exists (select newer.id from BookingChangeRequest newer where newer.scopeKey = r.scopeKey "
                    + "and newer.type = 'RESCHEDULE' and newer.status = 'APPROVED' "
                    + "and (newer.decidedAt > r.decidedAt or (newer.decidedAt = r.decidedAt and newer.id > r.id))) "
                    + "order by r.decidedAt desc, r.id desc")
    Page<UUID> findLatestAppliedRescheduleIds(@Param("keyword") String keyword, Pageable pageable);
}
