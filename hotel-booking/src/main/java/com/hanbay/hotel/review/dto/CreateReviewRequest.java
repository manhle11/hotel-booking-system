package com.hanbay.hotel.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateReviewRequest(
        @JsonDeserialize(using = ReviewRatingDeserializer.class)
                @NotNull(message = "Vui lòng chọn số sao") @Min(value = 1, message = "Số sao phải từ 1 đến 5") @Max(value = 5, message = "Số sao phải từ 1 đến 5") Integer rating,
        @Size(max = 1000, message = "Nhận xét tối đa 1.000 ký tự") String comment) {}
