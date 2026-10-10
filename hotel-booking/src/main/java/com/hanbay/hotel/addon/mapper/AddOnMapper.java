package com.hanbay.hotel.addon.mapper;

import com.hanbay.hotel.addon.domain.AddOnService;
import com.hanbay.hotel.addon.dto.response.AddOnCatalogResponse;
import com.hanbay.hotel.addon.dto.response.AddOnManagementResponse;
import com.hanbay.hotel.addon.service.model.AddOnManagementResult;
import com.hanbay.hotel.common.mapper.MapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfig.class)
public interface AddOnMapper {
    AddOnCatalogResponse toCatalogResponse(AddOnService service);

    AddOnManagementResponse toManagementResponse(AddOnManagementResult result);
}
