import { apiClient } from '../../../lib/axios';
import type { ApiResponse } from '../../../types/api';

export interface BookingPolicySummary {
  bookingCode: string;
  version: string | null;
  terms: string | null;
  acceptedAt: string | null;
  firstNightAmount: number | null;
  accruedFee: number;
  freeCancellationUntil: string;
  halfFeeUntil: string;
  zone: string;
  legacy: boolean;
  settlementReviewRequired: boolean;
  fees: { event: string; amount: number; total: number; at: string; reason: string }[];
}
export async function getBookingPolicy(code: string, accessToken?: string) {
  return (
    await apiClient.get<ApiResponse<BookingPolicySummary>>(
      `/bookings/${encodeURIComponent(code)}/booking-policy`,
      {
        headers: accessToken ? { 'X-Booking-Access-Token': accessToken } : undefined,
      },
    )
  ).data.data;
}
