package com.hanbay.hotel.housekeeping.mapper;

import com.hanbay.hotel.common.mapper.MapperConfig;
import com.hanbay.hotel.housekeeping.dto.response.DirtyRoomResponse;
import com.hanbay.hotel.room.domain.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfig.class)
public interface DirtyRoomMapper {

    @Mapping(target = "roomId", source = "id")
    DirtyRoomResponse toResponse(Room room);
}
