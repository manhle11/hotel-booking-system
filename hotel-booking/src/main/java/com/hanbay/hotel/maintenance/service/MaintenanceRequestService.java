package com.hanbay.hotel.maintenance.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.maintenance.dto.request.CreateMaintenanceRequestRequest;
import com.hanbay.hotel.maintenance.dto.request.UpdateMaintenanceRequestStatusRequest;
import com.hanbay.hotel.maintenance.dto.response.MaintenanceRequestListItemResponse;
import com.hanbay.hotel.maintenance.dto.response.MaintenanceRequestResponse;
import java.util.UUID;

public interface MaintenanceRequestService {

    MaintenanceRequestResponse create(CreateMaintenanceRequestRequest request);

    PageResponse<MaintenanceRequestListItemResponse> findAll(int page, int size);

    PageResponse<MaintenanceRequestListItemResponse> findAll(int page, int size, String sort);

    MaintenanceRequestResponse findById(UUID id);

    MaintenanceRequestResponse updateStatus(UUID id, UpdateMaintenanceRequestStatusRequest request);
}
