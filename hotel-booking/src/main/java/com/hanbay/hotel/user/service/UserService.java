package com.hanbay.hotel.user.service;

import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.dto.UserResponse;
import com.hanbay.hotel.user.service.model.UpdateUserCommand;
import java.util.Optional;

public interface UserService {

    Optional<User> findByIdentifier(String identifier);

    UserResponse getCurrentProfile();

    UserResponse updateCurrentProfile(UpdateUserCommand command);
}
