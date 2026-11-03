package com.hanbay.hotel.room.service;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.pricing.service.PricingService;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.domain.RoomTypeStatus;
import com.hanbay.hotel.room.dto.response.AvailableRoomTypeResponse;
import com.hanbay.hotel.room.exception.RoomValidationException;
import com.hanbay.hotel.room.mapper.RoomTypeMapper;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailableRoomTypeSearchService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityCalculator roomAvailabilityCalculator;
    private final Clock clock;
    private final HotelStayPolicy stayPolicy;
    private final PricingService pricingService;
    private final RoomTypeMapper roomTypeMapper;

    @Transactional(readOnly = true)
    public PageResponse<AvailableRoomTypeResponse> search(
            LocalDate checkIn, LocalDate checkOut, int adults, int children, UUID roomTypeId, int page, int size) {
        validateSearchCriteria(checkIn, checkOut, adults, children);
        PaginationValidator.validate(page, size, MAX_PAGE_SIZE);

        List<RoomType> candidateRoomTypes =
                roomTypeRepository.findAllByStatusOrderByNameAsc(RoomTypeStatus.ACTIVE).stream()
                        .filter(roomType -> roomType.canAccommodate(adults, children))
                        .filter(roomType ->
                                roomTypeId == null || roomType.getId().equals(roomTypeId))
                        .toList();

        Map<UUID, Long> availableRoomCounts = roomAvailabilityCalculator.countAvailableRooms(
                candidateRoomTypes.stream().map(RoomType::getId).toList(), checkIn, checkOut);

        List<RoomType> availableRoomTypes = candidateRoomTypes.stream()
                .filter(roomType -> availableRoomCounts.getOrDefault(roomType.getId(), 0L) > 0)
                .toList();

        int totalElements = availableRoomTypes.size();
        int fromIndex = (int) Math.min((long) page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<RoomType> pageRoomTypes = availableRoomTypes.subList(fromIndex, toIndex);
        Map<UUID, Long> readyRoomCounts = roomAvailabilityCalculator.countReadyRooms(
                pageRoomTypes.stream().map(RoomType::getId).toList());
        List<AvailableRoomTypeResponse> content = pageRoomTypes.stream()
                .map(roomType -> roomTypeMapper.toAvailableResponse(
                        roomType,
                        availableRoomCounts.getOrDefault(roomType.getId(), 0L),
                        readyRoomCounts.getOrDefault(roomType.getId(), 0L),
                        pricingService.calculateRoomPrice(roomType, checkIn, checkOut)))
                .toList();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return new PageResponse<>(
                content, page, size, totalElements, totalPages, page == 0, totalPages == 0 || page >= totalPages - 1);
    }

    private void validateSearchCriteria(LocalDate checkIn, LocalDate checkOut, int adults, int children) {
        if (checkIn == null) {
            throw new RoomValidationException("Ngày nhận phòng là bắt buộc.");
        }
        if (checkOut == null) {
            throw new RoomValidationException("Ngày trả phòng là bắt buộc.");
        }
        if (checkIn.isBefore(stayPolicy.today())) {
            throw new RoomValidationException("Ngày nhận phòng không được ở quá khứ.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new RoomValidationException("Ngày trả phòng phải sau ngày nhận phòng.");
        }
        if (stayPolicy.bookingClosed(checkIn)) {
            throw new RoomValidationException("Đã qua giờ nhận đặt phòng cho hôm nay. Vui lòng chọn ngày tiếp theo.");
        }
        if (adults < 1) {
            throw new RoomValidationException("Số người lớn phải lớn hơn hoặc bằng 1.");
        }
        if (children < 0) {
            throw new RoomValidationException("Số trẻ em không được âm.");
        }
    }
}
