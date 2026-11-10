import { beforeEach, expect, it, vi } from 'vitest';
import { apiClient, publicApiClient } from '../../lib/axios';
import {
  createReview,
  deleteReview,
  getManagedReviews,
  getManagedReviewSummary,
  getReviewEligibility,
  getReviewSummary,
  getRoomReviews,
  replyReview,
} from './api';
vi.mock('../../lib/axios', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  publicApiClient: { get: vi.fn() },
}));
beforeEach(() => {
  vi.resetAllMocks();
  for (const fn of [apiClient.get, apiClient.post, apiClient.put, publicApiClient.get])
    vi.mocked(fn).mockResolvedValue({ data: { data: {} } });
});
it('reads public reviews and summary without requiring an account', async () => {
  await getRoomReviews('deluxe', 2, 1);
  expect(publicApiClient.get).toHaveBeenCalledWith('/room-types/deluxe/reviews', {
    params: { page: 2, size: 6, rating: 1 },
  });
  await getReviewSummary('deluxe');
  expect(publicApiClient.get).toHaveBeenCalledWith('/room-types/deluxe/reviews/summary');
});
it('uses the authenticated client without any guest access headers', async () => {
  await getReviewEligibility('booking');
  expect(apiClient.get).toHaveBeenCalledWith('/bookings/booking/review-eligibility');
  await createReview('booking', { rating: 5, comment: 'Tốt' });
  expect(apiClient.post).toHaveBeenCalledWith('/bookings/booking/reviews', {
    rating: 5,
    comment: 'Tốt',
  });
});
it('filters manager history and sends the required deletion reason', async () => {
  await getManagedReviews(1, { roomTypeId: 'deluxe', rating: 2, deleted: true });
  expect(apiClient.get).toHaveBeenCalledWith('/management/reviews', {
    params: { page: 1, size: 20, roomTypeId: 'deluxe', rating: 2, deleted: true },
  });
  await deleteReview({ id: 'review', reason: 'Spam' });
  expect(apiClient.delete).toHaveBeenCalledWith('/management/reviews/review', {
    data: { reason: 'Spam' },
  });
});
it('uses the same filters for the paged list and the complete manager summary', async () => {
  const filters = {
    roomTypeId: 'deluxe',
    replied: false,
    search: 'RV-ORDER-17',
    fromDate: '2026-11-01',
    toDate: '2026-11-09',
  };
  await getManagedReviews(2, filters);
  await getManagedReviewSummary(filters);
  expect(apiClient.get).toHaveBeenCalledWith('/management/reviews', {
    params: { page: 2, size: 20, deleted: false, ...filters },
  });
  expect(apiClient.get).toHaveBeenCalledWith('/management/reviews/summary', {
    params: { deleted: false, ...filters },
  });
});
it('sends hotel replies to their own endpoint', async () => {
  await replyReview('review', 'Cảm ơn bạn.');
  expect(apiClient.put).toHaveBeenCalledWith('/management/reviews/review/reply', {
    reply: 'Cảm ơn bạn.',
  });
});
