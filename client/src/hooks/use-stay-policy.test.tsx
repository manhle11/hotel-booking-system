// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import type { PropsWithChildren } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '../lib/axios';
import { DEFAULT_STAY_POLICY, useStayPolicy } from './use-stay-policy';

vi.mock('../lib/axios', () => ({ apiClient: { get: vi.fn() } }));
let client: QueryClient;

afterEach(() => {
  cleanup();
  client.clear();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

function renderPolicy(serverNow: string, cutoff = '23:59:59', cached = false) {
  vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-11-03T00:00:00Z').valueOf());
  const policy = { ...DEFAULT_STAY_POLICY, sameDayBookingCutoff: cutoff, serverNow };
  vi.mocked(apiClient.get).mockResolvedValue({
    data: { data: policy },
  });
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (cached) client.setQueryData(['stay-policy'], policy);
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return renderHook(() => useStayPolicy(), { wrapper });
}

describe('shared hotel stay policy', () => {
  it('uses server time and still permits same-day bookings after the 14:00 check-in time', async () => {
    const { result } = renderPolicy('2026-11-05T15:00:00Z');
    await waitFor(() => expect(result.current.policy.serverNow).toBeDefined());
    expect(result.current.today.format('YYYY-MM-DD')).toBe('2026-11-05');
    expect(result.current.disabledArrivalDate(dayjs('2026-11-04'))).toBe(true);
    expect(result.current.disabledArrivalDate(dayjs('2026-11-05'))).toBe(false);
  });

  it('closes same-day booking at the configured cutoff while allowing tomorrow', async () => {
    const { result } = renderPolicy('2026-11-05T15:00:00Z', '22:00:00');
    await waitFor(() => expect(result.current.policy.serverNow).toBeDefined());
    expect(result.current.sameDayClosed).toBe(true);
    expect(result.current.disabledArrivalDate(dayjs('2026-11-05'))).toBe(true);
    expect(result.current.disabledArrivalDate(dayjs('2026-11-06'))).toBe(false);
  });

  it('uses the new server date immediately when a delayed response arrives at midnight', async () => {
    const { result } = renderPolicy('2026-11-05T17:00:00Z');
    vi.mocked(Date.now).mockReturnValue(new Date('2026-11-03T00:00:05Z').valueOf());
    await waitFor(() => expect(result.current.policy.serverNow).toBeDefined());
    expect(result.current.now.format('YYYY-MM-DD HH:mm:ss')).toBe('2026-11-06 00:00:00');
    expect(result.current.disabledArrivalDate(dayjs('2026-11-05'))).toBe(true);
    expect(result.current.disabledArrivalDate(dayjs('2026-11-06'))).toBe(false);
  });

  it.each([
    ['2026-11-05T14:59:45Z', '22:00:00', '2026-11-05 22:00:15', true],
    ['2026-11-05T16:59:45Z', '23:59:59', '2026-11-06 00:00:15', false],
  ] as const)(
    'advances the server clock from %s across the booking cutoff or midnight',
    (serverNow, cutoff, expectedNow, sameDayClosed) => {
      vi.useFakeTimers();
      const { result } = renderPolicy(serverNow, cutoff, true);
      expect(result.current.today.format('YYYY-MM-DD')).toBe('2026-11-05');
      expect(result.current.sameDayClosed).toBe(false);

      vi.mocked(Date.now).mockReturnValue(new Date('2026-11-03T00:00:30Z').valueOf());
      act(() => vi.advanceTimersByTime(30_000));

      expect(result.current.now.format('YYYY-MM-DD HH:mm:ss')).toBe(expectedNow);
      expect(result.current.sameDayClosed).toBe(sameDayClosed);
      expect(result.current.disabledArrivalDate(dayjs('2026-11-05'))).toBe(true);
      expect(result.current.disabledArrivalDate(dayjs('2026-11-06'))).toBe(false);
    },
  );
});
