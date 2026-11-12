package com.hanbay.hotel.roommap;

import com.hanbay.hotel.room.domain.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RoomMapDto {
    private RoomMapDto() {}

    public enum ElementKind {
        ROOM,
        CORRIDOR,
        ELEVATOR,
        STAIRS,
        COMMON_AREA
    }

    public record LayoutElement(
            UUID id, ElementKind kind, String roomKey, String label, int x, int y, int width, int height) {}

    public record PublicRoom(
            String key,
            String roomNumber,
            UUID roomTypeId,
            String roomTypeName,
            String bedType,
            String thumbnailUrl,
            boolean bookable) {}

    public record StaffRoom(
            String key,
            UUID roomId,
            String roomNumber,
            UUID roomTypeId,
            String roomTypeName,
            RoomStatus operationalStatus) {}

    public record PublicFloor(
            int floorNumber,
            String name,
            int gridColumns,
            int gridRows,
            long version,
            Instant publishedAt,
            List<LayoutElement> elements,
            List<PublicRoom> rooms) {}

    public record StaffFloor(
            int floorNumber,
            String name,
            int gridColumns,
            int gridRows,
            long version,
            Instant publishedAt,
            List<LayoutElement> elements,
            List<StaffRoom> rooms) {}

    public record PublicMap(List<PublicFloor> floors) {}

    public record StaffMap(List<StaffFloor> floors, Instant serverNow) {}

    public record WritePlan(
            @NotBlank @Size(max = 80) String name,
            @Min(8) @Max(80) int gridColumns,
            @Min(8) @Max(80) int gridRows,
            @Min(0) long expectedVersion,
            @NotNull @Size(max = 400) List<@NotNull @Valid WriteElement> elements) {}

    public record WriteElement(
            @NotNull ElementKind kind,
            UUID roomId,
            @Size(max = 80) String label,
            @Min(0) int x,
            @Min(0) int y,
            @Min(1) @Max(80) int width,
            @Min(1) @Max(80) int height) {}
}
