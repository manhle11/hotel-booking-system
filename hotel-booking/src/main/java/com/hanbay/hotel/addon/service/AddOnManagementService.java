package com.hanbay.hotel.addon.service;

import com.hanbay.hotel.addon.service.model.AddOnManagementResult;
import com.hanbay.hotel.addon.service.model.AddOnSearchCriteria;
import com.hanbay.hotel.addon.service.model.CreateAddOnCommand;
import com.hanbay.hotel.addon.service.model.UpdateAddOnCommand;
import com.hanbay.hotel.addon.service.model.UpdateAddOnStatusCommand;
import com.hanbay.hotel.common.response.PageResponse;
import java.util.UUID;

public interface AddOnManagementService {

    PageResponse<AddOnManagementResult> findAll(AddOnSearchCriteria criteria);

    AddOnManagementResult findById(UUID id);

    AddOnManagementResult create(CreateAddOnCommand command);

    AddOnManagementResult update(UUID id, UpdateAddOnCommand command);

    AddOnManagementResult updateStatus(UUID id, UpdateAddOnStatusCommand command);
}
