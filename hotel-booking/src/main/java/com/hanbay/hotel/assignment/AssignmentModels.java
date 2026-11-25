package com.hanbay.hotel.assignment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AssignmentModels {
    private AssignmentModels() {}

    public record PhysicalRoom(
            UUID id, UUID roomTypeId, String roomTypeName, String roomNumber, int floor, String status) {
        public boolean sellable() {
            return !status.equals("MAINTENANCE") && !status.equals("OUT_OF_SERVICE");
        }
    }

    public record Stay(
            UUID id,
            String bookingCode,
            UUID roomTypeId,
            LocalDate start,
            LocalDate end,
            String status,
            int stayRevision,
            UUID orderId,
            Integer orderRoomNumber,
            String guestName,
            Instant expiresAt,
            UUID roomId,
            String allocationStatus,
            boolean overdue,
            LocalDate scheduledEnd) {
        public Stay(
                UUID id,
                String bookingCode,
                UUID roomTypeId,
                LocalDate start,
                LocalDate end,
                String status,
                int stayRevision,
                UUID orderId,
                Integer orderRoomNumber,
                String guestName,
                Instant expiresAt,
                UUID roomId,
                String allocationStatus) {
            this(
                    id,
                    bookingCode,
                    roomTypeId,
                    start,
                    end,
                    status,
                    stayRevision,
                    orderId,
                    orderRoomNumber,
                    guestName,
                    expiresAt,
                    roomId,
                    allocationStatus,
                    false,
                    end);
        }

        public boolean pending() {
            return status.equals("PENDING");
        }

        public boolean occupied() {
            return status.equals("CHECKED_IN");
        }

        public boolean fixed(LocalDate today) {
            return roomId != null
                    && (occupied() || "LOCKED".equals(allocationStatus) || !start.isAfter(today.plusDays(1)));
        }

        public boolean overlaps(LocalDate from, LocalDate to) {
            return start.isBefore(to) && end.isAfter(from);
        }
    }

    public record Snapshot(
            List<PhysicalRoom> rooms,
            List<Stay> stays,
            LocalDate today,
            LocalDate overdueThrough,
            Instant now,
            String version) {
        public Snapshot forType(UUID typeId) {
            return new Snapshot(
                    rooms.stream().filter(r -> r.roomTypeId().equals(typeId)).toList(),
                    stays.stream().filter(b -> b.roomTypeId().equals(typeId)).toList(),
                    today,
                    overdueThrough,
                    now,
                    version);
        }

        public Map<UUID, UUID> currentAssignments() {
            var result = new java.util.LinkedHashMap<UUID, UUID>();
            stays.stream().filter(b -> b.roomId() != null).forEach(b -> result.put(b.id(), b.roomId()));
            return result;
        }

        public Snapshot through(LocalDate to) {
            return new Snapshot(
                    rooms,
                    stays.stream()
                            .map(b -> b.overdue() && !b.end().isAfter(to)
                                    ? new Stay(
                                            b.id(),
                                            b.bookingCode(),
                                            b.roomTypeId(),
                                            b.start(),
                                            to.plusDays(1),
                                            b.status(),
                                            b.stayRevision(),
                                            b.orderId(),
                                            b.orderRoomNumber(),
                                            b.guestName(),
                                            b.expiresAt(),
                                            b.roomId(),
                                            b.allocationStatus(),
                                            true,
                                            b.scheduledEnd())
                                    : b)
                            .toList(),
                    today,
                    overdueThrough,
                    now,
                    version);
        }
    }

    public record Metrics(
            int orphanOne,
            int orphanTwo,
            int changed,
            int unassigned,
            int windowsThree,
            int windowsFive,
            int windowsSeven) {}

    public record SolverConfiguration(int orphanOneWeight, int orphanTwoWeight, int changeWeight, double maxSeconds) {}

    public record Solution(String status, String algorithm, Map<UUID, UUID> assignments, List<String> warnings) {
        public boolean feasible() {
            return status.equals("OPTIMAL") || status.equals("FEASIBLE");
        }
    }

    public record CalendarView(
            LocalDate from,
            LocalDate to,
            LocalDate today,
            String version,
            Instant serverNow,
            List<PhysicalRoom> rooms,
            List<Stay> bookings,
            List<String> warnings) {}

    public record SuggestedRoom(
            UUID bookingId, UUID roomId, String roomNumber, String allocationStatus, boolean ready) {}

    public record Change(
            UUID bookingId,
            String bookingCode,
            UUID fromRoomId,
            String fromRoomNumber,
            UUID toRoomId,
            String toRoomNumber,
            LocalDate start,
            LocalDate end,
            boolean changed,
            String reason) {}

    public record Plan(
            UUID id,
            UUID roomTypeId,
            LocalDate from,
            LocalDate to,
            String sourceVersion,
            Instant createdAt,
            Instant validUntil,
            String status,
            String algorithm,
            Metrics before,
            Metrics after,
            List<Change> changes,
            List<String> warnings,
            Map<UUID, UUID> assignments,
            boolean applied,
            SolverConfiguration configuration) {
        public Plan asApplied() {
            return new Plan(
                    id,
                    roomTypeId,
                    from,
                    to,
                    sourceVersion,
                    createdAt,
                    validUntil,
                    status,
                    algorithm,
                    before,
                    after,
                    changes,
                    warnings,
                    assignments,
                    true,
                    configuration);
        }
    }

    public record History(
            UUID id,
            UUID bookingId,
            String action,
            UUID actorId,
            String actorName,
            Instant createdAt,
            String reason,
            String details) {}
}
