package com.hanbay.hotel.roommap;

import static com.hanbay.hotel.roommap.RoomMapDto.*;

import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomTypeStatus;
import com.hanbay.hotel.room.repository.RoomRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RoomMapService {
    private final RoomRepository rooms;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PublicMap publicMap() {
        Snapshot snapshot = snapshot(null);
        return new PublicMap(snapshot.floors().stream()
                .map(floor -> {
                    Plan plan = snapshot.plan(floor);
                    List<Room> floorRooms = snapshot.rooms(floor);
                    Map<UUID, Room> byId =
                            floorRooms.stream().collect(Collectors.toMap(Room::getId, Function.identity()));
                    return new PublicFloor(
                            floor,
                            plan.name(),
                            plan.columns(),
                            plan.rows(),
                            plan.version(),
                            plan.publishedAt(),
                            elements(snapshot, plan, byId, true),
                            floorRooms.stream()
                                    .map(room -> new PublicRoom(
                                            room.getRoomNumber(),
                                            room.getRoomNumber(),
                                            room.getRoomType().getId(),
                                            room.getRoomType().getName(),
                                            room.getRoomType().getBedType(),
                                            room.getRoomType().getThumbnailUrl(),
                                            room.getRoomType().getStatus() == RoomTypeStatus.ACTIVE))
                                    .toList());
                })
                .toList());
    }

    public StaffMap staffMap(Integer floor) {
        Snapshot snapshot = snapshot(floor);
        return new StaffMap(
                snapshot.floors().stream().map(n -> staffFloor(snapshot, n)).toList(), clock.instant());
    }

    public StaffFloor managementFloor(int floor) {
        validateFloor(floor);
        return staffFloor(snapshot(floor), floor);
    }

    @Transactional
    public StaffFloor publish(int floor, WritePlan request) {
        validateFloor(floor);
        validateGeometry(request);
        List<UUID> roomIds = request.elements().stream()
                .map(WriteElement::roomId)
                .filter(Objects::nonNull)
                .toList();
        if (new HashSet<>(roomIds).size() != roomIds.size()) invalid("Một phòng chỉ được bố trí một lần.");
        // Same physical-room locks as room update/delete. Lock rooms before the layout to avoid cycles.
        List<Room> lockedRooms = roomIds.isEmpty() ? List.of() : rooms.findAllForLayoutUpdate(roomIds);
        if (lockedRooms.size() != roomIds.size() || lockedRooms.stream().anyMatch(room -> room.getFloor() != floor)) {
            invalid("Chỉ được chọn phòng tồn tại thuộc đúng tầng đang cấu hình.");
        }

        jdbc.update(
                """
                INSERT INTO hotel_floor_plans (id, floor_number, name, grid_columns, grid_rows, version)
                VALUES (?, ?, ?, ?, ?, 0) ON CONFLICT (floor_number) DO NOTHING
                """,
                UUID.randomUUID(),
                floor,
                request.name().trim(),
                request.gridColumns(),
                request.gridRows());
        Plan current = jdbc.queryForObject(
                "SELECT * FROM hotel_floor_plans WHERE floor_number = ? FOR UPDATE", this::plan, floor);
        if (current.version() != request.expectedVersion()) {
            throw new FloorPlanConflictException(
                    "FLOOR_PLAN_VERSION_CONFLICT", "Bố cục đã được thay đổi. Hãy tải lại bản mới trước khi lưu.");
        }
        jdbc.update("DELETE FROM hotel_floor_plan_elements WHERE floor_plan_id = ?", current.id());
        for (WriteElement element : request.elements()) {
            jdbc.update(
                    """
                    INSERT INTO hotel_floor_plan_elements
                    (id, floor_plan_id, kind, room_id, label, x, y, width, height)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    current.id(),
                    element.kind().name(),
                    element.roomId(),
                    element.kind() == ElementKind.ROOM ? null : clean(element.label()),
                    element.x(),
                    element.y(),
                    element.width(),
                    element.height());
        }
        jdbc.update(
                """
                UPDATE hotel_floor_plans SET name = ?, grid_columns = ?, grid_rows = ?, version = version + 1,
                published_at = ? WHERE id = ?
                """,
                request.name().trim(),
                request.gridColumns(),
                request.gridRows(),
                java.sql.Timestamp.from(clock.instant()),
                current.id());
        return managementFloor(floor);
    }

    private void validateGeometry(WritePlan request) {
        // Also defend service calls that do not pass through MVC Bean Validation.
        if (request.name() == null
                || request.name().isBlank()
                || request.name().length() > 80
                || request.gridColumns() < 8
                || request.gridColumns() > 80
                || request.gridRows() < 8
                || request.gridRows() > 80
                || request.expectedVersion() < 0
                || request.elements() == null
                || request.elements().size() > 400) invalid("Bố cục hoặc kích thước lưới không hợp lệ.");
        for (int i = 0; i < request.elements().size(); i++) {
            WriteElement e = request.elements().get(i);
            if (e == null
                    || e.kind() == null
                    || (e.kind() == ElementKind.ROOM) != (e.roomId() != null)
                    || e.x() < 0
                    || e.y() < 0
                    || e.width() < 1
                    || e.height() < 1
                    || e.width() > 80
                    || e.height() > 80
                    || e.x() > request.gridColumns() - e.width()
                    || e.y() > request.gridRows() - e.height()
                    || (e.label() != null && e.label().length() > 80))
                invalid("Phần tử sai phòng hoặc nằm ngoài lưới.");
            for (int j = 0; j < i; j++) {
                WriteElement other = request.elements().get(j);
                if (e.x() < other.x() + other.width()
                        && e.x() + e.width() > other.x()
                        && e.y() < other.y() + other.height()
                        && e.y() + e.height() > other.y()) {
                    invalid("Các phần tử không được chồng lên nhau.");
                }
            }
        }
    }

    private Snapshot snapshot(Integer floor) {
        if (floor != null) validateFloor(floor);
        List<Room> allRooms = rooms.findAllForMap().stream()
                .filter(r -> floor == null || r.getFloor() == floor)
                .toList();
        List<Plan> plans = jdbc
                .query(
                        "SELECT * FROM hotel_floor_plans WHERE published_at IS NOT NULL ORDER BY floor_number",
                        this::plan)
                .stream()
                .filter(p -> floor == null || p.floor() == floor)
                .toList();
        Set<UUID> planIds = plans.stream().map(Plan::id).collect(Collectors.toSet());
        List<StoredElement> elements = jdbc
                .query(
                        "SELECT * FROM hotel_floor_plan_elements ORDER BY y, x, id",
                        (rs, n) -> new StoredElement(
                                rs.getObject("id", UUID.class),
                                rs.getObject("floor_plan_id", UUID.class),
                                ElementKind.valueOf(rs.getString("kind")),
                                rs.getObject("room_id", UUID.class),
                                rs.getString("label"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("width"),
                                rs.getInt("height")))
                .stream()
                .filter(e -> planIds.contains(e.planId()))
                .toList();
        SortedSet<Integer> floors = new TreeSet<>();
        allRooms.forEach(room -> floors.add(room.getFloor()));
        plans.forEach(p -> floors.add(p.floor()));
        return new Snapshot(allRooms, plans, elements, floors);
    }

    private StaffFloor staffFloor(Snapshot snapshot, int floor) {
        Plan plan = snapshot.plan(floor);
        List<Room> floorRooms = snapshot.rooms(floor);
        Map<UUID, Room> byId = floorRooms.stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        return new StaffFloor(
                floor,
                plan.name(),
                plan.columns(),
                plan.rows(),
                plan.version(),
                plan.publishedAt(),
                elements(snapshot, plan, byId, false),
                floorRooms.stream()
                        .map(room -> new StaffRoom(
                                room.getId().toString(),
                                room.getId(),
                                room.getRoomNumber(),
                                room.getRoomType().getId(),
                                room.getRoomType().getName(),
                                room.getStatus()))
                        .toList());
    }

    private List<LayoutElement> elements(Snapshot snapshot, Plan plan, Map<UUID, Room> byId, boolean publicView) {
        return snapshot.elements().stream()
                .filter(e -> e.planId().equals(plan.id()))
                .filter(e -> e.roomId() == null || byId.containsKey(e.roomId()))
                .map(e -> new LayoutElement(
                        e.id(),
                        e.kind(),
                        e.roomId() == null
                                ? null
                                : publicView
                                        ? byId.get(e.roomId()).getRoomNumber()
                                        : e.roomId().toString(),
                        e.kind() == ElementKind.ROOM ? null : e.label(),
                        e.x(),
                        e.y(),
                        e.width(),
                        e.height()))
                .toList();
    }

    private Plan plan(ResultSet rs, int index) throws SQLException {
        var published = rs.getTimestamp("published_at");
        return new Plan(
                rs.getObject("id", UUID.class),
                rs.getInt("floor_number"),
                rs.getString("name"),
                rs.getInt("grid_columns"),
                rs.getInt("grid_rows"),
                rs.getLong("version"),
                published == null ? null : published.toInstant());
    }

    private void validateFloor(int floor) {
        if (floor < -10 || floor > 200) invalid("Tầng phải từ -10 đến 200.");
    }

    private void invalid(String message) {
        throw new RequestValidationException("floorPlan", message);
    }

    private String clean(String label) {
        return label == null ? "" : label.trim();
    }

    private record Plan(UUID id, int floor, String name, int columns, int rows, long version, Instant publishedAt) {}

    private record StoredElement(
            UUID id, UUID planId, ElementKind kind, UUID roomId, String label, int x, int y, int width, int height) {}

    private record Snapshot(
            List<Room> allRooms, List<Plan> plans, List<StoredElement> elements, SortedSet<Integer> floors) {
        Plan plan(int floor) {
            return plans.stream()
                    .filter(p -> p.floor() == floor)
                    .findFirst()
                    .orElse(new Plan(null, floor, "Tầng " + floor, 24, 16, 0, null));
        }

        List<Room> rooms(int floor) {
            return allRooms.stream().filter(r -> r.getFloor() == floor).toList();
        }
    }
}
