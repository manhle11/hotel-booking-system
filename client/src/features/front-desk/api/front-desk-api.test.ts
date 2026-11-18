import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '../../../lib/axios';
import { getTodayArrivals, getArrivalWork } from './front-desk-api';

vi.mock('../../../lib/axios', () => ({
  apiClient: { get: vi.fn() },
}));

describe('getTodayArrivals', () => {
  beforeEach(() => vi.clearAllMocks());

  it('gọi đúng endpoint với phân trang và unwrap response data', async () => {
    const page = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    };
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: page } });

    await expect(getTodayArrivals({ page: 0, size: 20 })).resolves.toBe(page);

    expect(apiClient.get).toHaveBeenCalledWith('/front-desk/arrivals/today', {
      params: { page: 0, size: 20 },
    });
  });

  it('propagate lỗi đã được API client normalize', async () => {
    const error = new Error('Không thể tải danh sách');
    vi.mocked(apiClient.get).mockRejectedValue(error);

    await expect(getTodayArrivals({ page: 1, size: 20 })).rejects.toBe(error);
  });

  it('sends the group and reason to the server before pagination', async () => {
    const data = {
      content: [],
      page: 2,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: false,
      last: true,
    };
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data } });
    await expect(
      getArrivalWork({ page: 2, size: 20, reason: 'PAST_ETA' }, 'ACTION_REQUIRED'),
    ).resolves.toBe(data);
    expect(apiClient.get).toHaveBeenCalledWith('/front-desk/arrivals/work', {
      params: { page: 2, size: 20, reason: 'PAST_ETA', group: 'ACTION_REQUIRED' },
    });
  });

  it('sends the inclusive resolved date range to the server', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { content: [] } } });
    await getArrivalWork(
      {
        page: 0,
        size: 20,
        resolvedFrom: '2026-10-01',
        resolvedTo: '2026-10-31',
      },
      'RESOLVED',
    );
    expect(apiClient.get).toHaveBeenCalledWith('/front-desk/arrivals/work', {
      params: {
        page: 0,
        size: 20,
        group: 'RESOLVED',
        resolvedFrom: '2026-10-01',
        resolvedTo: '2026-10-31',
      },
    });
  });
});
