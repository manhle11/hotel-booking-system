package com.hanbay.hotel.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại phải có từ 8 đến 15 chữ số") String phone) {}
