package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Simulates one additional room of each type independently. Never publishes or saves allocations. */
@Service
@RequiredArgsConstructor
public class RoomAssignmentFeasibilityService {
    public enum Feasibility {
        FEASIBLE,
        INFEASIBLE,
        UNKNOWN
    }

    private final AssignmentStore store;
    private final RoomAssignmentSolver solver;

    @Transactional(readOnly = true)
    public Map<UUID, Feasibility> check(Collection<UUID> typeIds, LocalDate from, LocalDate to) {
        if (typeIds.isEmpty()) return Map.of();
        Snapshot source = store.snapshot().through(to);
        long deadline = System.nanoTime() + 2_000_000_000L;
        Map<UUID, Feasibility> result = new LinkedHashMap<>();
        for (UUID id : typeIds) {
            double remaining = (deadline - System.nanoTime()) / 1_000_000_000.0;
            if (remaining <= 0) {
                result.put(id, Feasibility.UNKNOWN);
                continue;
            }
            Snapshot scope = source.forType(id);
            List<Stay> stays = new ArrayList<>(scope.stays());
            stays.add(new Stay(
                    UUID.randomUUID(), "RECOMMENDATION", id, from, to, "PENDING", 0, null, null, "", null, null, null));
            Snapshot candidate = new Snapshot(
                    scope.rooms(), stays, scope.today(), scope.overdueThrough(), scope.now(), scope.version());
            Set<UUID> movable = stays.stream()
                    .filter(b -> !b.fixed(scope.today()))
                    .map(Stay::id)
                    .collect(Collectors.toSet());
            Solution solution = solver.solve(candidate, movable, Map.of(), from, to, false, Math.min(0.5, remaining));
            result.put(
                    id,
                    solution.feasible()
                            ? Feasibility.FEASIBLE
                            : solution.status().equals("INFEASIBLE") ? Feasibility.INFEASIBLE : Feasibility.UNKNOWN);
        }
        return result;
    }
}
