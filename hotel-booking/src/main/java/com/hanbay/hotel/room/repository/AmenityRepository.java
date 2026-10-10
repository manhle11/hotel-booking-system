package com.hanbay.hotel.room.repository;

import com.hanbay.hotel.room.domain.Amenity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, UUID> {}
