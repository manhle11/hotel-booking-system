import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../../lib/axios';
import { decideArrivalHold, holdArrival, updateArrivalEstimate } from './arrival-handling-api';

vi.mock('../../../lib/axios', () => ({ apiClient: { post: vi.fn() } }));
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: {} } });
});
const codes = ['BK-A', 'BK-B', 'BK-C', 'BK-D'];
const expectedAt = '2026-11-14T01:50:00Z';
const until = '2026-11-14T02:00:00Z';
describe('explicit room selection for arrival handling', () => {
  it('sends all four selected codes for a shared ETA', async () => {
    await updateArrivalEstimate('BK-A', expectedAt, 'Cả đoàn đến chung', codes);
    expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/bookings/BK-A/arrival-estimate', {
      expectedAt,
      note: 'Cả đoàn đến chung',
      bookingCodes: codes,
    });
  });
  it('keeps one-room callers compatible without inventing an order scope', async () => {
    await updateArrivalEstimate('BK-A', expectedAt, 'Một phòng');
    expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/bookings/BK-A/arrival-estimate', {
      expectedAt,
      note: 'Một phòng',
    });
  });
  it('preserves the selected rooms when requesting and deciding a late hold', async () => {
    await holdArrival('BK-A', until, 'Xin nhận sau hạn', expectedAt, codes);
    expect(apiClient.post).toHaveBeenNthCalledWith(1, '/bookings/BK-A/arrival-hold', {
      until,
      expectedAt,
      note: 'Xin nhận sau hạn',
      bookingCodes: codes,
    });
    await decideArrivalHold('BK-A', {
      requestedUntil: until,
      approve: true,
      note: 'Đồng ý',
      bookingCodes: codes,
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, '/bookings/BK-A/arrival-hold/decision', {
      requestedUntil: until,
      approve: true,
      note: 'Đồng ý',
      bookingCodes: codes,
    });
  });
});
