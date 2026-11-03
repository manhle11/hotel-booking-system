package com.hanbay.hotel.user.dto;

import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateAccountRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại phải có từ 8 đến 15 chữ số") String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull @Size(max = 1, message = "Mỗi tài khoản chỉ được có một vai trò") Set<@NotNull RoleCode> roles) {}
