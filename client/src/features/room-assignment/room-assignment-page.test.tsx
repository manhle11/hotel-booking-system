// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { hotelToday } from '../../utils/hotel-time';
import * as api from './api';
import type { AssignmentCalendar, AssignmentPlan } from './api';
import { RoomAssignmentPage } from './room-assignment-page';

const auth = vi.hoisted(() => ({ roles: ['RECEPTIONIST'] }));
vi.mock('../authentication/providers/auth-context', () => ({ useAuth: () => auth }));
vi.mock('./api', () => ({
  getAssignmentCalendar: vi.fn(),
  getLatestAssignment: vi.fn(),
  suggestAssignment: vi.fn(),
  applyAssignment: vi.fn(),
  assignBooking: vi.fn(),
  getAssignmentHistory: vi.fn(),
  getAssignmentPlan: vi.fn(),
}));
vi.mock('../room-map/api', () => ({
  invalidateRoomMaps: vi.fn().mockResolvedValue(undefined),
  roomStatuses: {
    AVAILABLE: 'Sẵn sàng',
    OCCUPIED: 'Đang có khách',
    DIRTY: 'Chờ dọn',
    CLEANING: 'Đang dọn',
    MAINTENANCE: 'Bảo trì',
    OUT_OF_SERVICE: 'Ngừng phục vụ',
  },
}));

const today = hotelToday();
const from = today.format('YYYY-MM-DD'),
  to = today.add(60, 'day').format('YYYY-MM-DD');
const metrics = {
  orphanOne: 0,
  orphanTwo: 0,
  changed: 0,
  unassigned: 0,
  windowsThree: 0,
  windowsFive: 0,
  windowsSeven: 0,
};
function fixture(): AssignmentCalendar {
  return {
    from,
    to,
    today: from,
    version: 'version-1',
    serverNow: new Date().toISOString(),
    warnings: [],
    rooms: [
      {
        id: 'room-1',
        roomTypeId: 'type-1',
        roomTypeName: 'Deluxe',
        roomNumber: '201',
        floor: 2,
        status: 'AVAILABLE',
      },
      {
        id: 'room-2',
        roomTypeId: 'type-1',
        roomTypeName: 'Deluxe',
        roomNumber: '202',
        floor: 2,
        status: 'AVAILABLE',
      },
    ],
    bookings: [
      {
        id: 'booking-1',
        bookingCode: 'HAN-001',
        roomTypeId: 'type-1',
        start: today.add(5, 'day').format('YYYY-MM-DD'),
        end: today.add(8, 'day').format('YYYY-MM-DD'),
        scheduledEnd: today.add(8, 'day').format('YYYY-MM-DD'),
        overdue: false,
        status: 'CONFIRMED',
        stayRevision: 0,
        orderId: null,
        orderRoomNumber: null,
        guestName: 'Nguyễn Văn An',
        expiresAt: null,
        roomId: null,
        allocationStatus: null,
      },
    ],
  };
}
function proposal(data: AssignmentCalendar): AssignmentPlan {
  return {
    id: 'plan-1',
    roomTypeId: 'type-1',
    from,
    to,
    sourceVersion: data.version,
    createdAt: new Date().toISOString(),
    validUntil: new Date(Date.now() + 300_000).toISOString(),
    status: 'FEASIBLE',
    algorithm: 'CP_SAT',
    before: { ...metrics, unassigned: 1 },
    after: metrics,
    warnings: [],
    assignments: { 'booking-1': 'room-1' },
    applied: false,
    changes: [
      {
        bookingId: 'booking-1',
        bookingCode: 'HAN-001',
        fromRoomId: null,
        fromRoomNumber: null,
        toRoomId: 'room-1',
        toRoomNumber: '201',
        start: data.bookings[0].start,
        end: data.bookings[0].end,
        changed: true,
        reason: 'Phòng trống liên tục cho toàn bộ kỳ ở.',
      },
    ],
  };
}
function mount() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App>
          <MemoryRouter>
            <RoomAssignmentPage />
          </MemoryRouter>
        </App>
      </ConfigProvider>
    </QueryClientProvider>,
  );
  return client;
}
beforeEach(() => {
  vi.clearAllMocks();
  auth.roles = ['RECEPTIONIST'];
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia = vi.fn().mockImplementation(() => ({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  vi.mocked(api.getAssignmentCalendar).mockResolvedValue(fixture());
  vi.mocked(api.getLatestAssignment).mockResolvedValue(null);
  vi.mocked(api.getAssignmentHistory).mockResolvedValue([]);
  vi.mocked(api.suggestAssignment).mockResolvedValue(proposal(fixture()));
  vi.mocked(api.applyAssignment).mockResolvedValue({ ...proposal(fixture()), applied: true });
  vi.mocked(api.assignBooking).mockResolvedValue(fixture());
  vi.mocked(api.getAssignmentPlan).mockResolvedValue(proposal(fixture()));
});
afterEach(cleanup);

describe('RoomAssignmentPage', () => {
  it('keeps historical room numbers visible after a room has been renamed or removed', async () => {
    vi.mocked(api.getAssignmentHistory).mockResolvedValue([
      {
        id: 'history-1',
        bookingId: 'booking-1',
        action: 'ROOM_ASSIGNED',
        actorId: null,
        actorName: 'Lễ tân',
        createdAt: new Date().toISOString(),
        reason: 'Theo yêu cầu khách',
        details: JSON.stringify({
          bookingCode: 'HAN-001',
          fromRoom: 'former-room',
          toRoom: 'removed-room',
          fromRoomNumber: '201',
          toRoomNumber: '909',
        }),
      },
    ]);
    mount();
    await screen.findByText('Nguyễn Văn An');
    fireEvent.click(screen.getByRole('button', { name: 'Lịch sử gán phòng' }));
    expect(await screen.findByText('Phòng 201 → Phòng 909')).toBeInTheDocument();
  });
  it('searches the room type selector when the hotel has many room types', async () => {
    const data = fixture();
    data.rooms.push(
      ...Array.from({ length: 30 }, (_, i) => ({
        ...data.rooms[0],
        id: `extra-room-${i}`,
        roomTypeId: `extra-type-${i}`,
        roomTypeName: `Loại phòng ${i}`,
      })),
    );
    data.rooms.push({
      ...data.rooms[0],
      id: 'special-room',
      roomTypeId: 'special-type',
      roomTypeName: 'Loại đặc biệt',
    });
    data.bookings.push({
      ...data.bookings[0],
      id: 'special-booking',
      bookingCode: 'HAN-SPECIAL',
      roomTypeId: 'special-type',
      guestName: 'Khách hạng đặc biệt',
    });
    vi.mocked(api.getAssignmentCalendar).mockResolvedValue(data);
    mount();
    await screen.findByText('Nguyễn Văn An');
    const select = screen.getByRole('combobox', { name: 'Loại phòng' });
    fireEvent.mouseDown(select);
    fireEvent.change(select, { target: { value: 'Loại đặc biệt' } });
    fireEvent.click(await screen.findByTitle('Loại đặc biệt'));
    expect(await screen.findByText('Khách hạng đặc biệt')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Gợi ý xếp phòng' }));
    await waitFor(() =>
      expect(api.suggestAssignment).toHaveBeenCalledWith('special-type', from, to),
    );
  });
  it('shows an overdue stay separately from future tentative bookings in the same room', async () => {
    const data = fixture();
    const future = {
      ...data.bookings[0],
      id: 'future-booking',
      bookingCode: 'HAN-FUTURE',
      roomId: 'room-1',
      allocationStatus: 'TENTATIVE' as const,
    };
    data.bookings[0] = {
      ...data.bookings[0],
      roomId: 'room-1',
      status: 'CHECKED_IN',
      allocationStatus: 'OCCUPIED',
      overdue: true,
      start: today.subtract(2, 'day').format('YYYY-MM-DD'),
      end: today.add(90, 'day').format('YYYY-MM-DD'),
      scheduledEnd: from,
    };
    data.bookings.push(future);
    data.rooms[0].status = 'OCCUPIED';
    vi.mocked(api.getAssignmentCalendar).mockResolvedValue(data);
    mount();
    expect(await screen.findByText('Quá giờ trả phòng')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Booking HAN-001, phòng 201' })).toHaveStyle({
      gridRow: '1',
    });
    expect(screen.getByRole('button', { name: 'Booking HAN-FUTURE, phòng 201' })).toHaveStyle({
      gridRow: '2',
    });
  });
  it('shows the daily calendar and confirmed bookings awaiting assignment', async () => {
    mount();
    expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Lịch gán phòng theo ngày' })).toBeInTheDocument();
    expect(api.getAssignmentCalendar).toHaveBeenCalledWith(from, to);
    expect(screen.getByRole('button', { name: 'Gợi ý xếp phòng' })).toBeEnabled();
  });

  it('requires explicit review and confirmation before applying a suggestion', async () => {
    mount();
    await screen.findByText('Nguyễn Văn An');
    fireEvent.click(screen.getByRole('button', { name: 'Gợi ý xếp phòng' }));
    expect(await screen.findByText('Phương án đề xuất')).toBeInTheDocument();
    expect(api.applyAssignment).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng phương án' }));
    expect(api.applyAssignment).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận áp dụng' }));
    await waitFor(() =>
      expect(api.applyAssignment).toHaveBeenCalledWith(
        expect.objectContaining({ id: 'plan-1', sourceVersion: 'version-1' }),
      ),
    );
  });

  it('recovers a successful apply when its HTTP response was lost', async () => {
    vi.mocked(api.applyAssignment).mockRejectedValueOnce(new Error('Network error'));
    vi.mocked(api.getAssignmentPlan).mockResolvedValueOnce({
      ...proposal(fixture()),
      applied: true,
    });
    mount();
    await screen.findByText('Nguyễn Văn An');
    fireEvent.click(screen.getByRole('button', { name: 'Gợi ý xếp phòng' }));
    await screen.findByText('Phương án đề xuất');
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng phương án' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận áp dụng' }));
    expect(
      await screen.findByText('Phương án đã được áp dụng. Lịch phòng đã được cập nhật.'),
    ).toBeInTheDocument();
    expect(api.getAssignmentPlan).toHaveBeenCalledWith('plan-1');
    expect(api.applyAssignment).toHaveBeenCalledTimes(1);
  });

  it('disables an old proposal when the source calendar changes', async () => {
    const client = mount();
    await screen.findByText('Nguyễn Văn An');
    fireEvent.click(screen.getByRole('button', { name: 'Gợi ý xếp phòng' }));
    await screen.findByText('Phương án đề xuất');
    act(() =>
      client.setQueryData(['room-assignment-calendar', from, to], {
        ...fixture(),
        version: 'version-2',
      }),
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Áp dụng phương án' })).toBeDisabled(),
    );
    expect(
      screen.getByText('Lịch đã cập nhật hoặc gợi ý hết hạn. Vui lòng lấy gợi ý mới.'),
    ).toBeInTheDocument();
  });

  it('does not offer apply for an infeasible or undecided proposal', async () => {
    vi.mocked(api.suggestAssignment).mockResolvedValue({
      ...proposal(fixture()),
      status: 'INFEASIBLE',
      after: null,
      changes: [],
      assignments: {},
    });
    mount();
    await screen.findByText('Nguyễn Văn An');
    fireEvent.click(screen.getByRole('button', { name: 'Gợi ý xếp phòng' }));
    await screen.findByText('Không xếp được với ràng buộc hiện tại');
    expect(screen.getByRole('button', { name: 'Áp dụng phương án' })).toBeDisabled();
  });

  it('prevents a receptionist from unlocking a fixed room', async () => {
    const data = fixture();
    data.bookings[0].roomId = 'room-1';
    data.bookings[0].allocationStatus = 'LOCKED';
    vi.mocked(api.getAssignmentCalendar).mockResolvedValue(data);
    mount();
    fireEvent.click(await screen.findByRole('button', { name: 'Booking HAN-001, phòng 201' }));
    expect(
      await screen.findByRole('checkbox', { name: 'Khóa phòng cụ thể cho booking này' }),
    ).toBeDisabled();
    expect(screen.getByRole('combobox', { name: 'Phòng cho toàn bộ kỳ ở' })).toBeDisabled();
  });

  it('requires a reason when a manager unlocks a room', async () => {
    auth.roles = ['MANAGER'];
    const data = fixture();
    data.bookings[0].roomId = 'room-1';
    data.bookings[0].allocationStatus = 'LOCKED';
    vi.mocked(api.getAssignmentCalendar).mockResolvedValue(data);
    mount();
    fireEvent.click(await screen.findByRole('button', { name: 'Booking HAN-001, phòng 201' }));
    fireEvent.click(
      await screen.findByRole('checkbox', { name: 'Khóa phòng cụ thể cho booking này' }),
    );
    expect(screen.getByRole('button', { name: 'Lưu gán phòng' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do thay đổi gán phòng' }), {
      target: { value: 'Khách đồng ý đổi phòng' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu gán phòng' }));
    await waitFor(() =>
      expect(api.assignBooking).toHaveBeenCalledWith(
        'booking-1',
        expect.objectContaining({
          locked: false,
          expectedVersion: 'version-1',
          reason: 'Khách đồng ý đổi phòng',
        }),
      ),
    );
  });
});
