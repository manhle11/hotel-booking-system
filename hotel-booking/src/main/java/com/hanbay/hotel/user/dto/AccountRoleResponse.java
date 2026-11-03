package com.hanbay.hotel.user.dto;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;

public record AccountRoleResponse(RoleCode code, String name, String description) {}
