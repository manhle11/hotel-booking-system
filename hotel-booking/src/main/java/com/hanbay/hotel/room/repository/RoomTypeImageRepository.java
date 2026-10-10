package com.hanbay.hotel.room.repository;

import com.hanbay.hotel.room.domain.RoomTypeImage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTypeImageRepository extends JpaRepository<RoomTypeImage, UUID> {}
