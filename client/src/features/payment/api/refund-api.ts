import { apiClient } from '../../../lib/axios';
import type { ApiResponse } from '../../../types/api';
import type { RefundRequest, RefundRequestPage } from '../types/refund';

export async function getPendingRefunds(
  page: number,
  size: number,
  sort?: string,
): Promise<RefundRequestPage> {
  const response = await apiClient.get<ApiResponse<RefundRequestPage>>(
    '/payment-monitoring/refunds',
    {
      params: { page, size, ...(sort ? { sort } : {}) },
    },
  );
  return response.data.data;
}

export async function confirmRefund(
  refundId: string,
  bankReference: string,
): Promise<RefundRequest> {
  const response = await apiClient.post<ApiResponse<RefundRequest>>(
    `/payment-monitoring/refunds/${refundId}/confirm`,
    { bankReference },
  );
  return response.data.data;
}
