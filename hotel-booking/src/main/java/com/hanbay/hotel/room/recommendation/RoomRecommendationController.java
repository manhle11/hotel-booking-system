package com.hanbay.hotel.room.recommendation;

import com.hanbay.hotel.ai.config.PolicyRagProperties;
import com.hanbay.hotel.authentication.service.AuthenticationRateLimitService;
import com.hanbay.hotel.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix:/api/v1}/room-recommendations")
@RequiredArgsConstructor
public class RoomRecommendationController {
    private final RoomRecommendationService service;
    private final AuthenticationRateLimitService rateLimit;
    private final PolicyRagProperties aiProperties;

    @PostMapping
    public ApiResponse<RecommendationModels.Result> recommend(
            @RequestBody RecommendationModels.Criteria criteria, HttpServletRequest request) {
        rateLimit.check(
                "room-recommendations",
                request.getUserPrincipal().getName(),
                aiProperties.rateLimitPerMinute(),
                Duration.ofMinutes(1));
        return ApiResponse.success(service.recommend(criteria));
    }
}
