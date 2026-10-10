package com.hanbay.hotel.frontdesk.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.frontdesk.dto.response.TodayArrivalResponse;

public interface FrontDeskService {

    PageResponse<TodayArrivalResponse> getTodayArrivals(int page, int size);
}
