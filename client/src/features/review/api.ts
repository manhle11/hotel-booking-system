import { apiClient, publicApiClient } from '../../lib/axios';
import type { ApiResponse, PageResponse } from '../../types/api';

export interface RoomReview {
  id: string;
  roomTypeId: string;
  roomTypeName: string;
  authorName: string;
  rating: number;
  comment: string | null;
  createdAt: string;
  hotelReply?: string | null;
  repliedAt?: string | null;
}
export interface ReviewStay {
  bookingCode: string;
  roomNumber: string | null;
  checkInDate: string;
  checkOutDate: string;
  checkedInAt: string | null;
  checkedOutAt: string | null;
}
export interface ManagedReview {
  review: RoomReview;
  bookingCode: string;
  orderCode: string | null;
  guestName: string;
  stays: ReviewStay[];
  deletedAt: string | null;
  deletedByName: string | null;
  deleteReason: string | null;
  repliedByName: string | null;
}
export interface ReviewSummary {
  averageRating: number | null;
  totalReviews: number;
}
export interface ManagedReviewFilters {
  roomTypeId?: string;
  rating?: number;
  deleted?: boolean;
  replied?: boolean;
  search?: string;
  fromDate?: string;
  toDate?: string;
}
export interface ManagedReviewSummary {
  totalReviews: number;
  unrepliedReviews: number;
  repliedReviews: number;
  lowRatingReviews: number;
}
export interface ReviewEligibility {
  canReview: boolean;
  reasonCode: string | null;
  message: string;
  deadline: string | null;
  review: RoomReview | null;
}
export async function getReviewSummary(roomTypeId: string): Promise<ReviewSummary> {
  return (
    await publicApiClient.get<ApiResponse<ReviewSummary>>(
      `/room-types/${roomTypeId}/reviews/summary`,
    )
  ).data.data;
}
export async function getRoomReviews(roomTypeId: string, page: number, rating?: number) {
  return (
    await publicApiClient.get<ApiResponse<PageResponse<RoomReview>>>(
      `/room-types/${roomTypeId}/reviews`,
      {
        params: { page, size: 6, rating },
      },
    )
  ).data.data;
}
export async function getReviewEligibility(bookingId: string): Promise<ReviewEligibility> {
  return (
    await apiClient.get<ApiResponse<ReviewEligibility>>(`/bookings/${bookingId}/review-eligibility`)
  ).data.data;
}
export async function createReview(
  bookingId: string,
  request: { rating: number; comment?: string },
): Promise<RoomReview> {
  return (await apiClient.post<ApiResponse<RoomReview>>(`/bookings/${bookingId}/reviews`, request))
    .data.data;
}
export async function getManagedReviews(page: number, filters: ManagedReviewFilters = {}) {
  return (
    await apiClient.get<ApiResponse<PageResponse<ManagedReview>>>('/management/reviews', {
      params: { page, size: 20, deleted: false, ...filters },
    })
  ).data.data;
}
export async function getManagedReviewSummary(
  filters: ManagedReviewFilters = {},
): Promise<ManagedReviewSummary> {
  return (
    await apiClient.get<ApiResponse<ManagedReviewSummary>>('/management/reviews/summary', {
      params: { deleted: false, ...filters },
    })
  ).data.data;
}
export async function deleteReview({ id, reason }: { id: string; reason: string }): Promise<void> {
  await apiClient.delete(`/management/reviews/${id}`, { data: { reason } });
}

export async function replyReview(id: string, reply: string): Promise<RoomReview> {
  return (
    await apiClient.put<ApiResponse<RoomReview>>(`/management/reviews/${id}/reply`, { reply })
  ).data.data;
}
export async function removeReviewReply(id: string): Promise<void> {
  await apiClient.delete(`/management/reviews/${id}/reply`);
}
