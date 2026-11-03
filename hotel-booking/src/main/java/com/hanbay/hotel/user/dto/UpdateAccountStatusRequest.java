package com.hanbay.hotel.user.dto;

import com.hanbay.hotel.authentication.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull UserStatus status) {}
