package com.hanbay.hotel.pricing.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.pricing.service.model.CreateHolidayCalendarCommand;
import com.hanbay.hotel.pricing.service.model.HolidayCalendarManagementResult;
import com.hanbay.hotel.pricing.service.model.HolidayCalendarSearchCriteria;
import com.hanbay.hotel.pricing.service.model.UpdateHolidayCalendarCommand;
import com.hanbay.hotel.pricing.service.model.UpdateHolidayCalendarStatusCommand;
import java.util.UUID;

public interface HolidayCalendarManagementService {

    PageResponse<HolidayCalendarManagementResult> findAll(HolidayCalendarSearchCriteria criteria);

    HolidayCalendarManagementResult findById(UUID id);

    HolidayCalendarManagementResult create(CreateHolidayCalendarCommand command);

    HolidayCalendarManagementResult update(UUID id, UpdateHolidayCalendarCommand command);

    HolidayCalendarManagementResult updateStatus(UUID id, UpdateHolidayCalendarStatusCommand command);
}
