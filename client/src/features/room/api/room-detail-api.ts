import { queryOptions } from '@tanstack/react-query';
import { publicApiClient } from '../../../lib/axios';

import type { ApiEnvelope, RoomTypeDetail } from '../types/room-detail';
import { roomTypeDetailKeys } from './room-type-query-keys';

export async function getRoomTypeDetail(roomTypeId: string): Promise<RoomTypeDetail> {
  const response = await publicApiClient.get<ApiEnvelope<RoomTypeDetail>>(
    `/room-types/${encodeURIComponent(roomTypeId)}`,
  );
  return response.data.data;
}

export const roomTypeDetailOptions = (roomTypeId: string) =>
  queryOptions({
    queryKey: roomTypeDetailKeys.detail(roomTypeId),
    queryFn: () => getRoomTypeDetail(roomTypeId),
    staleTime: 60_000,
  });
