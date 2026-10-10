package com.hanbay.hotel.booking.repository;

import com.hanbay.hotel.booking.domain.BookingNight;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingNightRepository extends JpaRepository<BookingNight, UUID> {}
