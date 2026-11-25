package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService.Feasibility;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RoomAssignmentFeasibilityServiceTest {
    private final AssignmentStore store = mock(AssignmentStore.class);
    private final RoomAssignmentSolver solver = new RoomAssignmentSolver(0.5);
    private final RoomAssignmentFeasibilityService service = new RoomAssignmentFeasibilityService(store, solver);
    private final UUID type = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 25), in = today.plusDays(5);

    private PhysicalRoom room(UUID id) {
        return new PhysicalRoom(id, type, "Twin", id.toString(), 2, "AVAILABLE");
    }

    private Stay stay(UUID room, LocalDate start, LocalDate end) {
        return new Stay(
                UUID.randomUUID(),
                "B",
                type,
                start,
                end,
                "CONFIRMED",
                0,
                null,
                null,
                "Private guest",
                null,
                room,
                "LOCKED");
    }

    @Test
    void simulatesCandidateWithoutSavingAnyPlanOrBookingAndRejectsCrossedLocks() {
        var source = new Snapshot(
                List.of(room(first), room(second)),
                List.of(stay(first, in, in.plusDays(1)), stay(second, in.plusDays(1), in.plusDays(2))),
                today,
                today.minusDays(1),
                Instant.now(),
                "v1");
        when(store.snapshot()).thenReturn(source);
        assertThat(service.check(List.of(type), in, in.plusDays(2))).containsEntry(type, Feasibility.INFEASIBLE);
        verify(store).snapshot();
        verifyNoMoreInteractions(store);
        assertThat(source.stays()).hasSize(2);
    }

    @Test
    void adjacentStaysOnOneRoomAllowContinuousStayOnTheOther() {
        when(store.snapshot())
                .thenReturn(new Snapshot(
                        List.of(room(first), room(second)),
                        List.of(stay(first, in, in.plusDays(1)), stay(first, in.plusDays(1), in.plusDays(2))),
                        today,
                        today.minusDays(1),
                        Instant.now(),
                        "v1"));
        assertThat(service.check(List.of(type), in, in.plusDays(2))).containsEntry(type, Feasibility.FEASIBLE);
        verifyNoMoreInteractionsAfterSnapshot();
    }

    private void verifyNoMoreInteractionsAfterSnapshot() {
        verify(store).snapshot();
        verifyNoMoreInteractions(store);
    }
}
