import { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient, publicApiClient } from '../../lib/axios';
import { roomTypeDetailOptions } from '../room/api/room-detail-api';
import { roomTypeDetailKeys } from '../room/api/room-type-query-keys';
import type { RoomTypeDetail } from '../room/types/room-detail';
import { getFloorPlan, getPublicMap, getStaffMap, invalidateRoomMaps, type FloorPlan } from './api';

vi.mock('../../lib/axios', () => ({
  apiClient: { get: vi.fn() },
  publicApiClient: { get: vi.fn() },
}));

const floor: FloorPlan = {
  floorNumber: 4,
  name: 'Tầng 4',
  gridColumns: 80,
  gridRows: 40,
  version: 1,
  publishedAt: '2026-11-11T10:00:00Z',
  elements: [],
  rooms: [406, 407].map((number) => ({
    key: String(number),
    roomId: String(number),
    roomNumber: String(number),
    roomTypeId: 'deluxe-twin',
    roomTypeName: 'Deluxe Twin',
    operationalStatus: 'AVAILABLE',
  })),
};

let client: QueryClient;
let detail: RoomTypeDetail;

beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  detail = {
    id: 'deluxe-twin',
    name: 'Deluxe Twin',
    shortDescription: null,
    description: null,
    basePrice: 2_500_000,
    areaM2: 36,
    maxGuests: 3,
    capacityAdults: 2,
    capacityChildren: 1,
    images: ['https://example.test/twin.jpg'],
    amenities: [{ id: 'wifi', name: 'Wi-Fi', icon: 'wifi' }],
    beds: [{ bedType: 'SINGLE', quantity: 2 }],
    category: { id: 'deluxe', code: 'DELUXE', name: 'Deluxe' },
  };
  vi.mocked(publicApiClient.get).mockImplementation(async (url) => {
    if (url === '/hotel-map') return { data: { data: { floors: [floor] } } };
    if (url === '/room-types/deluxe-twin') return { data: { data: detail } };
    throw new Error(`Unexpected public request: ${url}`);
  });
  vi.mocked(apiClient.get).mockImplementation(async (url) => {
    if (url === '/front-desk/room-map') return { data: { data: { floors: [floor] } } };
    if (url === '/management/floor-plans/4') return { data: { data: floor } };
    throw new Error(`Unexpected private request: ${url}`);
  });
});

afterEach(() => client.clear());

describe('shared room-type details', () => {
  it('shares one in-flight request across public, staff, floor editor and room detail views', async () => {
    const [publicMap, staffMap, editorFloor, roomDetail] = await Promise.all([
      getPublicMap(client),
      getStaffMap(client),
      getFloorPlan(client, 4),
      client.fetchQuery(roomTypeDetailOptions('deluxe-twin')),
    ]);
    for (const source of [publicMap.floors[0], staffMap.floors[0], editorFloor]) {
      expect(source.rooms[0].typeInfo).toEqual(roomDetail);
      expect(source.rooms[0].areaM2).toBe(36);
      expect(source.rooms[0].categoryCode).toBe('DELUXE');
      expect(source.rooms[1].typeInfo).toEqual(roomDetail);
    }
    expect(
      vi
        .mocked(publicApiClient.get)
        .mock.calls.filter(([url]) => url === '/room-types/deluxe-twin'),
    ).toHaveLength(1);
  });

  it('keeps catalog details cached for operational refreshes and reloads them after catalog invalidation', async () => {
    await getPublicMap(client);
    detail = { ...detail, areaM2: 40, basePrice: 2_700_000 };

    await invalidateRoomMaps(client);
    const operationalRefresh = await getStaffMap(client);
    expect(operationalRefresh.floors[0].rooms[0].areaM2).toBe(36);

    await client.invalidateQueries({ queryKey: roomTypeDetailKeys.all });
    const catalogRefresh = await getPublicMap(client);
    expect(catalogRefresh.floors[0].rooms[0].areaM2).toBe(40);
    expect(catalogRefresh.floors[0].rooms[0].typeInfo?.basePrice).toBe(2_700_000);
    expect(await client.fetchQuery(roomTypeDetailOptions('deluxe-twin'))).toEqual(detail);
    expect(
      vi
        .mocked(publicApiClient.get)
        .mock.calls.filter(([url]) => url === '/room-types/deluxe-twin'),
    ).toHaveLength(2);
  });

  it('keeps the map usable when details fail and retries the missing details on the next refresh', async () => {
    let unavailable = true;
    vi.mocked(publicApiClient.get).mockImplementation(async (url) => {
      if (url === '/hotel-map') return { data: { data: { floors: [floor] } } };
      if (unavailable) throw new Error('Room type unavailable');
      return { data: { data: detail } };
    });
    const first = await getPublicMap(client);
    expect(first.floors[0].rooms).toHaveLength(2);
    expect(first.floors[0].rooms[0].typeInfo).toBeNull();
    expect(first.floors[0].rooms[0].areaM2).toBeNull();

    unavailable = false;
    const recovered = await getPublicMap(client);
    expect(recovered.floors[0].rooms[0].typeInfo).toEqual(detail);
    expect(recovered.floors[0].rooms[0].areaM2).toBe(36);
  });
});
