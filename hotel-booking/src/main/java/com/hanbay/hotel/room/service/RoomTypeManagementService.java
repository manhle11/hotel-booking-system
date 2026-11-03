package com.hanbay.hotel.room.service;

import com.hanbay.hotel.common.query.DashboardTableQuery;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.media.ImageStorageService;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.*;
import com.hanbay.hotel.room.dto.request.RoomTypeWriteRequest;
import com.hanbay.hotel.room.exception.*;
import com.hanbay.hotel.room.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomTypeManagementService {
    private final RoomTypeRepository types;
    private final AmenityRepository amenities;
    private final ImageStorageService storage;
    private final DashboardTableQuery tableQuery;

    public PageResponse<RoomTypeManagementResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new RoomValidationException("Phân trang không hợp lệ.");
        return PageResponse.from(
                types.findAll(PageRequest.of(page, size, Sort.by("name", "id"))).map(RoomTypeManagementResponse::from));
    }

    public PageResponse<RoomTypeManagementResponse> list(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) return list(page, size);
        return PageResponse.from(tableQuery
                .findAll(DashboardTableQuery.Table.ROOM_TYPES, sort, page, size, types::findAllById, RoomType::getId)
                .map(RoomTypeManagementResponse::from));
    }

    public RoomTypeManagementResponse detail(UUID id) {
        return RoomTypeManagementResponse.from(find(id));
    }

    public List<Amenity> amenities() {
        return amenities.findAll(Sort.by("name"));
    }

    @Transactional
    public RoomTypeManagementResponse save(UUID id, RoomTypeWriteRequest r) {
        RoomType type = id == null
                ? RoomType.builder().build()
                : types.findByIdForUpdate(id)
                        .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng."));
        Set<Amenity> selected = new LinkedHashSet<>(amenities.findAllById(r.amenityIds()));
        if (selected.size() != r.amenityIds().size()) throw new RoomValidationException("Tiện nghi không tồn tại.");
        if (r.maxGuests() < r.capacityAdults() || r.maxGuests() > r.capacityAdults() + r.capacityChildren())
            throw new RoomValidationException("Tổng sức chứa không hợp lệ.");
        Set<String> existing = type.getRoomTypeImages().stream()
                .map(RoomTypeImage::getImageUrl)
                .collect(java.util.stream.Collectors.toSet());
        for (String url : r.images()) if (!existing.contains(url)) storage.requireImage(url);
        type.updateDetails(
                r.code().trim(),
                r.name().trim(),
                r.description(),
                r.shortDescription(),
                r.basePrice(),
                r.maxGuests(),
                r.capacityAdults(),
                r.capacityChildren(),
                r.bedType(),
                r.areaM2(),
                r.status(),
                r.images(),
                selected);
        return RoomTypeManagementResponse.from(types.saveAndFlush(type));
    }

    @Transactional
    public void delete(UUID id) {
        types.delete(types.findByIdForUpdate(id)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng.")));
        types.flush();
    }

    private RoomType find(UUID id) {
        return types.findById(id).orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng."));
    }
}
