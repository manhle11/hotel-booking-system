import { apiClient } from '../../../lib/axios';
import type { ApiResponse } from '../../../types/api';
import type { TodayArrivalsPage, TodayArrivalsParams } from '../types/today-arrivals';
import type { ArrivalGroup } from '../types/arrival-handling';

export async function getArrivalWork(
  params: TodayArrivalsParams,
  group: ArrivalGroup,
): Promise<TodayArrivalsPage> {
  return (
    await apiClient.get<ApiResponse<TodayArrivalsPage>>('/front-desk/arrivals/work', {
      params: { ...params, group },
    })
  ).data.data;
}

export async function getTodayArrivals(params: TodayArrivalsParams): Promise<TodayArrivalsPage> {
  const response = await apiClient.get<ApiResponse<TodayArrivalsPage>>(
    '/front-desk/arrivals/today',
    { params },
  );
  return response.data.data;
}

export async function getOverdueArrivals(params: TodayArrivalsParams): Promise<TodayArrivalsPage> {
  const response = await apiClient.get<ApiResponse<TodayArrivalsPage>>(
    '/front-desk/arrivals/overdue',
    { params },
  );
  return response.data.data;
}
