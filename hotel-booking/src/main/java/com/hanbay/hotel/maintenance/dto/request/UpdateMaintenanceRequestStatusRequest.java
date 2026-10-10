package com.hanbay.hotel.maintenance.dto.request;

import com.hanbay.hotel.maintenance.domain.MaintenanceRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Trạng thái xử lý mới của yêu cầu bảo trì")
public record UpdateMaintenanceRequestStatusRequest(
        @NotNull(message = "Trạng thái yêu cầu bảo trì không được để trống") MaintenanceRequestStatus status) {}
