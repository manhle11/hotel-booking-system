package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;

import com.hanbay.hotel.common.security.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One inventory writer per hotel. The lock precedes existing booking/type/room row locks. */
@Component
@RequiredArgsConstructor
public class InventoryWriteGuard {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final AssignmentStore store;
    private final RoomAssignmentSolver solver;
    private final CurrentUserProvider currentUser;

    public void enlist() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) return;
        if (TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(Synchronization.class::isInstance)) return;
        jdbc.query("SELECT pg_advisory_xact_lock(2026112401)", rs -> {});
        Snapshot before = store.snapshot();
        jdbc.query(
                "SELECT set_config('hotel.assignment.today', ?, true)",
                rs -> {},
                before.today().toString());
        TransactionSynchronizationManager.registerSynchronization(new Synchronization(before));
    }

    private class Synchronization implements TransactionSynchronization {
        private final Snapshot before;

        Synchronization(Snapshot before) {
            this.before = before;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            if (readOnly) return;
            entities.flush();
            Snapshot after = store.snapshot();
            if (before.version().equals(after.version())) return;
            Set<UUID> types = changedTypes(before, after);
            Map<UUID, Stay> previous = byId(before.stays());
            for (Stay b : after.stays()) {
                Stay old = previous.get(b.id());
                if (b.occupied()
                        && old != null
                        && old.fixed(before.today())
                        && !Objects.equals(old.roomId(), b.roomId()))
                    throw new AssignmentException(
                            "ASSIGNMENT_LOCKED",
                            "Booking đã có phòng cố định. Quản lý cần duyệt thay đổi trước khi check-in.");
            }
            if (addsConstraints(before, after)) {
                for (UUID type : types) {
                    Snapshot scope = after.forType(type);
                    Set<UUID> movable = scope.stays().stream()
                            .filter(b -> !b.fixed(scope.today()))
                            .map(Stay::id)
                            .collect(Collectors.toSet());
                    Solution solution = solver.solve(
                            scope,
                            movable,
                            Map.of(),
                            scope.today(),
                            scope.today().plusDays(60),
                            false);
                    if (!solution.feasible())
                        throw new AssignmentException(
                                solution.status().equals("INFEASIBLE")
                                        ? "ASSIGNMENT_INFEASIBLE"
                                        : "ASSIGNMENT_UNDETERMINED",
                                solution.warnings().isEmpty()
                                        ? "Chưa xác nhận được khả năng xếp toàn bộ booking."
                                        : solution.warnings().get(0));
                }
            }
            try {
                jdbc.execute("SET CONSTRAINTS room_allocations_no_overlap IMMEDIATE");
            } catch (DataIntegrityViolationException e) {
                throw new AssignmentException(
                        "ASSIGNMENT_OVERLAP",
                        "Phòng đã có booking hoặc khoảng bảo trì trùng lịch. Vui lòng lấy gợi ý mới.");
            }
            UUID actor = currentUser.currentUserId().orElse(null);
            for (UUID type : types)
                jdbc.update(
                        """
                    INSERT INTO room_assignment_refresh_queue(room_type_id)
                    SELECT id FROM room_types WHERE id = ?
                    ON CONFLICT (room_type_id) DO UPDATE SET requested_at = clock_timestamp()
                    """,
                        type);
            Map<UUID, Stay> old = byId(before.stays());
            Map<UUID, Stay> updated = byId(after.stays());
            Set<UUID> ids = new HashSet<>(old.keySet());
            ids.addAll(updated.keySet());
            for (UUID id : ids) {
                Stay a = old.get(id), b = updated.get(id);
                if (a == null
                        || b == null
                        || !a.status().equals(b.status())
                        || !a.start().equals(b.start())
                        || !a.end().equals(b.end())) {
                    Stay anchor = b == null ? a : b;
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("before", a);
                    details.put("after", b);
                    store.history(id, anchor.roomTypeId(), "BOOKING_UPDATED", actor, null, details);
                }
            }
        }
    }

    private Set<UUID> changedTypes(Snapshot before, Snapshot after) {
        Set<UUID> result = new HashSet<>();
        Map<UUID, Stay> old = byId(before.stays()), updated = byId(after.stays());
        old.forEach((id, b) -> {
            if (!b.equals(updated.get(id))) result.add(b.roomTypeId());
        });
        updated.forEach((id, b) -> {
            if (!b.equals(old.get(id))) result.add(b.roomTypeId());
        });
        Map<UUID, PhysicalRoom> rooms = before.rooms().stream().collect(Collectors.toMap(PhysicalRoom::id, r -> r));
        after.rooms().forEach(r -> {
            PhysicalRoom priorRoom = rooms.remove(r.id());
            if (!r.equals(priorRoom)) {
                result.add(r.roomTypeId());
                if (priorRoom != null) result.add(priorRoom.roomTypeId());
            }
        });
        rooms.values().forEach(r -> result.add(r.roomTypeId()));
        return result;
    }

    private Map<UUID, Stay> byId(List<Stay> stays) {
        return stays.stream().collect(Collectors.toMap(Stay::id, b -> b));
    }

    private boolean addsConstraints(Snapshot before, Snapshot after) {
        Set<UUID> available = after.rooms().stream()
                .filter(PhysicalRoom::sellable)
                .map(PhysicalRoom::id)
                .collect(Collectors.toSet());
        if (before.rooms().stream().anyMatch(r -> r.sellable() && !available.contains(r.id()))) return true;
        if (before.rooms().stream().anyMatch(old -> after.rooms().stream()
                .anyMatch(r -> r.id().equals(old.id()) && !r.roomTypeId().equals(old.roomTypeId())))) return true;
        Map<UUID, Stay> old = byId(before.stays());
        for (Stay b : after.stays()) {
            Stay a = old.get(b.id());
            if (a == null
                    || !a.roomTypeId().equals(b.roomTypeId())
                    || b.start().isBefore(a.start())
                    || b.end().isAfter(a.end())
                    || (b.fixed(after.today())
                            && (!a.fixed(before.today()) || !Objects.equals(a.roomId(), b.roomId())))) return true;
        }
        return false;
    }
}
