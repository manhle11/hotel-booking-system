package com.hanbay.hotel.user.mapper;

import com.hanbay.hotel.authentication.domain.entity.Role;
import com.hanbay.hotel.common.mapper.MapperConfig;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.dto.UserResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfig.class)
public interface UserMapper {

    UserResponse toResponse(User user);

    default List<String> mapRoles(Set<Role> roles) {
        if (roles == null) return List.of();
        return roles.stream()
                .map(Role::getCode)
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
