package com.hanbay.hotel.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyReviewRequest(
        @NotBlank(message = "Vui lòng nhập phản hồi") @Size(max = 1000, message = "Phản hồi tối đa 1.000 ký tự") String reply) {}
