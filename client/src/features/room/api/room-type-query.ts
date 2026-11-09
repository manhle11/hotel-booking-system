import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type { RoomTypeCatalogParams } from '../types/room-type';
import { getRoomTypeCatalog } from './room-type-api';
import { roomTypeCatalogKeys } from './room-type-query-keys';

export { roomTypeCatalogKeys } from './room-type-query-keys';

export function useRoomTypeCatalog(params: RoomTypeCatalogParams) {
  return useQuery({
    queryKey: roomTypeCatalogKeys.list(params),
    queryFn: () => getRoomTypeCatalog(params),
    placeholderData: keepPreviousData,
  });
}
