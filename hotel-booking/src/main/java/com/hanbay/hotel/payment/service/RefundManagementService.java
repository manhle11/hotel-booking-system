package com.hanbay.hotel.payment.service;

import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.payment.dto.request.ConfirmRefundRequest;
import com.hanbay.hotel.payment.dto.response.RefundRequestResponse;
import java.util.UUID;

public interface RefundManagementService {
    PageResponse<RefundRequestResponse> getPendingRefunds(int page, int size);

    PageResponse<RefundRequestResponse> getPendingRefunds(int page, int size, String sort);

    RefundRequestResponse confirmRefund(UUID refundId, ConfirmRefundRequest request);
}
