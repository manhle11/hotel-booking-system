package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RoomAssignmentSolver {
    private final double seconds;
    private final SolverConfiguration configuration;

    private static class NativeRuntime {
        private static final boolean AVAILABLE = load();

        private static boolean load() {
            try {
                Loader.loadNativeLibraries();
                return true;
            } catch (RuntimeException | LinkageError exception) {
                log.warn("CP-SAT native runtime unavailable; only validated greedy assignments can be used", exception);
                return false;
            }
        }

        static boolean available() {
            return AVAILABLE;
        }
    }

    /** Native extraction/loading belongs to startup, outside the interactive solve budget. */
    @PostConstruct
    public void initializeNativeRuntime() {
        NativeRuntime.available();
    }

    public RoomAssignmentSolver(double seconds) {
        this(seconds, 10, 4, 3);
    }

    @Autowired
    public RoomAssignmentSolver(
            @Value("${hotel.assignment.solver-seconds:5}") double seconds,
            @Value("${hotel.assignment.weights.orphan-one:10}") int one,
            @Value("${hotel.assignment.weights.orphan-two:4}") int two,
            @Value("${hotel.assignment.weights.changed:3}") int changed) {
        if (!Double.isFinite(seconds) || seconds <= 0 || one < 0 || two < 0 || changed < 0)
            throw new IllegalArgumentException("Assignment budget must be positive and weights must be nonnegative");
        this.seconds = seconds;
        this.configuration = new SolverConfiguration(one, two, changed, seconds);
    }

    public SolverConfiguration configuration() {
        return configuration;
    }

    public long score(Metrics metrics) {
        return (long) configuration.orphanOneWeight() * metrics.orphanOne()
                + (long) configuration.orphanTwoWeight() * metrics.orphanTwo()
                + (long) configuration.changeWeight() * metrics.changed();
    }

    /** All stays, including pending holds and stays outside the visible window, participate. */
    public Solution solve(
            Snapshot snapshot,
            Set<UUID> movable,
            Map<UUID, UUID> forced,
            LocalDate from,
            LocalDate to,
            boolean optimize) {
        return solve(snapshot, movable, forced, from, to, optimize, seconds);
    }

    /** Read-only admission checks may use a smaller per-request budget. */
    public Solution solve(
            Snapshot snapshot,
            Set<UUID> movable,
            Map<UUID, UUID> forced,
            LocalDate from,
            LocalDate to,
            boolean optimize,
            double maxSeconds) {
        if (!Double.isFinite(maxSeconds) || maxSeconds <= 0) throw new IllegalArgumentException("Invalid solve budget");
        Map<UUID, UUID> baseline = greedy(snapshot, Set.of(), forced);
        Map<UUID, UUID> greedy = greedy(snapshot, movable, forced);
        Map<UUID, UUID> fallback = best(snapshot, baseline, greedy, from, to);
        if (!optimize && fallback != null) return new Solution("FEASIBLE", "GREEDY", fallback, List.of());
        if (snapshot.stays().isEmpty()) return new Solution("OPTIMAL", "GREEDY", Map.of(), List.of());
        if (!NativeRuntime.available()) return unavailable(fallback);
        try {
            CpModel model = new CpModel();
            Map<UUID, Map<UUID, BoolVar>> choices = new LinkedHashMap<>();
            Map<UUID, List<IntervalVar>> intervals = new LinkedHashMap<>();
            snapshot.rooms().forEach(r -> intervals.put(r.id(), new ArrayList<>()));
            for (Stay stay : snapshot.stays()) {
                Map<UUID, BoolVar> roomChoices = new LinkedHashMap<>();
                UUID fixed = fixedRoom(stay, snapshot.today(), movable, forced);
                for (PhysicalRoom room : snapshot.rooms()) {
                    if (!room.sellable()
                            || !room.roomTypeId().equals(stay.roomTypeId())
                            || (fixed != null && !fixed.equals(room.id()))) continue;
                    BoolVar selected = model.newBoolVar("assign_" + stay.id() + "_" + room.id());
                    roomChoices.put(room.id(), selected);
                    intervals
                            .get(room.id())
                            .add(model.newOptionalFixedSizeIntervalVar(
                                    LinearExpr.constant(stay.start().toEpochDay()),
                                    ChronoUnit.DAYS.between(stay.start(), stay.end()),
                                    selected,
                                    "stay"));
                    if (fallback != null) model.addHint(selected, room.id().equals(fallback.get(stay.id())) ? 1 : 0);
                }
                if (roomChoices.isEmpty())
                    return new Solution(
                            "INFEASIBLE",
                            "CP_SAT",
                            Map.of(),
                            List.of("Booking " + stay.bookingCode() + " không có phòng đáp ứng ràng buộc."));
                model.addExactlyOne(new ArrayList<>(roomChoices.values()));
                choices.put(stay.id(), roomChoices);
            }
            intervals.values().forEach(model::addNoOverlap);
            if (optimize) addObjective(model, snapshot, choices, from, to);
            CpSolver solver = new CpSolver();
            solver.getParameters()
                    .setMaxTimeInSeconds(Math.min(seconds, maxSeconds))
                    .setNumSearchWorkers(2)
                    .setRandomSeed(0);
            CpSolverStatus status = solver.solve(model);
            if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
                Map<UUID, UUID> result = new LinkedHashMap<>();
                choices.forEach((bookingId, vars) -> vars.forEach((roomId, selected) -> {
                    if (solver.booleanValue(selected)) result.put(bookingId, roomId);
                }));
                validate(snapshot, result, movable, forced);
                Map<UUID, UUID> chosen = fallback != null
                                && score(metrics(snapshot, fallback, from, to))
                                        <= score(metrics(snapshot, result, from, to))
                        ? fallback
                        : result;
                return new Solution(status.name(), "CP_SAT", chosen, List.of());
            }
            if (fallback != null)
                return new Solution(
                        "FEASIBLE",
                        "GREEDY",
                        fallback,
                        List.of("Bộ tối ưu trả " + status.name() + "; sử dụng phương án dự phòng đã kiểm tra."));
            return new Solution(
                    status.name(),
                    "CP_SAT",
                    Map.of(),
                    List.of(
                            status == CpSolverStatus.INFEASIBLE
                                    ? "Không thể xếp toàn bộ booking với các phòng cố định hiện tại."
                                    : "Chưa tìm được phương án hợp lệ trong thời gian cho phép. Vui lòng thử lại hoặc điều chỉnh khóa phòng."));
        } catch (RuntimeException | LinkageError exception) {
            log.error("Room assignment optimizer failed; validating fallback availability", exception);
            return unavailable(fallback);
        }
    }

    private Solution unavailable(Map<UUID, UUID> fallback) {
        if (fallback != null)
            return new Solution(
                    "FEASIBLE",
                    "GREEDY",
                    fallback,
                    List.of("Bộ tối ưu chưa khả dụng; sử dụng phương án dự phòng đã kiểm tra."));
        return new Solution(
                "MODEL_INVALID",
                "CP_SAT",
                Map.of(),
                List.of("Bộ tối ưu chưa khả dụng và chưa có phương án dự phòng hợp lệ."));
    }

    private Map<UUID, UUID> best(Snapshot s, Map<UUID, UUID> a, Map<UUID, UUID> b, LocalDate from, LocalDate to) {
        if (a == null) return b;
        if (b == null) return a;
        return score(metrics(s, a, from, to)) <= score(metrics(s, b, from, to)) ? a : b;
    }

    public Map<UUID, UUID> greedy(Snapshot snapshot, Set<UUID> movable, Map<UUID, UUID> forced) {
        Map<UUID, UUID> assigned = new LinkedHashMap<>();
        Map<UUID, List<Stay>> calendars = new LinkedHashMap<>();
        snapshot.rooms().stream().filter(PhysicalRoom::sellable).forEach(r -> calendars.put(r.id(), new ArrayList<>()));
        for (Stay stay : snapshot.stays()) {
            UUID fixed = fixedRoom(stay, snapshot.today(), movable, forced);
            if (fixed != null) {
                if (!eligible(snapshot, stay, fixed) || !fits(calendars.get(fixed), stay)) return null;
                calendars.get(fixed).add(stay);
                assigned.put(stay.id(), fixed);
            }
        }
        for (Stay stay : snapshot.stays().stream()
                .sorted(Comparator.comparing(Stay::start).thenComparing(Stay::bookingCode))
                .toList()) {
            if (assigned.containsKey(stay.id())) continue;
            PhysicalRoom room = snapshot.rooms().stream()
                    .filter(r -> eligible(snapshot, stay, r.id()))
                    .filter(r -> fits(calendars.get(r.id()), stay))
                    .min(Comparator.<PhysicalRoom>comparingLong(r -> precedingGap(calendars.get(r.id()), stay))
                            .thenComparing(r -> !r.id().equals(stay.roomId()))
                            .thenComparing(PhysicalRoom::roomNumber))
                    .orElse(null);
            if (room == null) return null;
            calendars.get(room.id()).add(stay);
            assigned.put(stay.id(), room.id());
        }
        validate(snapshot, assigned, movable, forced);
        return assigned;
    }

    private long precedingGap(List<Stay> calendar, Stay stay) {
        return calendar.stream()
                .filter(b -> !b.end().isAfter(stay.start()))
                .mapToLong(b -> ChronoUnit.DAYS.between(b.end(), stay.start()))
                .min()
                .orElse(Long.MAX_VALUE);
    }

    private boolean fits(List<Stay> calendar, Stay stay) {
        return calendar != null
                && calendar.stream()
                        .noneMatch(
                                b -> b.start().isBefore(stay.end()) && b.end().isAfter(stay.start()));
    }

    private UUID fixedRoom(Stay b, LocalDate today, Set<UUID> movable, Map<UUID, UUID> forced) {
        if (forced.containsKey(b.id())) return forced.get(b.id());
        return b.fixed(today) || (b.roomId() != null && !movable.contains(b.id())) ? b.roomId() : null;
    }

    private boolean eligible(Snapshot s, Stay b, UUID roomId) {
        return s.rooms().stream()
                .anyMatch(r ->
                        r.id().equals(roomId) && r.sellable() && r.roomTypeId().equals(b.roomTypeId()));
    }

    /** Independent from the CP model: never persist an unchecked fallback or solver result. */
    public void validate(Snapshot s, Map<UUID, UUID> assigned, Set<UUID> movable, Map<UUID, UUID> forced) {
        if (assigned.size() != s.stays().size()) throw new IllegalArgumentException("Incomplete assignment");
        Map<UUID, List<Stay>> calendars = new HashMap<>();
        for (Stay b : s.stays()) {
            UUID roomId = assigned.get(b.id());
            UUID fixed = fixedRoom(b, s.today(), movable, forced);
            if (roomId == null || !eligible(s, b, roomId) || (fixed != null && !fixed.equals(roomId)))
                throw new IllegalArgumentException("Invalid room for " + b.bookingCode());
            var calendar = calendars.computeIfAbsent(roomId, ignored -> new ArrayList<>());
            if (!fits(calendar, b)) throw new IllegalArgumentException("Overlapping room assignments");
            calendar.add(b);
        }
    }

    private void addObjective(
            CpModel model, Snapshot snapshot, Map<UUID, Map<UUID, BoolVar>> choices, LocalDate from, LocalDate to) {
        LinearExprBuilder objective = LinearExpr.newBuilder();
        for (Stay b : snapshot.stays()) {
            if (b.roomId() == null || b.pending()) continue;
            choices.get(b.id()).forEach((roomId, var) -> {
                if (!roomId.equals(b.roomId())) objective.addTerm(var, configuration.changeWeight());
            });
        }
        for (PhysicalRoom room : snapshot.rooms()) {
            if (!room.sellable()) continue;
            Map<LocalDate, BoolVar> occupancy = new HashMap<>();
            for (LocalDate date = from.minusDays(1); !date.isAfter(to); date = date.plusDays(1)) {
                BoolVar occupied = model.newBoolVar("occupied_" + room.id() + "_" + date);
                LinearExprBuilder sum = LinearExpr.newBuilder();
                for (Stay b : snapshot.stays()) {
                    BoolVar choice = choices.get(b.id()).get(room.id());
                    if (!b.pending() && choice != null && !date.isBefore(b.start()) && date.isBefore(b.end()))
                        sum.add(choice);
                }
                model.addEquality(occupied, sum);
                occupancy.put(date, occupied);
            }
            for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
                for (int size = 1; size <= 2; size++) {
                    if (date.plusDays(size).isAfter(to)) continue;
                    List<Literal> terms = new ArrayList<>();
                    terms.add(occupancy.get(date.minusDays(1)));
                    terms.add(occupancy.get(date.plusDays(size)));
                    for (int i = 0; i < size; i++)
                        terms.add(occupancy.get(date.plusDays(i)).not());
                    BoolVar gap = model.newBoolVar("gap_" + room.id() + "_" + date + "_" + size);
                    model.addBoolAnd(terms).onlyEnforceIf(gap);
                    model.addBoolOr(terms.stream().map(Literal::not).toList()).onlyEnforceIf(gap.not());
                    objective.addTerm(
                            gap, size == 1 ? configuration.orphanOneWeight() : configuration.orphanTwoWeight());
                }
            }
        }
        model.minimize(objective);
    }

    public Metrics metrics(Snapshot snapshot, Map<UUID, UUID> assignments, LocalDate from, LocalDate to) {
        int one = 0, two = 0, changed = 0, unassigned = 0, three = 0, five = 0, seven = 0;
        for (Stay b : snapshot.stays()) {
            if (b.pending() || !b.overlaps(from, to)) continue;
            if (!assignments.containsKey(b.id())) unassigned++;
            else if (b.roomId() != null && !b.roomId().equals(assignments.get(b.id()))) changed++;
        }
        for (PhysicalRoom room : snapshot.rooms()) {
            if (!room.sellable()) continue;
            var stays = snapshot.stays().stream()
                    .filter(b -> !b.pending() && room.id().equals(assignments.get(b.id())))
                    .sorted(Comparator.comparing(Stay::start))
                    .toList();
            LocalDate previous = null;
            LocalDate cursor = from;
            for (Stay b : stays) {
                if (previous != null && !previous.isBefore(from) && !b.start().isAfter(to)) {
                    long gap = ChronoUnit.DAYS.between(previous, b.start());
                    if (gap == 1) one++;
                    if (gap == 2) two++;
                }
                LocalDate boundary = b.start().isBefore(to) ? b.start() : to;
                long gap = Math.max(0, ChronoUnit.DAYS.between(cursor, boundary));
                three += Math.max(0, gap - 2);
                five += Math.max(0, gap - 4);
                seven += Math.max(0, gap - 6);
                if (b.end().isAfter(cursor)) cursor = b.end();
                if (cursor.isAfter(to)) cursor = to;
                previous = b.end();
            }
            long tail = Math.max(0, ChronoUnit.DAYS.between(cursor, to));
            three += Math.max(0, tail - 2);
            five += Math.max(0, tail - 4);
            seven += Math.max(0, tail - 6);
        }
        return new Metrics(one, two, changed, unassigned, three, five, seven);
    }
}
