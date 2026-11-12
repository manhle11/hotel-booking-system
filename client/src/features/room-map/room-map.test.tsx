// @vitest-environment jsdom
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { apiClient, publicApiClient } from '../../lib/axios';
import { HotelFloorMap } from './hotel-floor-map';
import { PublicHotelMapPage } from './public-hotel-map-page';
import { FloorPlanManagementPage } from './floor-plan-management-page';
import { FloorPlanEditor } from './floor-plan-editor';
import { StaffRoomMapPage } from './staff-room-map-page';
import { getPublicMap, type FloorPlan } from './api';
import { createLayoutTemplate } from './floor-plan-layout';
import { RoomMapDetails } from './room-map-details';

vi.mock('../../lib/axios', () => ({
  apiClient: { get: vi.fn(), put: vi.fn() },
  publicApiClient: { get: vi.fn() },
}));
vi.mock('../../components/layout/MainHeader', () => ({
  MainHeader: () => <header>HANBAY</header>,
}));
vi.mock('../../components/layout/MainFooter', () => ({
  MainFooter: () => <footer>HANBAY</footer>,
}));
vi.mock('../authentication/providers/auth-context', () => ({
  useAuth: () => ({ roles: ['RECEPTIONIST'] }),
}));

const floor: FloorPlan = {
  floorNumber: 3,
  name: 'Tầng 3',
  gridColumns: 24,
  gridRows: 16,
  version: 1,
  publishedAt: '2026-11-11T01:00:00Z',
  rooms: [
    {
      key: 'room-306',
      roomId: 'room-306',
      roomNumber: '306',
      roomTypeId: 'twin',
      roomTypeName: 'Superior Twin',
      areaM2: 30,
      operationalStatus: 'AVAILABLE',
      bookable: true,
    },
    {
      key: 'room-307',
      roomId: 'room-307',
      roomNumber: '307',
      roomTypeId: 'twin',
      roomTypeName: 'Superior Twin',
      areaM2: 30,
      operationalStatus: 'OCCUPIED',
      bookable: true,
    },
    {
      key: 'room-301',
      roomId: 'room-301',
      roomNumber: '301',
      roomTypeId: 'queen',
      roomTypeName: 'Superior Queen',
      areaM2: 30,
      operationalStatus: 'AVAILABLE',
      bookable: true,
    },
  ],
  elements: [
    {
      id: 'node-306',
      kind: 'ROOM',
      roomKey: 'room-306',
      label: null,
      x: 0,
      y: 0,
      width: 4,
      height: 3,
    },
    {
      id: 'node-307',
      kind: 'ROOM',
      roomKey: 'room-307',
      label: null,
      x: 5,
      y: 0,
      width: 4,
      height: 3,
    },
  ],
};

beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn(() => ({
      matches: false,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() {},
    })),
  });
});
beforeEach(() => {
  vi.resetAllMocks();
});
afterEach(cleanup);
function page(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return {
    ...render(
      <MemoryRouter>
        <QueryClientProvider client={client}>
          <ConfigProvider>
            <App>{ui}</App>
          </ConfigProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    ),
    client,
  };
}

describe('HotelFloorMap', () => {
  it('keeps unplaced rooms visible and blocks rooms ineligible for the selected booking', () => {
    const select = vi.fn();
    page(
      <HotelFloorMap
        floor={floor}
        showStatus
        onRoomClick={select}
        reason={(room) => (room.key === 'room-306' ? undefined : 'Không đủ điều kiện')}
      />,
    );
    fireEvent.keyDown(screen.getByRole('button', { name: /Phòng 306,/ }), { key: 'Enter' });
    expect(select).toHaveBeenCalledWith(floor.rooms[0]);
    select.mockClear();
    fireEvent.click(screen.getByRole('button', { name: /Phòng 307,/ }));
    fireEvent.keyDown(screen.getByRole('button', { name: /Phòng 307,/ }), { key: ' ' });
    expect(select).not.toHaveBeenCalled();
    expect(screen.getByText('Chưa bố trí trên sơ đồ (1)')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Phòng 301,/ }).hasAttribute('disabled')).toBe(true);
  });

  it('falls back to the complete room list without inventing a layout', () => {
    page(
      <HotelFloorMap
        floor={{ ...floor, elements: [], publishedAt: null, version: 0 }}
        onRoomClick={() => {}}
      />,
    );
    expect(screen.getByText('Chưa cấu hình sơ đồ tầng này')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: /Phòng 30/ })).toHaveLength(3);
  });
});

it('public map uses the public API and offers room-type booking without operational labels', async () => {
  vi.mocked(publicApiClient.get).mockResolvedValue({ data: { data: { floors: [floor] } } });
  page(<PublicHotelMapPage />);
  fireEvent.click(await screen.findByRole('button', { name: /Phòng 306,/ }));
  expect(
    (await screen.findByRole('link', { name: 'Xem hạng phòng và đặt' })).getAttribute('href'),
  ).toBe('/room-types/twin');
  expect(screen.queryByText('Đang có khách')).toBeNull();
  expect(screen.queryByText('Sẵn sàng')).toBeNull();
  expect(apiClient.get).not.toHaveBeenCalled();
});

it('manager builds a suggested layout, previews it and publishes with the loaded version', async () => {
  vi.mocked(publicApiClient.get).mockResolvedValue({ data: { data: { floors: [floor] } } });
  vi.mocked(apiClient.get).mockResolvedValue({
    data: { data: { ...floor, elements: [], publishedAt: null, version: 0 } },
  });
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: { ...floor, version: 1 } } });
  page(<FloorPlanManagementPage />);
  fireEvent.click(await screen.findByRole('button', { name: 'Tự xếp đều' }));
  expect(apiClient.get).not.toHaveBeenCalledWith('/management/floor-plans/1');
  fireEvent.click(screen.getByRole('button', { name: 'Lưu và công bố' }));
  await waitFor(() =>
    expect(apiClient.put).toHaveBeenCalledWith(
      '/management/floor-plans/3',
      expect.objectContaining({
        expectedVersion: 0,
        elements: expect.arrayContaining([
          expect.objectContaining({ kind: 'ROOM', roomId: 'room-306' }),
        ]),
      }),
    ),
  );
});

it('rearranges an existing layout without publishing and supports undo and redo', () => {
  page(<FloorPlanEditor floor={floor} onReload={async () => {}} />);
  expect(screen.queryByRole('spinbutton', { name: 'X 1' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Tự xếp đều' }));
  expect(screen.getByText('3/3 phòng đã bố trí')).toBeTruthy();
  expect(apiClient.put).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác' }));
  expect(screen.getByText('2/3 phòng đã bố trí')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Làm lại' }));
  expect(screen.getByText('3/3 phòng đã bố trí')).toBeTruthy();
});

it('supports moving a room with the keyboard and only sends the edit on publication', async () => {
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: { ...floor, version: 2 } } });
  page(<FloorPlanEditor floor={floor} onReload={async () => {}} />);
  fireEvent.keyDown(screen.getByRole('button', { name: 'Chỉnh Phòng 306' }), { key: 'ArrowDown' });
  expect(apiClient.put).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Lưu và công bố' }));
  await waitFor(() =>
    expect(apiClient.put).toHaveBeenCalledWith(
      '/management/floor-plans/3',
      expect.objectContaining({
        expectedVersion: 1,
        elements: expect.arrayContaining([expect.objectContaining({ roomId: 'room-306', y: 1 })]),
      }),
    ),
  );
});

it('places additional areas into free space instead of stacking every new element at zero', async () => {
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: { ...floor, version: 2 } } });
  page(<FloorPlanEditor floor={floor} onReload={async () => {}} />);
  fireEvent.click(screen.getByRole('button', { name: 'Thang máy' }));
  fireEvent.click(screen.getByRole('button', { name: 'Cầu thang' }));
  fireEvent.click(screen.getByRole('button', { name: 'Lưu và công bố' }));
  await waitFor(() => expect(apiClient.put).toHaveBeenCalled());
  const payload = vi.mocked(apiClient.put).mock.calls[0][1] as {
    elements: { kind: string; x: number; y: number; width: number; height: number }[];
  };
  const added = payload.elements.filter((e) => e.kind !== 'ROOM');
  expect(added).toHaveLength(2);
  expect(added.some((e) => e.x !== 0 || e.y !== 0)).toBe(true);
});

it('staff map refreshes its selected room details after inventory changes', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({
    data: { data: { floors: [floor], serverNow: '2026-11-11T01:00:00Z' } },
  });
  const { client } = page(<StaffRoomMapPage />);
  fireEvent.click(await screen.findByRole('button', { name: /Phòng 306,/ }));
  const changed = {
    ...floor,
    rooms: floor.rooms.map((room) =>
      room.key === 'room-306' ? { ...room, operationalStatus: 'DIRTY' } : room,
    ),
  };
  vi.mocked(apiClient.get).mockResolvedValue({
    data: { data: { floors: [changed], serverNow: '2026-11-11T01:01:00Z' } },
  });
  await act(() => client.invalidateQueries({ queryKey: ['staff-room-map'] }));
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /Phòng 306,/ }).getAttribute('aria-label')).toContain(
      'Chờ dọn',
    ),
  );
  expect(screen.getAllByText('Chờ dọn').length).toBeGreaterThanOrEqual(2);
});

it('loads structured room area once per type and does not infer it from descriptions', async () => {
  const missing = { ...floor, rooms: floor.rooms.map((room) => ({ ...room, areaM2: undefined })) };
  vi.mocked(publicApiClient.get).mockImplementation(async (url) => ({
    data: {
      data:
        url === '/hotel-map'
          ? { floors: [missing] }
          : {
              areaM2: String(url).endsWith('/twin') ? 30 : null,
              description: 'Phòng 99 m² chỉ là nội dung mô tả',
              category: { id: 'c', code: 'SUPERIOR', name: 'Superior' },
              basePrice: 1700000,
            },
    },
  }));
  const client = new QueryClient();
  const first = await getPublicMap(client);
  const second = await getPublicMap(client);
  expect(first.floors[0].rooms[0].areaM2).toBe(30);
  expect(first.floors[0].rooms[0].categoryCode).toBe('SUPERIOR');
  expect(first.floors[0].rooms[0].typeInfo?.basePrice).toBe(1700000);
  expect(first.floors[0].rooms[2].areaM2).toBeNull();
  expect(second.floors[0].rooms[0].areaM2).toBe(30);
  expect(
    vi.mocked(publicApiClient.get).mock.calls.filter(([url]) => url === '/room-types/twin'),
  ).toHaveLength(1);
});

it('renders relative tile areas and a fixed canvas without fitting to the room count', () => {
  const proportional: FloorPlan = {
    ...floor,
    gridColumns: 80,
    gridRows: 40,
    rooms: [
      { ...floor.rooms[0], areaM2: 22 },
      { ...floor.rooms[1], areaM2: 60 },
    ],
    elements: [
      { ...floor.elements[0], x: 2, y: 4, width: 11, height: 8 },
      { ...floor.elements[1], x: 20, y: 4, width: 20, height: 12 },
    ],
  };
  page(<HotelFloorMap floor={proportional} onRoomClick={() => {}} />);
  const small = screen.getByRole('button', { name: /Phòng 306,/ }).querySelector('rect')!;
  const large = screen.getByRole('button', { name: /Phòng 307,/ }).querySelector('rect')!;
  const area = (element: Element) =>
    Number(element.getAttribute('width')) * Number(element.getAttribute('height'));
  expect(area(large) / area(small)).toBeCloseTo(60 / 22, 8);
  const svg = screen.getByRole('group', { name: 'Mặt bằng Tầng 3' }) as unknown as SVGSVGElement;
  expect(svg.style.width).toBe('1120px');
  expect(svg.style.height).toBe('560px');
  expect(svg.textContent).not.toContain('m²');
  expect(screen.queryByText('22 m²')).toBeNull();
  expect(screen.queryByText('60 m²')).toBeNull();
});

it('keeps area-sized rooms fixed when a resize shortcut is pressed', () => {
  page(<FloorPlanEditor floor={floor} onReload={async () => {}} />);
  const room = screen.getByRole('button', { name: 'Chỉnh Phòng 306' });
  fireEvent.click(room);
  expect(room.querySelector('.floor-plan-resize-handle')).toBeNull();
  const before = room.querySelector('rect')!.getAttribute('width');
  fireEvent.keyDown(room, { key: 'ArrowRight', shiftKey: true });
  expect(room.querySelector('rect')!.getAttribute('width')).toBe(before);
  expect(screen.getByRole('alert').textContent).toContain('giữ theo bố cục');
  expect(apiClient.put).not.toHaveBeenCalled();
});

it('shows a real alignment grid and toggles it without changing the layout', () => {
  page(<FloorPlanEditor floor={floor} onReload={async () => {}} />);
  const room = screen.getByRole('button', { name: 'Chỉnh Phòng 306' });
  const before = room.querySelector('rect')!.outerHTML;
  expect(screen.getByTestId('floor-plan-grid')).toBeTruthy();
  fireEvent.click(screen.getByRole('switch', { name: 'Hiện lưới căn chỉnh' }));
  expect(screen.queryByTestId('floor-plan-grid')).toBeNull();
  expect(room.querySelector('rect')!.outerHTML).toBe(before);
  fireEvent.click(screen.getByRole('switch', { name: 'Hiện lưới căn chỉnh' }));
  expect(screen.getByTestId('floor-plan-grid')).toBeTruthy();
  expect(apiClient.put).not.toHaveBeenCalled();
});

it('allows an explicit manual size override and preserves it when publishing', async () => {
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: { ...floor, version: 2 } } });
  page(<FloorPlanEditor floor={floor} onReload={async () => {}} />);
  fireEvent.click(screen.getByText('Nâng cao: kích thước mặt bằng và bảng tọa độ'));
  expect(screen.getByRole('spinbutton', { name: 'Rộng 1' })).toHaveProperty('disabled', true);
  fireEvent.click(screen.getByRole('switch', { name: 'Cho phép chỉnh kích thước phòng thủ công' }));
  expect(screen.getByRole('spinbutton', { name: 'Rộng 1' })).toHaveProperty('disabled', false);
  const room = screen.getByRole('button', { name: 'Chỉnh Phòng 306' });
  fireEvent.click(room);
  expect(room.querySelector('.floor-plan-resize-handle')).toBeTruthy();
  fireEvent.keyDown(room, { key: 'ArrowRight', shiftKey: true });
  expect(room.querySelector('rect')!.getAttribute('width')).toBe('5');
  expect(apiClient.put).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Lưu và công bố' }));
  await waitFor(() =>
    expect(apiClient.put).toHaveBeenCalledWith(
      '/management/floor-plans/3',
      expect.objectContaining({
        elements: expect.arrayContaining([
          expect.objectContaining({ roomId: 'room-306', width: 5, height: 3 }),
        ]),
      }),
    ),
  );
});

it('displays a lobby with no empty-room warning and lists its guest areas', () => {
  const template = createLayoutTemplate([], 'lobby');
  const lobby: FloorPlan = {
    ...floor,
    floorNumber: 1,
    name: 'Tầng 1 · Sảnh & lễ tân',
    gridColumns: 80,
    gridRows: 40,
    rooms: [],
    elements: template.elements.map((element) => ({
      ...element,
      id: element.editorId,
      roomKey: null,
    })),
  };
  page(<HotelFloorMap floor={lobby} showStatus />);
  expect(screen.getByText('Quầy lễ tân')).toBeTruthy();
  expect(screen.queryByText('Tầng này chưa có phòng')).toBeNull();
  expect(screen.queryByText('Sẵn sàng')).toBeNull();
  const canvas = screen.getByRole('group', { name: `Mặt bằng ${lobby.name}` });
  expect(canvas.querySelectorAll('.lobby-map-artwork')).toHaveLength(7);
  expect(canvas.querySelector('[data-area="reception"]')).toBeTruthy();
  expect(canvas.querySelector('[data-area="lounge"]')).toBeTruthy();
  expect(canvas.querySelector('[data-area="luggage"]')).toBeTruthy();
  expect(canvas.querySelector('.floor-map-backdrop--lobby')).toBeTruthy();
  fireEvent.click(screen.getByText('Khu vực', { exact: true }));
  expect(screen.getByRole('list').textContent).toContain('Gửi hành lý');
  expect(screen.getByRole('list').textContent).toContain('Lối vào chính');
});

it('lets a manager create and publish a lobby floor with no physical rooms', async () => {
  const lobby = {
    ...floor,
    floorNumber: 1,
    name: 'Tầng 1 · Sảnh & lễ tân',
    rooms: [],
    elements: [],
    version: 0,
    publishedAt: null,
  };
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: { ...lobby, version: 1 } } });
  page(<FloorPlanEditor floor={lobby} onReload={async () => {}} />);
  fireEvent.click(screen.getByRole('button', { name: 'Tự xếp đều' }));
  const reception = screen.getByRole('button', { name: 'Chỉnh Quầy lễ tân' });
  expect(reception.querySelector('[data-area="reception"]')).toBeTruthy();
  fireEvent.keyDown(reception, { key: 'ArrowRight' });
  expect(reception.querySelector('.lobby-map-artwork')?.getAttribute('x')).toBe('19.6');
  expect(apiClient.put).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Lưu và công bố' }));
  await waitFor(() =>
    expect(apiClient.put).toHaveBeenCalledWith(
      '/management/floor-plans/1',
      expect.objectContaining({
        expectedVersion: 0,
        gridColumns: 80,
        gridRows: 40,
        elements: expect.arrayContaining([
          expect.objectContaining({
            kind: 'COMMON_AREA',
            label: 'Quầy lễ tân',
            roomId: null,
            x: 17,
          }),
        ]),
      }),
    ),
  );
});

it('adds walls without adding selectable rooms', () => {
  const rooms = floor.rooms.slice(0, 2).map((room) => ({ ...room, areaM2: 60 }));
  const layout = createLayoutTemplate(rooms, 'double-sided');
  const sparse: FloorPlan = {
    ...floor,
    gridColumns: layout.columns,
    gridRows: layout.rows,
    rooms,
    elements: layout.elements.map((element) => ({
      ...element,
      id: element.editorId,
      roomKey: element.roomId,
    })),
  };
  const select = vi.fn();
  page(<HotelFloorMap floor={sparse} onRoomClick={select} />);
  const svg = screen.getByRole('group', { name: 'Mặt bằng Tầng 3' });
  expect(svg.querySelectorAll('.floor-map-building-wall')).toHaveLength(1);
  expect(svg.querySelectorAll('.floor-map-room-wall')).toHaveLength(2);
  expect(svg.querySelectorAll('[role="button"]')).toHaveLength(2);
  expect(svg.querySelector('.floor-map-backdrop')?.getAttribute('pointer-events')).toBe('none');
  fireEvent.click(screen.getByRole('button', { name: /Phòng 306,/ }));
  expect(select).toHaveBeenCalledWith(rooms[0]);
  expect(svg.textContent).not.toContain('m²');
  expect(apiClient.put).not.toHaveBeenCalled();
});

it('publishes an equal four-room floor from the fill-floor template without changing inventory', async () => {
  const rooms = Array.from({ length: 4 }, (_, i) => ({
    ...floor.rooms[0],
    key: `r-${i}`,
    roomId: `r-${i}`,
    roomNumber: String(501 + i),
    areaM2: 38,
  }));
  const source = {
    ...floor,
    floorNumber: 5,
    name: 'Tầng 5',
    gridColumns: 80,
    gridRows: 40,
    rooms,
    elements: [],
  };
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: { ...source, version: 2 } } });
  page(<FloorPlanEditor floor={source} onReload={async () => {}} />);
  fireEvent.click(screen.getByRole('button', { name: 'Tự xếp đều' }));
  const canvas = screen.getByRole('group', { name: 'Chỉnh sửa sơ đồ bằng kéo thả' });
  expect(canvas.querySelectorAll('.map-room')).toHaveLength(4);
  for (const tile of canvas.querySelectorAll('.map-room > rect')) {
    expect(tile.getAttribute('width')).toBe('33');
    expect(tile.getAttribute('height')).toBe('12');
  }
  expect(apiClient.put).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Lưu và công bố' }));
  await waitFor(() =>
    expect(apiClient.put).toHaveBeenCalledWith(
      '/management/floor-plans/5',
      expect.objectContaining({
        gridColumns: 80,
        gridRows: 40,
        elements: expect.arrayContaining([
          expect.objectContaining({
            kind: 'ROOM',
            roomId: 'r-3',
            x: 41,
            y: 24,
            width: 33,
            height: 12,
          }),
        ]),
      }),
    ),
  );
  expect(rooms.every((room) => room.areaM2 === 38)).toBe(true);
});

it('keeps category color while rendering separate status badges and the selected outline', () => {
  const themed: FloorPlan = {
    ...floor,
    rooms: floor.rooms.map((room) => ({ ...room, categoryCode: 'SUPERIOR' })),
  };
  const select = vi.fn();
  page(<HotelFloorMap floor={themed} showStatus selectedKey="room-306" onRoomClick={select} />);
  const ready = screen.getByRole('button', { name: /Phòng 306,/ }),
    occupied = screen.getByRole('button', { name: /Phòng 307,/ });
  expect(ready.classList.contains('map-room--superior')).toBe(true);
  expect(occupied.classList.contains('map-room--superior')).toBe(true);
  expect(ready.classList.contains('map-room--selected')).toBe(true);
  expect(ready.querySelector('.room-map-status-badge')?.textContent).toBe('Sẵn sàng');
  expect(occupied.querySelector('.room-map-status-badge')?.textContent).toBe('Đang có khách');
  expect(ready.querySelector('.room-map-caption')?.textContent).toBe('Superior Twin');
  fireEvent.click(ready);
  expect(select).toHaveBeenCalledWith(themed.rooms[0]);
});

it('shows structured room information without inventing price or availability', () => {
  page(
    <RoomMapDetails
      room={{
        ...floor.rooms[0],
        typeInfo: {
          images: ['https://example.test/room.jpg'],
          basePrice: 1700000,
          maxGuests: 3,
          capacityAdults: 2,
          capacityChildren: 1,
          beds: [{ bedType: 'SINGLE', quantity: 2 }],
          amenities: [{ id: 'wifi', name: 'Wi-Fi', icon: 'wifi' }],
          category: { id: 'c', code: 'SUPERIOR', name: 'Superior' },
        },
      }}
    />,
  );
  expect(screen.getByRole('img', { name: 'Ảnh minh họa hạng Superior Twin' })).toBeTruthy();
  expect(screen.getByText('Giường đơn × 2')).toBeTruthy();
  expect(screen.getByText('Tối đa 3 khách')).toBeTruthy();
  expect(screen.getByText('Wi-Fi')).toBeTruthy();
  expect(screen.getByText(/1\.700\.000/)).toBeTruthy();
  expect(screen.queryByText('Còn phòng')).toBeNull();
});
