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
  youngChildUnderAge?: number;
  adultFromAge?: number;
  childrenShareExistingBeds?: boolean;
}

export const DEFAULT_STAY_POLICY: StayPolicy = {
  zone: HOTEL_TIME_ZONE,
  checkInTime: '14:00:00',
  checkOutTime: '12:00:00',
  sameDayBookingCutoff: '23:59:59',
  lateArrivalCutoff: '06:00:00',
  paymentHoldMinutes: 15,
  youngChildUnderAge: 6,
  adultFromAge: 12,
  childrenShareExistingBeds: true,
};

export function useHotelPolicy() {
  const query = useHotelPolicyQuery();
  return query.data ?? DEFAULT_STAY_POLICY;
}

function useHotelPolicyQuery(refetchInterval: number | false = false) {
  return useQuery({
    queryKey: ['stay-policy'],
    queryFn: async () => (await apiClient.get<ApiResponse<StayPolicy>>('/stay-policy')).data.data,
    staleTime: 60_000,
    refetchInterval,
    refetchOnWindowFocus: 'always',
    placeholderData: DEFAULT_STAY_POLICY,
  });
}

export function useStayPolicy() {
  const query = useHotelPolicyQuery(60_000);
  const policy = query.data ?? DEFAULT_STAY_POLICY;
  const [clientNow, setClientNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setClientNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  const offset = policy.serverNow ? dayjs(policy.serverNow).valueOf() - query.dataUpdatedAt : 0;
  // A fresh response must use its server timestamp even before the next clock tick.
  const now = dayjs(Math.max(clientNow, query.dataUpdatedAt) + offset).tz(policy.zone);
  const today = dayjs(now.format('YYYY-MM-DD'));
  const sameDayClosed = now.format('HH:mm:ss') >= policy.sameDayBookingCutoff;
  const disabledArrivalDate = (date: Dayjs) =>
    date.isBefore(today, 'day') || (date.isSame(today, 'day') && sameDayClosed);

  return { policy, now, today, sameDayClosed, disabledArrivalDate };
}
