package com.hanbay.hotel.pricing.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.pricing.service.model.CreatePriceRuleCommand;
import com.hanbay.hotel.pricing.service.model.PriceRuleManagementResult;
import com.hanbay.hotel.pricing.service.model.PriceRuleSearchCriteria;
import com.hanbay.hotel.pricing.service.model.UpdatePriceRuleCommand;
import com.hanbay.hotel.pricing.service.model.UpdatePriceRuleStatusCommand;
import java.util.UUID;

public interface PriceRuleManagementService {

    PageResponse<PriceRuleManagementResult> findAll(PriceRuleSearchCriteria criteria);

    PriceRuleManagementResult findById(UUID id);

    PriceRuleManagementResult create(CreatePriceRuleCommand command);

    PriceRuleManagementResult update(UUID id, UpdatePriceRuleCommand command);

    PriceRuleManagementResult updateStatus(UUID id, UpdatePriceRuleStatusCommand command);
}
