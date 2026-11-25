package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class RoomAssignmentService {
    private final AssignmentStore store;
    private final RoomAssignmentSolver solver;
    private final InventoryWriteGuard guard;
    private final CurrentUserProvider currentUser;
    private final HotelStayPolicy policy;
    private final PlatformTransactionManager transactions;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CalendarView calendar(LocalDate from, LocalDate to, UUID typeId) {
        Snapshot s = store.snapshot();
        validateRange(from, to);
        return view(s, from, to, typeId);
    }

    private CalendarView view(Snapshot s, LocalDate from, LocalDate to, UUID typeId) {
        s = (typeId == null ? s : s.forType(typeId)).through(to);
        return new CalendarView(
                from,
                to,
                s.today(),
                s.version(),
                s.now(),
                s.rooms().stream()
                        .filter(r -> typeId == null || r.roomTypeId().equals(typeId))
                        .sorted(Comparator.comparingInt(PhysicalRoom::floor).thenComparing(PhysicalRoom::roomNumber))
                        .toList(),
                s.stays().stream()
                        .filter(b -> (typeId == null || b.roomTypeId().equals(typeId)) && b.overlaps(from, to))
                        .toList(),
                warnings(s));
    }

    private Snapshot readSnapshot() {
        TransactionTemplate read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> store.snapshot());
    }

    /** Snapshot and solve without holding an inventory write lock. Only the resulting draft is saved. */
    public Plan suggest(UUID typeId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Snapshot all = readSnapshot();
        Snapshot s = all.forType(typeId).through(to);
        if (s.rooms().isEmpty()) throw new RequestValidationException("roomTypeId", "Loại phòng chưa có phòng vật lý.");
        Set<UUID> movable = s.stays().stream()
                .filter(b -> b.overlaps(from, to) && !b.fixed(s.today()))
                .map(Stay::id)
                .collect(Collectors.toSet());
        Solution result = solver.solve(s, movable, Map.of(), from, to, true);
        Map<UUID, UUID> published = new LinkedHashMap<>();
        List<Change> changes = new ArrayList<>();
        Map<UUID, PhysicalRoom> rooms = s.rooms().stream().collect(Collectors.toMap(PhysicalRoom::id, r -> r));
        if (result.feasible())
            for (Stay b : s.stays()) {
                if (b.pending() || b.occupied() || !b.overlaps(from, to)) continue;
                UUID roomId = result.assignments().get(b.id());
                published.put(b.id(), roomId);
                boolean changed = !Objects.equals(b.roomId(), roomId);
                if (!changed) continue;
                PhysicalRoom old = rooms.get(b.roomId()), next = rooms.get(roomId);
                Stay preceding = s.stays().stream()
                        .filter(other -> !other.pending()
                                && other.end().equals(b.start())
                                && roomId.equals(result.assignments().get(other.id())))
                        .findFirst()
                        .orElse(null);
                String reason = preceding != null
                        ? "Khớp ngày trả phòng của booking " + preceding.bookingCode() + "."
                        : "Phòng trống liên tục cho toàn bộ kỳ ở, phù hợp các booking và phòng cố định hiện tại.";
                changes.add(new Change(
                        b.id(),
                        b.bookingCode(),
                        b.roomId(),
                        old == null ? null : old.roomNumber(),
                        roomId,
                        next.roomNumber(),
                        b.start(),
                        b.end(),
                        true,
                        reason));
            }
        Instant until = all.now().plusSeconds(300);
        Instant midnight = policy.today()
                .plusDays(1)
                .atStartOfDay(policy.getProperties().zoneId())
                .toInstant();
        if (midnight.isBefore(until)) until = midnight;
        for (Stay b : all.stays()) if (b.pending() && b.expiresAt().isBefore(until)) until = b.expiresAt();
        List<String> warnings = new ArrayList<>(warnings(s));
        warnings.addAll(result.warnings());
        Plan plan = new Plan(
                UUID.randomUUID(),
                typeId,
                from,
                to,
                all.version(),
                all.now(),
                until,
                result.status(),
                result.algorithm(),
                solver.metrics(s, s.currentAssignments(), from, to),
                result.feasible() ? solver.metrics(s, result.assignments(), from, to) : null,
                changes,
                warnings,
                published,
                false,
                solver.configuration());
        store.savePlan(plan, currentUser.currentUserId().orElse(null));
        return plan;
    }

    @Transactional
    public Plan apply(UUID id, String expectedVersion) {
        guard.enlist();
        Plan plan = store.plan(id, true);
        if (plan.applied()) return plan;
        Snapshot all = store.snapshot();
        if (!plan.sourceVersion().equals(expectedVersion)
                || !all.version().equals(expectedVersion)
                || !all.now().isBefore(plan.validUntil())) throw AssignmentException.stale();
        if (!Set.of("FEASIBLE", "OPTIMAL").contains(plan.status()))
            throw new AssignmentException("ASSIGNMENT_NO_SOLUTION", "Phương án chưa có lời giải hợp lệ để áp dụng.");
        Snapshot s = all.forType(plan.roomTypeId()).through(plan.to());
        Map<UUID, UUID> forced = new LinkedHashMap<>(s.currentAssignments());
        forced.putAll(plan.assignments());
        requireFeasible(s, forced, plan.from(), plan.to());
        UUID actor = currentUser.currentUserId().orElse(null);
        for (Stay b : s.stays()) {
            UUID next = plan.assignments().get(b.id());
            if (next == null || Objects.equals(next, b.roomId())) continue;
            if (b.pending() || b.fixed(s.today())) throw AssignmentException.stale();
            store.allocate(b, next, "TENTATIVE");
            store.history(
                    b.id(),
                    b.roomTypeId(),
                    "PLAN_APPLIED",
                    actor,
                    null,
                    Map.of(
                            "planId",
                            id,
                            "bookingCode",
                            b.bookingCode(),
                            "fromRoom",
                            b.roomId() == null ? "" : b.roomId(),
                            "toRoom",
                            next,
                            "fromRoomNumber",
                            roomNumber(s, b.roomId()),
                            "toRoomNumber",
                            roomNumber(s, next)));
        }
        store.markApplied(id, actor);
        return plan.asApplied();
    }

    @Transactional
    public CalendarView assign(
            UUID bookingId,
            UUID roomId,
            boolean locked,
            String expectedVersion,
            String reason,
            LocalDate from,
            LocalDate to) {
        validateRange(from, to);
        guard.enlist();
        Snapshot all = store.snapshot();
        if (!all.version().equals(expectedVersion)) throw AssignmentException.stale();
        Stay b = all.stays().stream()
                .filter(stay -> stay.id().equals(bookingId))
                .findFirst()
                .orElseThrow(() ->
                        new AssignmentException("ASSIGNMENT_BOOKING_NOT_FOUND", "Booking không còn chờ gán phòng."));
        if (!b.status().equals("CONFIRMED"))
            throw new AssignmentException(
                    "ASSIGNMENT_NOT_CONFIRMED", "Chỉ gán dự kiến cho booking đã xác nhận, chưa nhận phòng.");
        boolean roomChanged = !Objects.equals(b.roomId(), roomId);
        boolean unlocking = "LOCKED".equals(b.allocationStatus()) && !locked;
        if ((b.fixed(all.today()) && roomChanged) || unlocking) {
            if (!manager()) throw new AccessDeniedException("Chỉ quản lý được mở khóa hoặc đổi phòng đã cố định.");
            if (reason == null || reason.isBlank())
                throw new RequestValidationException("reason", "Nhập lý do mở khóa hoặc thay đổi phòng đã cố định.");
        }
        if (roomId == null && locked) throw new RequestValidationException("roomId", "Chọn phòng trước khi khóa.");
        Snapshot s = all.forType(b.roomTypeId());
        Map<UUID, UUID> forced = new LinkedHashMap<>(s.currentAssignments());
        forced.remove(b.id());
        if (roomId != null) {
            boolean allowed = s.rooms().stream().anyMatch(r -> r.id().equals(roomId) && r.sellable());
            if (!allowed) throw new RequestValidationException("roomId", "Phòng phải đúng loại và đang được phép bán.");
            forced.put(b.id(), roomId);
        }
        // Persist first so a manager's explicit unlock replaces the previous hard constraint.
        store.allocate(b, roomId, locked ? "LOCKED" : "TENTATIVE");
        Snapshot candidate = store.snapshot().forType(b.roomTypeId());
        requireFeasible(candidate, forced, from, to);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bookingCode", b.bookingCode());
        detail.put("fromRoom", b.roomId());
        detail.put("toRoom", roomId);
        detail.put("fromRoomNumber", roomNumber(s, b.roomId()));
        detail.put("toRoomNumber", roomNumber(s, roomId));
        detail.put("locked", locked);
        store.history(
                b.id(),
                b.roomTypeId(),
                locked ? "ROOM_LOCKED" : unlocking ? "ROOM_UNLOCKED" : "ROOM_ASSIGNED",
                currentUser.currentUserId().orElse(null),
                reason,
                detail);
        return view(store.snapshot(), from, to, b.roomTypeId());
    }

    private void requireFeasible(Snapshot s, Map<UUID, UUID> forced, LocalDate from, LocalDate to) {
        Solution result = solver.solve(s, Set.of(), forced, from, to, false);
        if (!result.feasible())
            throw new AssignmentException(
                    result.status().equals("INFEASIBLE") ? "ASSIGNMENT_INFEASIBLE" : "ASSIGNMENT_UNDETERMINED",
                    result.warnings().isEmpty()
                            ? "Chưa xác nhận được khả năng xếp toàn bộ booking."
                            : result.warnings().get(0));
    }

    private String roomNumber(Snapshot s, UUID roomId) {
        if (roomId == null) return "";
        return s.rooms().stream()
                .filter(r -> r.id().equals(roomId))
                .map(PhysicalRoom::roomNumber)
                .findFirst()
                .orElse("");
    }

    @Transactional(readOnly = true)
    public List<History> history(UUID typeId) {
        return store.history(typeId);
    }

    @Transactional(readOnly = true)
    public Plan plan(UUID id) {
        return store.plan(id, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Plan latest(UUID typeId, LocalDate from, LocalDate to) {
        Plan plan = store.latest(typeId);
        if (plan == null || !plan.from().equals(from) || !plan.to().equals(to)) return null;
        Snapshot s = store.snapshot();
        return plan.sourceVersion().equals(s.version()) && s.now().isBefore(plan.validUntil()) ? plan : null;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SuggestedRoom suggestedRoom(String bookingCode) {
        Snapshot s = store.snapshot();
        Stay booking = s.stays().stream()
                .filter(b -> b.bookingCode().equals(bookingCode) && !b.pending())
                .findFirst()
                .orElse(null);
        if (booking == null || booking.roomId() == null) return null;
        return s.rooms().stream()
                .filter(r -> r.id().equals(booking.roomId()))
                .map(r -> new SuggestedRoom(
                        booking.id(),
                        r.id(),
                        r.roomNumber(),
                        booking.allocationStatus(),
                        r.status().equals("AVAILABLE")))
                .findFirst()
                .orElse(null);
    }

    private boolean manager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
    }

    private List<String> warnings(Snapshot s) {
        List<String> result = new ArrayList<>();
        if (s.stays().stream().anyMatch(Stay::overdue))
            result.add("Có khách đang ở quá hạn; phòng tiếp tục bị chặn đến khi xử lý thực tế.");
        if (s.stays().stream().anyMatch(Stay::pending))
            result.add("Booking đang chờ thanh toán còn hạn được giữ chỗ khi kiểm tra khả năng xếp.");
        if (s.rooms().stream()
                .anyMatch(r -> r.status().equals("DIRTY") || r.status().equals("CLEANING")))
            result.add("Phòng đang bẩn/đang dọn vẫn được xếp cho tương lai; phải sẵn sàng trước khi check-in.");
        return result;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from) || ChronoUnit.DAYS.between(from, to) > 90)
            throw new RequestValidationException("to", "Chọn khoảng lịch từ 1 đến 90 ngày, không gồm ngày kết thúc.");
    }
}
