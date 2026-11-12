package com.hanbay.hotel.room.service;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.query.DashboardTableQuery;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.request.RoomWriteRequest;
import com.hanbay.hotel.room.dto.response.RoomManagementResponse;
import com.hanbay.hotel.room.exception.*;
import com.hanbay.hotel.room.repository.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomManagementService {
    private final RoomRepository rooms;
    private final RoomTypeRepository types;
    private final BookingRepository bookings;
    private final DashboardTableQuery tableQuery;
    private final HotelStayPolicy stayPolicy;
    private final com.hanbay.hotel.roommap.RoomLayoutGuard layoutGuard;

    public PageResponse<RoomManagementResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new RoomValidationException("Phân trang không hợp lệ.");
        return PageResponse.from(rooms.findAllByOrderByFloorAscRoomNumberAsc(PageRequest.of(page, size))
                .map(RoomManagementResponse::from));
    }

    public PageResponse<RoomManagementResponse> list(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) return list(page, size);
        return PageResponse.from(tableQuery
                .findAll(DashboardTableQuery.Table.ROOMS, sort, page, size, rooms::findAllById, Room::getId)
                .map(RoomManagementResponse::from));
    }

    public RoomManagementResponse detail(UUID id) {
        return RoomManagementResponse.from(find(id));
    }

    @Transactional
    public RoomManagementResponse create(RoomWriteRequest r) {
        RoomType type = lockType(r.roomTypeId());
        requireActive(type);
        return RoomManagementResponse.from(rooms.saveAndFlush(Room.builder()
                .roomType(type)
                .roomNumber(r.roomNumber())
                .floor(r.floor())
                .note(r.note())
                .status(RoomStatus.AVAILABLE)
                .build()));
    }

    @Transactional
    public RoomManagementResponse update(UUID id, RoomWriteRequest r) {
        UUID oldType = rooms.findRoomTypeId(id).orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng."));
        // Follow booking lock order: room type before physical room; sort both types to avoid deadlocks.
        java.util.stream.Stream.of(oldType, r.roomTypeId()).distinct().sorted().forEach(this::lockType);
        Room room = lockRoom(id);
        if (!room.getRoomType().getId().equals(oldType))
            throw new RoomValidationException("Phòng vừa thay đổi. Vui lòng tải lại.");
        RoomType type = lockType(r.roomTypeId());
        if (room.getFloor() != r.floor()) layoutGuard.requireUnplaced(id);
        if (!oldType.equals(r.roomTypeId())) {
            requireNoDemand(oldType);
            requireActive(type);
        }
        room.updateDetails(type, r.roomNumber(), r.floor(), r.note());
        return RoomManagementResponse.from(rooms.saveAndFlush(room));
    }

    @Transactional
    public void delete(UUID id) {
        UUID oldType = rooms.findRoomTypeId(id).orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng."));
        lockType(oldType);
        Room room = lockRoom(id);
        if (!room.getRoomType().getId().equals(oldType))
            throw new RoomValidationException("Phòng vừa thay đổi. Vui lòng tải lại.");
        if (room.getStatus() != RoomStatus.AVAILABLE) throw new RoomValidationException("Chỉ xóa phòng đang sẵn sàng.");
        // Keep the room lock while checking history so a concurrent check-in cannot lose its assignment.
        if (bookings.existsByAssignedRoomId(id)) throw RoomStateConflictException.bookingHistory();
        layoutGuard.requireUnplaced(id);
        requireNoDemand(oldType);
        rooms.delete(room);
        rooms.flush();
    }

    private void requireNoDemand(UUID typeId) {
        var now = stayPolicy.now();
        if (bookings.hasOutstandingDemandForRoomType(typeId, now.toLocalDate(), now.toInstant()))
            throw new RoomValidationException(
                    "Loại phòng còn đặt phòng chưa hoàn tất. Không thể giảm số phòng hoặc đổi loại lúc này.");
    }

    private void requireActive(RoomType type) {
        if (type.getStatus() != RoomTypeStatus.ACTIVE)
            throw new RoomValidationException("Loại phòng đã ngừng kinh doanh.");
    }

    private Room find(UUID id) {
        return rooms.findById(id).orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng."));
    }

    private Room lockRoom(UUID id) {
        return rooms.findByIdForUpdate(id).orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng."));
    }

    private RoomType lockType(UUID id) {
        return types.findByIdForUpdate(id)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng."));
    }
}
