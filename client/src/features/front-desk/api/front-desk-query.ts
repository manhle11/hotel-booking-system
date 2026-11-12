import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import type { TodayArrivalsParams, ArrivalView } from '../types/today-arrivals';
import { getTodayArrivals, getOverdueArrivals } from './front-desk-api';

export const frontDeskKeys = {
  all: ['front-desk'] as const,
  arrivals: ['front-desk', 'today-arrivals'] as const,
  todayArrivals: (params: TodayArrivalsParams) => [...frontDeskKeys.arrivals, params] as const,
  overdueArrivals: (params: TodayArrivalsParams) =>
    [...frontDeskKeys.all, 'overdue-arrivals', params] as const,
};

const CHECK_IN_UPDATE_KEY = 'hanbay:check-in-completed';

export function refreshArrivalsAfterCheckIn(client: QueryClient) {
  try {
    window.localStorage.setItem(CHECK_IN_UPDATE_KEY, crypto.randomUUID());
  } catch {
    // Polling and focus refresh still work when browser storage is unavailable.
  }
  return client.invalidateQueries({ queryKey: frontDeskKeys.all });
}

export function useArrivals(params: TodayArrivalsParams, view: ArrivalView = 'today') {
  const client = useQueryClient();
  const queryKey =
    view === 'today' ? frontDeskKeys.todayArrivals(params) : frontDeskKeys.overdueArrivals(params);
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === CHECK_IN_UPDATE_KEY && event.newValue) {
        void client.invalidateQueries({ queryKey: frontDeskKeys.all });
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [client]);

  return useQuery({
    queryKey,
    queryFn: () => (view === 'today' ? getTodayArrivals(params) : getOverdueArrivals(params)),
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === queryKey[1] ? previous : undefined,
    staleTime: 0,
    refetchInterval: 5_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
  });
}
