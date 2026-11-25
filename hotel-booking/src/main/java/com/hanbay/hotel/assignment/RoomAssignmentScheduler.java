package com.hanbay.hotel.assignment;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "hotel.assignment.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RoomAssignmentScheduler {
    private final JdbcTemplate jdbc;
    private final RoomAssignmentService service;
    private final HotelStayPolicy policy;

    private record Refresh(UUID typeId, Timestamp requestedAt) {}

    @Scheduled(
            fixedDelayString = "${hotel.assignment.refresh-delay-ms:30000}",
            initialDelayString = "${hotel.assignment.refresh-delay-ms:30000}")
    public void refreshDrafts() {
        jdbc.update(
                "DELETE FROM room_assignment_refresh_queue q WHERE NOT EXISTS (SELECT 1 FROM rooms r WHERE r.room_type_id = q.room_type_id)");
        var queue = jdbc.query(
                "SELECT room_type_id, requested_at FROM room_assignment_refresh_queue ORDER BY requested_at LIMIT 10",
                (rs, n) -> new Refresh(rs.getObject(1, UUID.class), rs.getTimestamp(2)));
        for (Refresh refresh : queue) {
            try {
                service.suggest(refresh.typeId(), policy.today(), policy.today().plusDays(60));
                jdbc.update(
                        "DELETE FROM room_assignment_refresh_queue WHERE room_type_id = ? AND requested_at = ?",
                        refresh.typeId(),
                        refresh.requestedAt());
            } catch (RuntimeException e) {
                log.warn("Không thể tạo gợi ý gán phòng cho loại {}", refresh.typeId(), e);
            }
        }
    }

    @Scheduled(cron = "${hotel.assignment.nightly-cron:0 0 2 * * *}", zone = "${hotel.stay.zone:Asia/Ho_Chi_Minh}")
    public void nightly() {
        jdbc.update(
                """
                INSERT INTO room_assignment_refresh_queue(room_type_id)
                SELECT DISTINCT room_type_id FROM rooms
                ON CONFLICT (room_type_id) DO UPDATE SET requested_at = clock_timestamp()
                """);
        jdbc.update(
                "DELETE FROM room_assignment_plans WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '30 days' AND applied_at IS NULL");
    }
}
