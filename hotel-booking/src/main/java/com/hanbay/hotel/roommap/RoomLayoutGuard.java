package com.hanbay.hotel.roommap;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomLayoutGuard {
    private final JdbcTemplate jdbc;

    public void requireUnplaced(UUID roomId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM hotel_floor_plan_elements WHERE room_id = ?)", Boolean.class, roomId))) {
            throw new FloorPlanConflictException(
                    "ROOM_LAYOUT_CONFLICT",
                    "Phòng đang nằm trong sơ đồ. Hãy gỡ phòng khỏi bố cục trước khi đổi tầng hoặc xóa phòng.");
        }
    }
}
