// @vitest-environment jsdom

import {
  QueryClient,
  QueryClientProvider,
  QueryObserver,
  focusManager,
  onlineManager,
} from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiClientError } from '../../../types/api';
import {
  checkInGuest,
  getAvailablePhysicalRooms,
  getCheckInBookingContext,
} from '../api/booking-api';
import { CheckInGuestPage } from './check-in-guest-page';
import { frontDeskKeys } from '../../front-desk/api/front-desk-query';
import {
  useStaffMap,
  type HotelMapResponse,
  type FloorPlan,
  type MapRoom,
} from '../../room-map/api';

vi.mock('../../room-map/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../room-map/api')>()),
  useStaffMap: vi.fn(),
}));

vi.mock('../api/booking-api', () => ({
  checkInGuest: vi.fn(),
  getAvailablePhysicalRooms: vi.fn(),
  getCheckInBookingContext: vi.fn(),
}));

const ROOM_ID = '30000000-0000-4000-8000-000000000001';

function mockMapFloors(floors: FloorPlan[]) {
  const mapClient = new QueryClient();
  mapClient.setQueryData<HotelMapResponse>(['map-fixture'], { floors });
  vi.mocked(useStaffMap).mockReturnValue(
    new QueryObserver<HotelMapResponse>(mapClient, {
      queryKey: ['map-fixture'],
      enabled: false,
    }).getCurrentResult(),
  );
}
function mapFloor(floorNumber: number, rooms: MapRoom[]): FloorPlan {
  return {
    floorNumber,
    name: `Tầng ${floorNumber}`,
    gridColumns: 24,
    gridRows: 16,
    version: 1,
    publishedAt: '2026-11-11T10:00:00Z',
    rooms,
    elements: rooms.map((room, index) => ({
      id: `node-${room.key}`,
      kind: 'ROOM',
      roomKey: room.key,
      label: null,
      x: index * 5,
      y: 0,
      width: 4,
      height: 3,
    })),
  };
}
const deluxeRoom: MapRoom = {
  key: 'twin-406',
  roomId: 'twin-406',
  roomNumber: '406',
  roomTypeId: 'DELUXE_TWIN',
  roomTypeName: 'Deluxe Twin',
  operationalStatus: 'AVAILABLE',
};
const familyRoom: MapRoom = {
  key: 'family-601',
  roomId: 'family-601',
  roomNumber: '601',
  roomTypeId: 'FAMILY_DOUBLE_QUEEN',
  roomTypeName: 'Family Double Queen',
  operationalStatus: 'AVAILABLE',
};
function mockHotelMap() {
  mockMapFloors([
    mapFloor(2, [
      {
        ...deluxeRoom,
        key: 'standard-201',
        roomId: 'standard-201',
        roomNumber: '201',
        roomTypeId: 'STANDARD_QUEEN',
        roomTypeName: 'Standard Queen',
      },
    ]),
    mapFloor(4, [
      {
        ...deluxeRoom,
        key: 'king-401',
        roomId: 'king-401',
        roomNumber: '401',
        roomTypeId: 'DELUXE_KING',
        roomTypeName: 'Deluxe King',
      },
      deluxeRoom,
    ]),
    mapFloor(6, [familyRoom]),
  ]);
}

beforeAll(() => {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  };

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(() => ({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

beforeEach(() => {
  vi.clearAllMocks();
  focusManager.setFocused(true);
  onlineManager.setOnline(true);
  vi.mocked(getCheckInBookingContext)
    .mockReset()
    .mockImplementation(async (code) => ({
      bookingId: 'booking-id',
      bookingCode: code,
      bookingStatus: 'CONFIRMED',
      primaryGuest: { fullName: 'Khách đối chiếu', phone: '0900000000', email: null },
      roomType: { id: 'type', code: 'SUITE', name: 'Suite' },
      checkInDate: '2026-11-12',
      checkOutDate: '2026-11-13',
      adultCount: 2,
      childCount: 0,
      totalAmount: 2500000,
      paidAmount: 2000000,
      outstandingAmount: 500000,
      canCheckIn: true,
      earlyArrivalRequired: false,
      assignedRoom: null,
      checkedInAt: null,
    }));
  const mapClient = new QueryClient();
  mapClient.setQueryData<HotelMapResponse>(['map-fixture'], {
    floors: [
      {
        floorNumber: 1,
        name: 'Tầng 1 · Sảnh & lễ tân',
        gridColumns: 80,
        gridRows: 40,
        version: 1,
        publishedAt: '2026-11-11T12:00:00Z',
        elements: [],
        rooms: [],
      },
      {
        floorNumber: 5,
        name: 'Tầng 5',
        gridColumns: 24,
        gridRows: 16,
        version: 0,
        publishedAt: null,
        elements: [],
        rooms: [
          {
            key: ROOM_ID,
            roomId: ROOM_ID,
            roomNumber: '0501',
            roomTypeId: 'type',
            roomTypeName: 'Suite',
            operationalStatus: 'AVAILABLE',
          },
        ],
      },
    ],
  });
  vi.mocked(useStaffMap).mockReturnValue(
    new QueryObserver<HotelMapResponse>(mapClient, {
      queryKey: ['map-fixture'],
      enabled: false,
    }).getCurrentResult(),
  );
});
afterEach(() => {
  cleanup();
  focusManager.setFocused(undefined);
  onlineManager.setOnline(true);
  vi.restoreAllMocks();
});

describe('CheckInGuestPage', () => {
  it.each(['background', 'focus', 'reconnect'] as const)(
    'hoàn tất khôi phục qua %s, reset form và làm mới danh sách đúng một lần',
    async (trigger) => {
      vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
        { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
      ]);
      const base = await getCheckInBookingContext('BK-AUTO');
      vi.mocked(getCheckInBookingContext)
        .mockResolvedValueOnce(base)
        .mockRejectedValueOnce(new Error('Mất mạng'));
      vi.mocked(checkInGuest).mockRejectedValue(
        new ApiClientError({ kind: 'network', message: 'Mất phản hồi' }),
      );
      const client = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      });
      const arrivalsKey = frontDeskKeys.todayArrivals({ page: 0, size: 20 });
      const overdueKey = frontDeskKeys.overdueArrivals({ page: 0, size: 20 });
      for (const key of [
        arrivalsKey,
        overdueKey,
        ['staff-room-map'],
        ['today-departures'],
        ['booking-history'],
        ['manager-dashboard'],
      ])
        client.setQueryData(key, { content: [] });
      const invalidate = vi.spyOn(client, 'invalidateQueries');
      try {
        renderPage(client);
        fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-AUTO' } });
        fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
        fireEvent.click(await screen.findByText(/Phòng 0501 · Tầng 5/));
        fireEvent.change(screen.getByLabelText(/Lý do duyệt nhận phòng sớm/), {
          target: { value: 'Duyệt riêng cho booking này' },
        });
        await confirmCheckIn();
        await screen.findByText('Chưa xác định được kết quả nhận phòng');
        expect(client.getQueryState(arrivalsKey)?.isInvalidated).toBe(false);
        vi.mocked(getCheckInBookingContext).mockResolvedValue({
          ...base,
          bookingStatus: 'CHECKED_IN',
          canCheckIn: false,
          assignedRoom: { roomId: 'actual-room', roomNumber: '0702', floor: 7, status: 'OCCUPIED' },
          checkedInAt: '2026-11-12T07:00:00Z',
        });
        if (trigger === 'background') {
          await act(() => client.refetchQueries({ queryKey: ['check-in-context', 'BK-AUTO'] }));
        } else {
          act(() => {
            if (trigger === 'focus') {
              focusManager.setFocused(false);
              focusManager.setFocused(true);
            } else {
              onlineManager.setOnline(false);
              onlineManager.setOnline(true);
            }
          });
        }
        await waitFor(() =>
          expect((screen.getByLabelText('Mã booking') as HTMLInputElement).value).toBe(''),
        );
        expect(screen.getByText('0702')).toBeTruthy();
        expect(
          (screen.getByLabelText(/Lý do duyệt nhận phòng sớm/) as HTMLTextAreaElement).value,
        ).toBe('');
        expect(screen.queryByRole('radio')).toBeNull();
        expect(screen.queryByText('Chưa xác định được kết quả nhận phòng')).toBeNull();
        for (const key of [
          arrivalsKey,
          overdueKey,
          ['staff-room-map'],
          ['today-departures'],
          ['booking-history'],
          ['manager-dashboard'],
        ])
          expect(client.getQueryState(key)?.isInvalidated).toBe(true);
        expect(checkInGuest).toHaveBeenCalledTimes(1);
        expect(
          invalidate.mock.calls.filter(([filters]) => filters?.queryKey === frontDeskKeys.all),
        ).toHaveLength(1);
        await act(() =>
          client.refetchQueries({ queryKey: ['check-in-context', 'BK-AUTO'], type: 'all' }),
        );
        expect(
          invalidate.mock.calls.filter(([filters]) => filters?.queryKey === frontDeskKeys.all),
        ).toHaveLength(1);
      } finally {
        cleanup();
        client.clear();
      }
    },
  );

  it('tra cứu booking đã nhận không tự reset mã hay phát thông báo hoàn tất mới', async () => {
    const base = await getCheckInBookingContext('BK-LOOKUP');
    vi.mocked(getCheckInBookingContext).mockResolvedValue({
      ...base,
      bookingStatus: 'CHECKED_IN',
      canCheckIn: false,
      assignedRoom: { roomId: ROOM_ID, roomNumber: '0501', floor: 5, status: 'OCCUPIED' },
      checkedInAt: '2026-11-12T07:00:00Z',
    });
    const client = new QueryClient();
    const arrivalsKey = frontDeskKeys.todayArrivals({ page: 0, size: 20 });
    client.setQueryData(arrivalsKey, { content: [] });
    try {
      renderPage(client);
      fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-LOOKUP' } });
      fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
      await screen.findByText('Check-in thành công');
      expect((screen.getByLabelText('Mã booking') as HTMLInputElement).value).toBe('BK-LOOKUP');
      expect(client.getQueryState(arrivalsKey)?.isInvalidated).toBe(false);
      expect(checkInGuest).not.toHaveBeenCalled();
    } finally {
      cleanup();
      client.clear();
    }
  });
  it('xóa lý do nhận sớm khi đổi booking và sau khi nhận thành công', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
    ]);
    vi.mocked(checkInGuest).mockResolvedValue({
      bookingId: 'booking-id',
      bookingCode: 'BK-B',
      bookingStatus: 'CHECKED_IN',
      room: { roomId: ROOM_ID, roomNumber: '0501', floor: 5, status: 'OCCUPIED' },
      checkedInAt: '2026-11-12T07:00:00Z',
    });
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-A' } });
    fireEvent.change(screen.getByLabelText(/Lý do duyệt nhận phòng sớm/), {
      target: { value: 'Lý do của A' },
    });
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-B' } });
    expect((screen.getByLabelText(/Lý do duyệt nhận phòng sớm/) as HTMLTextAreaElement).value).toBe(
      '',
    );
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    fireEvent.click(await screen.findByText(/Phòng 0501 · Tầng 5/));
    fireEvent.change(screen.getByLabelText(/Lý do duyệt nhận phòng sớm/), {
      target: { value: 'Lý do của B' },
    });
    await confirmCheckIn();
    await screen.findByText('Check-in thành công');
    expect((screen.getByLabelText('Mã booking') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText(/Lý do duyệt nhận phòng sớm/) as HTMLTextAreaElement).value).toBe(
      '',
    );
    expect(checkInGuest).toHaveBeenCalledWith('BK-B', ROOM_ID, 'Lý do của B');
  });

  it.each(['network', 'already-checked-in'] as const)(
    'tra cứu trạng thái đã lưu sau lỗi %s mà không gửi POST lần hai',
    async (kind) => {
      vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
        { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
      ]);
      const base = await getCheckInBookingContext('BK-RECOVER');
      vi.mocked(getCheckInBookingContext)
        .mockResolvedValueOnce(base)
        .mockResolvedValueOnce({
          ...base,
          bookingStatus: 'CHECKED_IN',
          canCheckIn: false,
          assignedRoom: { roomId: 'actual-room', roomNumber: '0702', floor: 7, status: 'OCCUPIED' },
          checkedInAt: '2026-11-12T07:00:00Z',
        });
      vi.mocked(checkInGuest).mockRejectedValue(
        new ApiClientError({
          kind: kind === 'network' ? 'network' : 'api',
          status: kind === 'network' ? undefined : 409,
          code: kind === 'network' ? undefined : 'BOOKING_ALREADY_CHECKED_IN',
          message: 'Không nhận được phản hồi',
        }),
      );
      renderPage();
      fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-RECOVER' } });
      fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
      fireEvent.click(await screen.findByText(/Phòng 0501 · Tầng 5/));
      await confirmCheckIn();
      await screen.findByText('Check-in thành công');
      expect(screen.getByText('0702')).toBeTruthy();
      expect(screen.getByText('Đã tra cứu và xác nhận khách đã nhận phòng.')).toBeTruthy();
      expect(checkInGuest).toHaveBeenCalledTimes(1);
    },
  );

  it('chặn gửi lại khi chưa xác minh được và cho phép tra cứu thủ công', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
    ]);
    const base = await getCheckInBookingContext('BK-VERIFY');
    vi.mocked(getCheckInBookingContext)
      .mockResolvedValueOnce(base)
      .mockRejectedValueOnce(new Error('Mất mạng'))
      .mockResolvedValueOnce({
        ...base,
        bookingStatus: 'CHECKED_IN',
        canCheckIn: false,
        assignedRoom: { roomId: ROOM_ID, roomNumber: '0501', floor: 5, status: 'OCCUPIED' },
        checkedInAt: '2026-11-12T07:00:00Z',
      });
    vi.mocked(checkInGuest).mockRejectedValue(
      new ApiClientError({ kind: 'network', message: 'Mất mạng' }),
    );
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-VERIFY' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    fireEvent.click(await screen.findByText(/Phòng 0501 · Tầng 5/));
    await confirmCheckIn();
    await screen.findByText('Chưa xác định được kết quả nhận phòng');
    expect(screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Tra cứu lại kết quả' }));
    await screen.findByText('Check-in thành công');
    expect(checkInGuest).toHaveBeenCalledTimes(1);
  });

  it('mở mã booking từ danh sách, hiển thị đối chiếu và không cho nhận quá giờ', async () => {
    const base = await getCheckInBookingContext('BK-LATE');
    vi.mocked(getCheckInBookingContext).mockResolvedValue({ ...base, canCheckIn: false });
    window.history.replaceState({}, '', '/front-desk/check-in?bookingCode=BK-LATE');
    try {
      renderPage();
      await screen.findByText('Đối chiếu booking');
      expect(screen.getByText('Khách đối chiếu')).toBeTruthy();
      expect(screen.getByText('Booking nằm ngoài khung giờ tiếp nhận')).toBeTruthy();
      expect(getAvailablePhysicalRooms).not.toHaveBeenCalled();
      expect(
        screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled'),
      ).toBe(true);
    } finally {
      window.history.replaceState({}, '', '/');
    }
  });

  it('bỏ phản hồi tra cứu cũ khi lễ tân đã chuyển sang booking khác', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
    ]);
    const base = await getCheckInBookingContext('BK-OLD');
    let finish: (data: typeof base) => void = () => {};
    const pending = new Promise<typeof base>((resolve) => {
      finish = resolve;
    });
    vi.mocked(getCheckInBookingContext)
      .mockResolvedValueOnce(base)
      .mockRejectedValueOnce(new Error('Mất mạng'))
      .mockReturnValueOnce(pending);
    vi.mocked(checkInGuest).mockRejectedValue(
      new ApiClientError({ kind: 'network', message: 'Mất mạng' }),
    );
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-OLD' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    fireEvent.click(await screen.findByText(/Phòng 0501 · Tầng 5/));
    await confirmCheckIn();
    await screen.findByText('Chưa xác định được kết quả nhận phòng');
    expect(
      screen.getByRole('button', { name: /Tìm phòng khả dụng/ }).hasAttribute('disabled'),
    ).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Tra cứu lại kết quả' }));
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-NEW' } });
    await act(async () =>
      finish({
        ...base,
        bookingStatus: 'CHECKED_IN',
        canCheckIn: false,
        assignedRoom: { roomId: ROOM_ID, roomNumber: '0501', floor: 5, status: 'OCCUPIED' },
        checkedInAt: '2026-11-12T07:00:00Z',
      }),
    );
    expect((screen.getByLabelText('Mã booking') as HTMLInputElement).value).toBe('BK-NEW');
    expect(screen.queryByText('Check-in thành công')).toBeNull();
  });
  it('chọn phòng từ sơ đồ chỉ cập nhật form, xác nhận mới gọi check-in', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
    ]);
    vi.mocked(checkInGuest).mockResolvedValue({
      bookingId: 'booking-id',
      bookingCode: 'BK-MAP',
      bookingStatus: 'CHECKED_IN',
      room: { roomId: ROOM_ID, roomNumber: '0501', floor: 5, status: 'OCCUPIED' },
      checkedInAt: '2026-11-11T07:00:00Z',
    });
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-MAP' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await screen.findByText(/Phòng 0501 · Tầng 5/);
    fireEvent.click(screen.getByRole('button', { name: 'Xem sơ đồ để chọn phòng' }));
    fireEvent.click(await screen.findByRole('button', { name: /Phòng 0501, Suite/ }));
    expect(screen.queryByRole('tab', { name: 'Tầng 1 · Sảnh & lễ tân' })).toBeNull();
    expect(checkInGuest).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled'),
      ).toBe(false),
    );
    await confirmCheckIn();
    await waitFor(() => expect(checkInGuest).toHaveBeenCalledWith('BK-MAP', ROOM_ID));
  });
  it('gọi API và hiển thị phòng khi check-in thành công', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
    ]);
    vi.mocked(checkInGuest).mockResolvedValue({
      bookingId: 'booking-id',
      bookingCode: 'BK20260819000001',
      bookingStatus: 'CHECKED_IN',
      room: { roomId: ROOM_ID, roomNumber: '0501', floor: 5, status: 'OCCUPIED' },
      checkedInAt: '2026-08-19T07:00:00Z',
    });
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    const arrivalKeys = [0, 1].map((page) => frontDeskKeys.todayArrivals({ page, size: 20 }));
    arrivalKeys.forEach((key) => client.setQueryData(key, { content: [] }));
    renderPage(client);

    fireEvent.change(screen.getByLabelText('Mã booking'), {
      target: { value: 'bk20260819000001' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await waitFor(() => expect(getAvailablePhysicalRooms).toHaveBeenCalled());
    const roomOption = await screen.findByText(/Phòng 0501 · Tầng 5/);
    fireEvent.click(roomOption);
    expect(
      roomOption.closest('label')?.classList.contains('ant-radio-button-wrapper-checked'),
    ).toBe(true);
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled'),
      ).toBe(false),
    );
    await confirmCheckIn();

    expect(await screen.findByText('Check-in thành công')).toBeTruthy();
    expect(screen.getByText('0501')).toBeTruthy();
    expect(checkInGuest).toHaveBeenCalledWith('BK20260819000001', ROOM_ID);
    for (const key of arrivalKeys) {
      expect(client.getQueryState(key)?.isInvalidated).toBe(true);
    }
  });

  it('hiển thị business error dễ hiểu', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: ROOM_ID, roomNumber: '0501', floor: 5 },
    ]);
    vi.mocked(checkInGuest).mockRejectedValue(
      new ApiClientError({
        kind: 'api',
        status: 409,
        code: 'ROOM_NOT_AVAILABLE',
        message: 'Conflict',
      }),
    );
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    const arrivalsKey = frontDeskKeys.todayArrivals({ page: 0, size: 20 });
    client.setQueryData(arrivalsKey, { content: [] });
    renderPage(client);

    fireEvent.change(screen.getByLabelText('Mã booking'), {
      target: { value: 'BK20260819000001' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await waitFor(() => expect(getAvailablePhysicalRooms).toHaveBeenCalled());
    fireEvent.click(await screen.findByText(/Phòng 0501 · Tầng 5/));
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled'),
      ).toBe(false),
    );
    await confirmCheckIn();

    expect(
      await screen.findByText('Phòng đã chọn không còn trống. Vui lòng chọn phòng khác.'),
    ).toBeTruthy();
    expect(client.getQueryState(arrivalsKey)?.isInvalidated).toBe(false);
  });

  it('thông báo khi booking không còn phòng khả dụng', async () => {
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([]);
    renderPage();

    fireEvent.change(screen.getByLabelText('Mã booking'), {
      target: { value: 'BK20260819000001' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));

    expect(
      await screen.findByText('Không còn phòng khả dụng thuộc hạng phòng đã đặt'),
    ).toBeTruthy();
    expect(checkInGuest).not.toHaveBeenCalled();
  });

  it('mở tầng 4 của booking Deluxe Twin, không mặc định tầng 2 và không chọn được Deluxe King', async () => {
    mockHotelMap();
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: 'twin-406', roomNumber: '406', floor: 4 },
    ]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-DELUXE' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await screen.findByText(/Phòng 406 · Tầng 4/);
    fireEvent.click(screen.getByRole('button', { name: 'Xem sơ đồ để chọn phòng' }));
    const tab = await screen.findByRole('tab', { name: 'Tầng 4 (1 có thể nhận)' });
    expect(tab.getAttribute('aria-selected')).toBe('true');
    expect(screen.queryByRole('tab', { name: /Tầng 2/ })).toBeNull();
    expect(screen.queryByRole('tab', { name: /Tầng 6/ })).toBeNull();
    expect(screen.getByText('Chọn phòng cho BK-DELUXE · Deluxe Twin')).toBeTruthy();
    const wrong = screen.getByRole('button', { name: /Phòng 401,/ });
    expect(wrong.getAttribute('aria-disabled')).toBe('true');
    expect(wrong.classList.contains('map-room--blocked')).toBe(true);
    fireEvent.click(wrong);
    fireEvent.keyDown(wrong, { key: 'Enter' });
    expect(screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('button', { name: /Phòng 406,/ }));
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled'),
      ).toBe(false),
    );
    expect(checkInGuest).not.toHaveBeenCalled();
  });

  it('đổi booking sang Family thì chuyển sang tầng 6 và bỏ lựa chọn Deluxe cũ', async () => {
    mockHotelMap();
    vi.mocked(getAvailablePhysicalRooms).mockImplementation(async (code) =>
      code === 'BK-DELUXE'
        ? [{ roomId: 'twin-406', roomNumber: '406', floor: 4 }]
        : [{ roomId: 'family-601', roomNumber: '601', floor: 6 }],
    );
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-DELUXE' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await screen.findByText(/Phòng 406 · Tầng 4/);
    fireEvent.click(screen.getByRole('button', { name: 'Xem sơ đồ để chọn phòng' }));
    fireEvent.click(await screen.findByRole('button', { name: /Phòng 406,/ }));
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-FAMILY' } });
    expect(screen.queryByRole('tab', { name: /Tầng 4/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await screen.findByText(/Phòng 601 · Tầng 6/);
    expect(
      (await screen.findByRole('tab', { name: 'Tầng 6 (1 có thể nhận)' })).getAttribute(
        'aria-selected',
      ),
    ).toBe('true');
    expect(screen.queryByRole('tab', { name: /Tầng 4/ })).toBeNull();
    expect(screen.getByRole('button', { name: /Xác nhận check-in/ }).hasAttribute('disabled')).toBe(
      true,
    );
    expect(checkInGuest).not.toHaveBeenCalled();
  });

  it('không hiện các tầng không liên quan khi booking không có phòng khả dụng', async () => {
    mockHotelMap();
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-EMPTY' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await screen.findByText('Không còn phòng khả dụng thuộc hạng phòng đã đặt');
    fireEvent.click(screen.getByRole('button', { name: 'Xem sơ đồ để chọn phòng' }));
    expect(screen.queryByRole('tab')).toBeNull();
    expect(checkInGuest).not.toHaveBeenCalled();
  });

  it('chọn phòng bằng danh sách chuyển sơ đồ sang đúng tầng của phòng đó', async () => {
    mockMapFloors([
      mapFloor(4, [deluxeRoom]),
      mapFloor(5, [{ ...deluxeRoom, key: 'twin-506', roomId: 'twin-506', roomNumber: '506' }]),
    ]);
    vi.mocked(getAvailablePhysicalRooms).mockResolvedValue([
      { roomId: 'twin-406', roomNumber: '406', floor: 4 },
      { roomId: 'twin-506', roomNumber: '506', floor: 5 },
    ]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Mã booking'), { target: { value: 'BK-MULTI-FLOOR' } });
    fireEvent.click(screen.getByRole('button', { name: /Tìm phòng khả dụng/ }));
    await screen.findByText(/Phòng 506 · Tầng 5/);
    fireEvent.click(screen.getByRole('button', { name: 'Xem sơ đồ để chọn phòng' }));
    expect(
      (await screen.findByRole('tab', { name: 'Tầng 4 (1 có thể nhận)' })).getAttribute(
        'aria-selected',
      ),
    ).toBe('true');
    fireEvent.click(screen.getByText(/Phòng 506 · Tầng 5/));
    await waitFor(() =>
      expect(
        screen.getByRole('tab', { name: 'Tầng 5 (1 có thể nhận)' }).getAttribute('aria-selected'),
      ).toBe('true'),
    );
    fireEvent.click(screen.getByRole('tab', { name: 'Tầng 4 (1 có thể nhận)' }));
    expect(
      screen.getByRole('tab', { name: 'Tầng 4 (1 có thể nhận)' }).getAttribute('aria-selected'),
    ).toBe('true');
    expect(checkInGuest).not.toHaveBeenCalled();
  });
});

function renderPage(
  queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } }),
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <App>
          <CheckInGuestPage />
        </App>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

async function confirmCheckIn() {
  const button = screen.getByRole('button', { name: /Xác nhận check-in/ });
  await waitFor(() => expect(button.hasAttribute('disabled')).toBe(false));
  fireEvent.click(button);
}
