package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;
import static org.assertj.core.api.Assertions.*;

import com.google.ortools.Loader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RoomAssignmentSolverTest {
    private final RoomAssignmentSolver solver = new RoomAssignmentSolver(2);
    private final UUID type = UUID.randomUUID();
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 24), start = today.plusDays(5);

    private PhysicalRoom room(UUID id, String number, String status) {
        return new PhysicalRoom(id, type, "Deluxe", number, 2, status);
    }

    private Stay stay(String code, int in, int out, UUID roomId, String state) {
        return new Stay(
                UUID.randomUUID(),
                code,
                type,
                start.plusDays(in),
                start.plusDays(out),
                "CONFIRMED",
                0,
                null,
                null,
                "Guest",
                null,
                roomId,
                state);
    }

    private Snapshot snapshot(List<Stay> stays) {
        return new Snapshot(
                List.of(room(first, "201", "AVAILABLE"), room(second, "202", "AVAILABLE")),
                stays,
                today,
                today.minusDays(1),
                Instant.parse("2026-11-24T08:00:00Z"),
                "version");
    }

    private Set<UUID> all(Snapshot s) {
        return s.stays().stream().map(Stay::id).collect(Collectors.toSet());
    }

    @Test
    void nativeOrToolsLoadsOnTheDevelopmentPlatform() {
        assertThatCode(Loader::loadNativeLibraries).doesNotThrowAnyException();
    }

    @Test
    void adjacentBookingsUseOneRoom() {
        Stay a = stay("A", 0, 1, null, null), b = stay("B", 1, 2, null, null);
        Snapshot s = new Snapshot(
                List.of(room(first, "201", "AVAILABLE")), List.of(a, b), today, today.minusDays(1), Instant.now(), "v");
        var result = solver.solve(s, all(s), Map.of(), start, start.plusDays(30), false);
        assertThat(result.feasible()).isTrue();
        assertThat(result.assignments().values()).containsOnly(first);
    }

    @Test
    void crossedLockedRoomsMakeContinuousStayInfeasibleDespiteNightlyCapacity() {
        Snapshot s = snapshot(List.of(
                stay("A", 0, 1, first, "LOCKED"), stay("B", 1, 2, second, "LOCKED"), stay("C", 0, 2, null, null)));
        var result = solver.solve(s, all(s), Map.of(), start, start.plusDays(30), false);
        assertThat(result.status()).isEqualTo("INFEASIBLE");
        assertThat(result.assignments()).isEmpty();
    }

    @Test
    void cpSatRecoversWhenGreedyChoosesTheWrongRoomBeforeAFixedBooking() {
        Stay a = stay("A", 0, 1, null, null),
                b = stay("B", 0, 2, null, null),
                fixed = stay("F", 1, 2, second, "LOCKED");
        Snapshot s = snapshot(List.of(a, b, fixed));
        assertThat(solver.greedy(s, all(s), Map.of())).isNull();
        var result = solver.solve(s, all(s), Map.of(), start, start.plusDays(30), false);
        assertThat(result.feasible()).isTrue();
        assertThat(result.algorithm()).isEqualTo("CP_SAT");
        assertThat(result.assignments()).containsEntry(a.id(), second).containsEntry(b.id(), first);
    }

    @Test
    void optimizerReducesAnOrphanNightWithoutMovingLockedOrOccupiedGuests() {
        Snapshot s = snapshot(List.of(
                stay("A", 0, 2, first, "TENTATIVE"),
                stay("B", 2, 3, second, "TENTATIVE"),
                stay("C", 3, 5, first, "TENTATIVE")));
        var result = solver.solve(s, all(s), Map.of(), start, start.plusDays(30), true);
        assertThat(result.feasible()).isTrue();
        assertThat(solver.metrics(s, s.currentAssignments(), start, start.plusDays(30))
                        .orphanOne())
                .isEqualTo(1);
        var metrics = solver.metrics(s, result.assignments(), start, start.plusDays(30));
        assertThat(metrics.orphanOne()).isZero();
        assertThat(metrics.changed()).isEqualTo(1);
        assertThat(solver.score(metrics)).isEqualTo(3);
    }

    @Test
    void maintenanceIsExcludedButDirtyRoomsRemainSellableForFutureStays() {
        Snapshot base = snapshot(List.of(stay("A", 0, 2, null, null)));
        Snapshot s = new Snapshot(
                List.of(room(first, "201", "MAINTENANCE"), room(second, "202", "DIRTY")),
                base.stays(),
                today,
                today.minusDays(1),
                Instant.now(),
                "v");
        assertThat(solver.solve(s, all(s), Map.of(), start, start.plusDays(30), false)
                        .assignments()
                        .values())
                .containsOnly(second);
    }

    @Test
    void frozenAssignmentsStayInTheirRooms() {
        Stay frozen = new Stay(
                UUID.randomUUID(),
                "TODAY",
                type,
                today,
                today.plusDays(2),
                "CONFIRMED",
                0,
                null,
                null,
                "Guest",
                null,
                first,
                "TENTATIVE");
        Snapshot s = snapshot(List.of(frozen));
        assertThat(solver.solve(s, all(s), Map.of(), today, today.plusDays(30), true)
                        .assignments())
                .containsEntry(frozen.id(), first);
    }

    @Test
    void pendingHoldsParticipateWithoutBecomingPublishedAssignmentsOrGapMetrics() {
        Stay hold = new Stay(
                UUID.randomUUID(),
                "PENDING",
                type,
                start,
                start.plusDays(3),
                "PENDING",
                0,
                null,
                null,
                "Guest",
                Instant.now().plusSeconds(600),
                null,
                null);
        Snapshot s = snapshot(List.of(hold, stay("A", 0, 3, null, null), stay("B", 1, 2, null, null)));
        assertThat(solver.solve(s, all(s), Map.of(), start, start.plusDays(30), false)
                        .status())
                .isEqualTo("INFEASIBLE");
    }

    @Test
    void validatorRejectsIncompleteAndOverlappingSolutions() {
        Stay a = stay("A", 0, 3, null, null), b = stay("B", 1, 2, null, null);
        Snapshot s = snapshot(List.of(a, b));
        assertThatThrownBy(() -> solver.validate(s, Map.of(a.id(), first), all(s), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> solver.validate(s, Map.of(a.id(), first, b.id(), first), all(s), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staysOutsideTheVisibleWindowStillPreventConflicts() {
        Snapshot s = snapshot(List.of(
                stay("LONG", -10, 100, first, "LOCKED"),
                stay("FUTURE", 29, 100, second, "LOCKED"),
                stay("NEW", 20, 40, null, null)));
        assertThat(solver.solve(s, all(s), Map.of(), start, start.plusDays(30), false)
                        .status())
                .isEqualTo("INFEASIBLE");
    }

    @Test
    void greedyMatchesNightlyCapacityForRandomUnconstrainedIntervals() {
        Random random = new Random(42);
        for (int sample = 0; sample < 100; sample++) {
            List<Stay> stays = new ArrayList<>();
            for (int n = 0; n < 30; n++) {
                int in = random.nextInt(30), out = in + 1 + random.nextInt(6);
                Stay candidate = stay("B" + n, in, out, null, null);
                boolean allowed = true;
                for (int day = in; day < out; day++) {
                    LocalDate night = start.plusDays(day);
                    if (stays.stream()
                                    .filter(b ->
                                            !b.start().isAfter(night) && b.end().isAfter(night))
                                    .count()
                            >= 2) allowed = false;
                }
                if (allowed) stays.add(candidate);
            }
            Snapshot s = snapshot(stays);
            assertThat(solver.greedy(s, all(s), Map.of()))
                    .as("sample %s", sample)
                    .hasSize(stays.size());
        }
    }
}
