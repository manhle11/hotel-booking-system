package com.hanbay.hotel.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteReviewRequest(
        @NotBlank(message = "Vui lòng nhập lý do xóa") @Size(max = 500, message = "Lý do xóa tối đa 500 ký tự") String reason) {}
