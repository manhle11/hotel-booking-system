package com.hanbay.hotel.maintenance.dto.response;

import com.hanbay.hotel.maintenance.domain.MaintenanceRequestStatus;
import com.hanbay.hotel.room.domain.RoomStatus;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceRequestListItemResponse(
        UUID id,
        UUID roomId,
        String roomNumber,
        RoomStatus roomStatus,
        String title,
        MaintenanceRequestStatus status,
        UUID createdById,
        String createdByName,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {}
