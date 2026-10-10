package com.hanbay.hotel.booking.repository;

import com.hanbay.hotel.booking.domain.BookingStatusHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, UUID> {}
