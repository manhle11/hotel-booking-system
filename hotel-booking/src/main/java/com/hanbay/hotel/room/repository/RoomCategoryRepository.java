package com.hanbay.hotel.room.repository;

import com.hanbay.hotel.room.domain.RoomCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomCategoryRepository extends JpaRepository<RoomCategory, UUID> {}
