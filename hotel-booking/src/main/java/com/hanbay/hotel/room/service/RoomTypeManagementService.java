package com.hanbay.hotel.room.service;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
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
    private final RoomCategoryRepository categories;
    private final HotelStayPolicy stayPolicy;

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
        RoomCategory category = r.categoryId() == null
                ? type.getCategory()
                : categories
                        .findById(r.categoryId())
                        .orElseThrow(() -> new RoomValidationException("Nhóm phòng không tồn tại."));
        if (id == null && (category == null || r.beds() == null)) {
            throw new RoomValidationException("Loại phòng mới phải có nhóm phòng và cấu hình giường.");
        }
        Map<BedType, Integer> configuration = requestedBeds(r);
        boolean bedsChanged = configuration != null && !configuration.equals(type.bedConfiguration());
        boolean legacyTextChanged = r.bedType() != null && !Objects.equals(r.bedType(), type.getBedType());
        if (id != null && (bedsChanged || legacyTextChanged) && types.hasBookingHistory(id)) {
            throw new RoomTypeBedConfigurationConflictException();
        }
        if (configuration == null && !type.getBeds().isEmpty() && legacyTextChanged) {
            throw new RoomValidationException(
                    "Vui lòng cập nhật giường qua trường beds; bedType là mô tả tương thích chỉ đọc.");
        }
        Set<Amenity> selected = new LinkedHashSet<>(amenities.findAllById(r.amenityIds()));
        if (selected.size() != r.amenityIds().size()) throw new RoomValidationException("Tiện nghi không tồn tại.");
        if (r.maxGuests() < r.capacityAdults() || r.maxGuests() > r.capacityAdults() + r.capacityChildren())
            throw new RoomValidationException("Tổng sức chứa không hợp lệ.");
        if (id != null
                && (r.maxGuests() < type.getMaxGuests()
                        || r.capacityAdults() < type.getCapacityAdults()
                        || r.capacityChildren() < type.getCapacityChildren())) {
            var now = stayPolicy.now();
            if (types.hasBookingsExceedingCapacity(
                    id, r.capacityAdults(), r.capacityChildren(), r.maxGuests(), now.toLocalDate(), now.toInstant()))
                throw new RoomTypeCapacityConflictException();
        }
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
                configuration == null && type.getBeds().isEmpty() && r.bedType() != null
                        ? r.bedType()
                        : type.getBedType(),
                r.areaM2(),
                r.status(),
                r.images(),
                selected);
        type.assignCategory(category);
        type.updateFeatures(r.viewType(), r.hasBalcony(), r.highFloor());
        if (configuration != null) type.replaceBeds(configuration);
        return RoomTypeManagementResponse.from(types.saveAndFlush(type));
    }

    @Transactional
    public void delete(UUID id) {
        RoomType type = types.findByIdForUpdate(id)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng."));
        type.deactivate();
        types.saveAndFlush(type);
    }

    private Map<BedType, Integer> requestedBeds(RoomTypeWriteRequest request) {
        if (request.beds() == null) return null; // Old clients preserve the current configuration.
        if (request.beds().isEmpty()) throw new RoomValidationException("Cấu hình giường không được rỗng.");
        Map<BedType, Integer> result = new EnumMap<>(BedType.class);
        for (var bed : request.beds()) {
            if (bed == null || bed.bedType() == null || bed.quantity() <= 0)
                throw new RoomValidationException("Loại giường và số lượng giường không hợp lệ.");
            if (result.putIfAbsent(bed.bedType(), bed.quantity()) != null)
                throw new RoomValidationException("Mỗi loại giường chỉ được xuất hiện một lần trong cấu hình.");
        }
        return result;
    }

    private RoomType find(UUID id) {
        return types.findById(id).orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng."));
    }
}
