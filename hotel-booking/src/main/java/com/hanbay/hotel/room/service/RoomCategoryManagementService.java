package com.hanbay.hotel.room.service;

import com.hanbay.hotel.common.exception.ResourceNotFoundException;
import com.hanbay.hotel.room.domain.RoomCategory;
import com.hanbay.hotel.room.dto.RoomCategoryResponse;
import com.hanbay.hotel.room.dto.request.RoomCategoryWriteRequest;
import com.hanbay.hotel.room.repository.RoomCategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomCategoryManagementService {
    private final RoomCategoryRepository categories;

    public List<RoomCategoryResponse> list() {
        return categories.findAll(Sort.by("name", "id")).stream()
                .map(RoomCategoryResponse::from)
                .toList();
    }

    @Transactional
    public RoomCategoryResponse save(UUID id, RoomCategoryWriteRequest request) {
        RoomCategory category = id == null
                ? RoomCategory.builder().build()
                : categories
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm phòng."));
        category.updateDetails(request.code().trim(), request.name().trim());
        return RoomCategoryResponse.from(categories.saveAndFlush(category));
    }
}
