// @vitest-environment jsdom
import {
  QueryClient,
  QueryClientProvider,
  focusManager,
  onlineManager,
} from '@tanstack/react-query';
import {
  act,
  cleanup,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getTodayArrivals, getOverdueArrivals } from '../api/front-desk-api';
import { refreshArrivalsAfterCheckIn, useArrivals } from '../api/front-desk-query';
import type { TodayArrival } from '../types/today-arrivals';
import { TodayArrivalsPage } from './today-arrivals-page';

vi.mock('../api/front-desk-api', () => ({
  getTodayArrivals: vi.fn(),
  getOverdueArrivals: vi.fn(),
}));

vi.mock('../../authentication/providers/auth-context', () => ({
  useAuth: () => ({ roles: ['RECEPTIONIST'] }),
}));

const arrival: TodayArrival = {
  bookingId: 'arrival-1',
  bookingCode: 'BK-ARRIVAL-1',
  primaryGuest: { fullName: 'Khách A', phone: null, email: null },
  roomType: { id: 'twin', code: 'DELUXE_TWIN', name: 'Deluxe Twin' },
  checkInDate: '2026-11-12',
  checkOutDate: '2026-11-13',
  adultCount: 2,
  childCount: 0,
  bookingStatus: 'CONFIRMED',
  createdAt: '2026-11-11T10:00:00Z',
};

let client: QueryClient;
let pendingArrivals: TodayArrival[];

beforeEach(() => {
  vi.clearAllMocks();
  focusManager.setFocused(true);
  onlineManager.setOnline(true);
  client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 60_000, refetchOnWindowFocus: false },
    },
  });
  pendingArrivals = [arrival, { ...arrival, bookingId: 'arrival-2', bookingCode: 'BK-ARRIVAL-2' }];
  vi.mocked(getTodayArrivals).mockImplementation(async ({ page, size }) => ({
    content: pendingArrivals,
    page,
    size,
    totalElements: pendingArrivals.length,
    totalPages: pendingArrivals.length ? 1 : 0,
    first: true,
    last: true,
  }));
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
});

afterEach(() => {
  cleanup();
  client.clear();
  focusManager.setFocused(undefined);
  onlineManager.setOnline(true);
  vi.useRealTimers();
  vi.restoreAllMocks();
});

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

async function openArrivals() {
  render(<TodayArrivalsPage />, { wrapper });
  await screen.findByText(arrival.bookingCode);
}

function spyOnStorageWrite() {
  // jsdom's Storage proxy requires a prototype spy; the fallback has its own prototype.
  const storagePrototype = Object.getPrototypeOf(window.localStorage) as Storage;
  return vi.spyOn(storagePrototype, 'setItem');
}

describe('live arrivals', () => {
  it('does not display today bookings under the overdue tab while waiting for its response', async () => {
    let finish: (data: Awaited<ReturnType<typeof getOverdueArrivals>>) => void = () => {};
    vi.mocked(getOverdueArrivals).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    await openArrivals();
    fireEvent.click(screen.getByRole('tab', { name: 'Chưa đến / quá giờ' }));
    expect(screen.queryByText(arrival.bookingCode)).toBeNull();
    await act(async () =>
      finish({
        content: [{ ...arrival, bookingCode: 'BK-OVERDUE' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }),
    );
    expect(await screen.findByText('BK-OVERDUE')).toBeTruthy();
  });

  it('removes a checked-in booking and updates the total without reloading the page', async () => {
    await openArrivals();
    pendingArrivals = pendingArrivals.slice(1);

    await act(() => refreshArrivalsAfterCheckIn(client));

    await waitFor(() => expect(screen.queryByText(arrival.bookingCode)).toBeNull());
    expect(screen.getByText('BK-ARRIVAL-2')).toBeTruthy();
    expect(screen.getByText('1', { selector: 'strong' })).toBeTruthy();
  });

  it('refreshes an open arrivals tab when another tab announces a completed check-in', async () => {
    await openArrivals();
    pendingArrivals = pendingArrivals.slice(1);
    const storage = spyOnStorageWrite();
    const otherTab = new QueryClient();
    try {
      await refreshArrivalsAfterCheckIn(otherTab);
      expect(storage).toHaveBeenCalledTimes(1);
      expect(getTodayArrivals).toHaveBeenCalledTimes(1);
      const [key, newValue] = storage.mock.calls[0];
      act(() => window.dispatchEvent(new StorageEvent('storage', { key, newValue })));

      await waitFor(() => expect(screen.queryByText(arrival.bookingCode)).toBeNull());
      expect(screen.getByText('BK-ARRIVAL-2')).toBeTruthy();
    } finally {
      otherTab.clear();
    }
  });

  it('detects check-ins from another browser during the automatic refresh', async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useArrivals({ page: 0, size: 20 }), { wrapper });
    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(result.current.data?.content).toHaveLength(2);
    pendingArrivals = pendingArrivals.slice(1);

    await act(() => vi.advanceTimersByTimeAsync(5_001));

    expect(result.current.data?.content.map((booking) => booking.bookingCode)).toEqual([
      'BK-ARRIVAL-2',
    ]);
    expect(result.current.data?.totalElements).toBe(1);
  });

  it.each(['focus', 'reconnect'] as const)('refreshes immediately on %s', async (event) => {
    await openArrivals();
    pendingArrivals = pendingArrivals.slice(1);
    act(() => {
      if (event === 'focus') {
        focusManager.setFocused(false);
        focusManager.setFocused(true);
      } else {
        onlineManager.setOnline(false);
        onlineManager.setOnline(true);
      }
    });

    await waitFor(() => expect(screen.queryByText(arrival.bookingCode)).toBeNull());
    expect(screen.getByText('BK-ARRIVAL-2')).toBeTruthy();
  });

  it('still updates arrivals when the browser blocks storage', async () => {
    await openArrivals();
    pendingArrivals = [];
    const storage = spyOnStorageWrite().mockImplementation(() => {
      throw new Error('Storage blocked');
    });

    await act(() => refreshArrivalsAfterCheckIn(client));

    expect(storage).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('Hôm nay không có khách dự kiến nhận phòng')).toBeTruthy();
    expect(screen.queryByText(arrival.bookingCode)).toBeNull();
  });
});
