import { useQuery } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';

import { apiClient } from '../lib/axios';
import type { ApiResponse } from '../types/api';
import { HOTEL_TIME_ZONE } from '../utils/hotel-time';

export interface StayPolicy {
  zone: string;
  checkInTime: string;
  checkOutTime: string;
  sameDayBookingCutoff: string;
  lateArrivalCutoff: string;
  paymentHoldMinutes: number;
  hotelToday?: string;
  serverNow?: string;
}

export const DEFAULT_STAY_POLICY: StayPolicy = {
  zone: HOTEL_TIME_ZONE,
  checkInTime: '14:00:00',
  checkOutTime: '12:00:00',
  sameDayBookingCutoff: '23:59:59',
  lateArrivalCutoff: '06:00:00',
  paymentHoldMinutes: 15,
};

export function useStayPolicy() {
  const query = useQuery({
    queryKey: ['stay-policy'],
    queryFn: async () => (await apiClient.get<ApiResponse<StayPolicy>>('/stay-policy')).data.data,
    staleTime: 60_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: 'always',
    placeholderData: DEFAULT_STAY_POLICY,
  });
  const policy = query.data ?? DEFAULT_STAY_POLICY;
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => setTick((value) => value + 1), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  void tick;
  const offset = policy.serverNow ? dayjs(policy.serverNow).valueOf() - query.dataUpdatedAt : 0;
  const now = dayjs(Date.now() + offset).tz(policy.zone);
  const today = dayjs(now.format('YYYY-MM-DD'));
  const sameDayClosed = now.format('HH:mm:ss') >= policy.sameDayBookingCutoff;
  const disabledArrivalDate = (date: Dayjs) =>
    date.isBefore(today, 'day') || (date.isSame(today, 'day') && sameDayClosed);

  return { policy, now, today, sameDayClosed, disabledArrivalDate };
}
