// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, renderHook, waitFor } from '@testing-library/react';
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
});

function renderPolicy(serverNow: string, cutoff = '23:59:59') {
  vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-11-03T00:00:00Z').valueOf());
  vi.mocked(apiClient.get).mockResolvedValue({
    data: {
      data: {
        ...DEFAULT_STAY_POLICY,
        sameDayBookingCutoff: cutoff,
        serverNow,
      },
    },
  });
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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
});
