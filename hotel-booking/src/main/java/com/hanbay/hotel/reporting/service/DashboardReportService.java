package com.hanbay.hotel.reporting.service;

import com.hanbay.hotel.reporting.dto.request.DashboardReportRequest;
import com.hanbay.hotel.reporting.dto.response.DashboardReportResponse;

public interface DashboardReportService {

    DashboardReportResponse getDashboardReport(DashboardReportRequest request);
}
