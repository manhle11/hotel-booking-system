package com.hanbay.hotel.booking.repository;

import com.hanbay.hotel.booking.domain.BookingOrder;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingOrderRepository extends JpaRepository<BookingOrder, UUID> {
    @Query(value = "select pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockCheckout(@Param("key") String key);

    Optional<CheckoutReference> findByIdempotencyKey(String key);

    interface CheckoutReference {
        UUID getId();

        String getAccessTokenHash();

        String getRequestHash();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BookingOrder o where o.id = :id")
    Optional<BookingOrder> findByIdForUpdate(@Param("id") UUID id);

    Page<BookingOrder> findAllByBookedById(UUID userId, Pageable pageable);

    @Query("select o.id from BookingOrder o where o.status = 'PENDING' and o.expiresAt <= :now order by o.id")
    List<UUID> findExpiredIds(@Param("now") Instant now);
}
