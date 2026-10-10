package com.hanbay.hotel.pricing.mapper;

import com.hanbay.hotel.common.mapper.MapperConfig;
import com.hanbay.hotel.pricing.dto.response.CalculateStayPriceResponse;
import com.hanbay.hotel.pricing.dto.response.PriceRuleManagementResponse;
import com.hanbay.hotel.pricing.dto.response.RoomTypePriceResponse;
import com.hanbay.hotel.pricing.dto.response.RoomTypeSummaryResponse;
import com.hanbay.hotel.pricing.dto.response.StayNightPriceResponse;
import com.hanbay.hotel.pricing.dto.result.NightlyPriceResult;
import com.hanbay.hotel.pricing.dto.result.RoomTypePricingResult;
import com.hanbay.hotel.pricing.dto.result.StayPriceResult;
import com.hanbay.hotel.pricing.service.model.PriceRuleManagementResult;
import com.hanbay.hotel.pricing.service.model.RoomTypeSummaryResult;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfig.class)
public interface PricingMapper {
    CalculateStayPriceResponse toResponse(StayPriceResult result);

    RoomTypePriceResponse toRoomTypeResponse(RoomTypePricingResult result);

    StayNightPriceResponse toNightlyResponse(NightlyPriceResult result);

    PriceRuleManagementResponse toManagementResponse(PriceRuleManagementResult result);

    RoomTypeSummaryResponse toRoomTypeSummaryResponse(RoomTypeSummaryResult result);
}
