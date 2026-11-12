import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { apiClient, publicApiClient } from '../../lib/axios';
import type { ApiResponse } from '../../types/api';
import { roomTypeDetailOptions } from '../room/api/room-detail-api';
import type { RoomTypeDetail } from '../room/types/room-detail';

export const roomStatuses = {
  AVAILABLE: 'Sẵn sàng',
  OCCUPIED: 'Đang có khách',
  DIRTY: 'Chờ dọn',
  CLEANING: 'Đang dọn',
  MAINTENANCE: 'Bảo trì',
  OUT_OF_SERVICE: 'Ngừng phục vụ',
} as const;
export type RoomStatus = keyof typeof roomStatuses;
export type ElementKind = 'ROOM' | 'CORRIDOR' | 'ELEVATOR' | 'STAIRS' | 'COMMON_AREA';
export interface MapRoom {
  key: string;
  roomNumber: string;
  roomTypeId: string;
  roomTypeName: string;
  areaM2?: number | null;
  categoryCode?: string | null;
  typeInfo?: Partial<RoomTypeDetail> | null;
  roomId?: string;
  operationalStatus?: RoomStatus;
  bedType?: string | null;
  thumbnailUrl?: string | null;
  bookable?: boolean;
}
export interface LayoutElement {
  id: string;
  kind: ElementKind;
  roomKey: string | null;
  label: string | null;
  x: number;
  y: number;
  width: number;
  height: number;
}
export interface FloorPlan {
  floorNumber: number;
  name: string;
  gridColumns: number;
  gridRows: number;
  version: number;
  publishedAt: string | null;
  elements: LayoutElement[];
  rooms: MapRoom[];
}
export interface HotelMapResponse {
  floors: FloorPlan[];
  serverNow?: string;
}
export interface WriteElement {
  kind: ElementKind;
  roomId: string | null;
  label: string;
  x: number;
  y: number;
  width: number;
  height: number;
}
export interface WritePlan {
  name: string;
  gridColumns: number;
  gridRows: number;
  expectedVersion: number;
  elements: WriteElement[];
}
async function attachAreas(client: QueryClient, floors: FloorPlan[]): Promise<FloorPlan[]> {
  const ids = [
    ...new Set(
      floors.flatMap((f) =>
        f.rooms.filter((r) => r.typeInfo === undefined).map((r) => r.roomTypeId),
      ),
    ),
  ];
  const details = new Map(
    await Promise.all(
      ids.map(async (id) => {
        const info = await client
          .fetchQuery({ ...roomTypeDetailOptions(id), retry: false })
          .catch(() => null);
        return [id, info] as const;
      }),
    ),
  );
  return floors.map((f) => ({
    ...f,
    rooms: f.rooms.map((room) => {
      const info = room.typeInfo ?? details.get(room.roomTypeId) ?? null;
      const area = info?.areaM2;
      return {
        ...room,
        typeInfo: info,
        categoryCode: room.categoryCode ?? info?.category?.code ?? null,
        areaM2:
          room.areaM2 !== undefined
            ? room.areaM2
            : typeof area === 'number' && Number.isFinite(area) && area > 0
              ? area
              : null,
      };
    }),
  }));
}
export const getPublicMap = async (client: QueryClient) => {
  const data = (await publicApiClient.get<ApiResponse<HotelMapResponse>>('/hotel-map')).data.data;
  return { ...data, floors: await attachAreas(client, data.floors) };
};
export const getStaffMap = async (client: QueryClient) => {
  const data = (await apiClient.get<ApiResponse<HotelMapResponse>>('/front-desk/room-map')).data
    .data;
  return { ...data, floors: await attachAreas(client, data.floors) };
};
export const getFloorPlan = async (client: QueryClient, floor: number) => {
  const data = (await apiClient.get<ApiResponse<FloorPlan>>(`/management/floor-plans/${floor}`))
    .data.data;
  return (await attachAreas(client, [data]))[0];
};
export const publishFloorPlan = async (client: QueryClient, floor: number, plan: WritePlan) => {
  const data = (
    await apiClient.put<ApiResponse<FloorPlan>>(`/management/floor-plans/${floor}`, plan)
  ).data.data;
  return (await attachAreas(client, [data]))[0];
};
export const usePublicMap = () => {
  const client = useQueryClient();
  return useQuery({ queryKey: ['hotel-map'], queryFn: () => getPublicMap(client) });
};
export const useStaffMap = (enabled = true) => {
  const client = useQueryClient();
  return useQuery({
    queryKey: ['staff-room-map'],
    queryFn: () => getStaffMap(client),
    enabled,
    refetchInterval: 15_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
  });
};
export const invalidateRoomMaps = (client: QueryClient) =>
  Promise.all(
    [
      'hotel-map',
      'staff-room-map',
      'floor-plan',
      'available-physical-rooms',
      'available-room-types',
      'managed-rooms',
    ].map((key) => client.invalidateQueries({ queryKey: [key] })),
  );
