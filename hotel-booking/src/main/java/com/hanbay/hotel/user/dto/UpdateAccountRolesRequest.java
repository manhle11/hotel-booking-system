package com.hanbay.hotel.user.dto;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateAccountRolesRequest(
        @NotNull @Size(max = 1, message = "Mỗi tài khoản chỉ được có một vai trò") Set<@NotNull RoleCode> roles) {}
