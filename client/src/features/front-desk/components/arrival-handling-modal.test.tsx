// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArrivalHandlingModal } from './arrival-handling-modal';
import {
  getArrivalHandling,
  markNoShow,
  recordContactResult,
  updateArrivalEstimate,
  decideArrivalHold,
} from '../api/arrival-handling-api';
import type { ArrivalHandling } from '../types/arrival-handling';
import type { TodayArrival } from '../types/today-arrivals';

const auth = vi.hoisted(() => ({ roles: ['RECEPTIONIST'] }));
vi.mock('../../authentication/providers/auth-context', () => ({ useAuth: () => auth }));
vi.mock('../../../hooks/use-stay-policy', () => ({
  useHotelPolicy: () => ({ lateArrivalCutoff: '06:00:00', zone: 'Asia/Ho_Chi_Minh' }),
}));
vi.mock('../api/arrival-handling-api', () => ({
  getArrivalHandling: vi.fn(),
  markNoShow: vi.fn(),
  holdArrival: vi.fn(),
  recordArrivalContact: vi.fn(),
  updateArrivalEstimate: vi.fn(),
  recordContactResult: vi.fn(),
  decideArrivalHold: vi.fn(),
  releaseArrivalHold: vi.fn(),
}));
vi.mock('../api/booking-change-api', () => ({
  getBookingChanges: vi.fn(async () => []),
  getStaffCancellationQuote: vi.fn(),
  createBookingChange: vi.fn(),
}));
import { getBookingChanges } from '../api/booking-change-api';

const arrival: TodayArrival = {
  bookingId: 'b1',
  bookingCode: 'BK-NO-SHOW',
  primaryGuest: { fullName: 'Khách A', phone: '0900000000', email: null },
  roomType: { id: 'type-1', code: 'DLX', name: 'Deluxe' },
  checkInDate: '2026-11-12',
  checkOutDate: '2026-11-15',
  adultCount: 2,
  childCount: 0,
  bookingStatus: 'CONFIRMED',
  createdAt: '2026-11-11T00:00:00Z',
};
const preview: ArrivalHandling = {
  bookingId: 'b1',
  bookingCode: 'BK-NO-SHOW',
  bookingStatus: 'CONFIRMED',
  policyVersion: 'HANBAY-ARRIVAL-2026-11-V1',
  legacyPolicy: false,
  canMarkNoShow: true,
  blockReason: null,
  cutoff: '2026-11-12T23:00:00Z',
  departureDeadline: '2026-11-15T05:00:00Z',
  serverNow: '2026-11-12T23:30:00Z',
  zone: 'Asia/Ho_Chi_Minh',
  originalTotalAmount: 2_800_000,
  paidAmount: 2_800_000,
  standardFee: 800_000,
  feeAmount: 800_000,
  refundAmount: 2_000_000,
  outstandingAmount: 0,
  holdUntil: null,
  holdRequestedUntil: null,
  noShowAt: null,
  previewToken: 'financial-snapshot',
  history: [],
  refunds: [],
  receivedAmount: 2_800_000,
  availableAmount: 2_800_000,
  progress: {
    group: 'ACTION_REQUIRED',
    reasons: ['RECEPTION_EXPIRED'],
    expectedAt: null,
    receptionDeadline: '2026-11-12T23:00:00Z',
    canReceive: false,
    holdPending: false,
    lastContactAt: null,
    lastContactChannel: null,
    lastContactOutcome: null,
    lastContactNote: null,
    changeRequestId: null,
  },
};

let client: QueryClient;
beforeEach(() => {
  vi.resetAllMocks();
  auth.roles = ['RECEPTIONIST'];
  client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
  vi.mocked(getArrivalHandling).mockResolvedValue(preview);
  vi.mocked(getBookingChanges).mockResolvedValue([]);
  vi.mocked(markNoShow).mockResolvedValue({
    ...preview,
    bookingStatus: 'NO_SHOW',
    canMarkNoShow: false,
  });
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
});
afterEach(() => {
  cleanup();
  client.clear();
  vi.restoreAllMocks();
});

async function open(expand = true) {
  render(
    <QueryClientProvider client={client}>
      <App>
        <MemoryRouter>
          <ArrivalHandlingModal arrival={arrival} onClose={vi.fn()} />
        </MemoryRouter>
      </App>
    </QueryClientProvider>,
  );
  await screen.findByRole('button', { name: 'Cập nhật giờ đến' });
  if (expand) {
    fireEvent.click(screen.getByRole('button', { name: 'Xử lý không đến' }));
    await screen.findByRole('button', { name: 'Xác nhận không đến' });
  }
}
async function choose(label: string, option: string) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: label }));
  fireEvent.click(await screen.findByText(option, { selector: '.ant-select-item-option-content' }));
}
async function fillDecision() {
  await choose('Kết quả liên hệ', 'Không nghe máy / chưa phản hồi');
  fireEvent.change(screen.getByLabelText('Ghi chú liên hệ'), {
    target: { value: 'Gọi điện: khách xác nhận không đến' },
  });
  if (screen.queryByLabelText('Lý do xử lý'))
    fireEvent.change(screen.getByLabelText('Lý do xử lý'), {
      target: { value: 'Khách không đến; đã đối chiếu điều khoản' },
    });
}
async function confirm() {
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận không đến' }));
}

function groupPreview(checkedInLast = false): ArrivalHandling {
  const codes = ['BK20261113001401', 'BK20261113001402', 'BK20261113001403', 'BK20261113001404'];
  return {
    ...preview,
    orderCode: 'ORDER-FOUR',
    roomCount: 4,
    orderRooms: codes.map((code, i) => ({
      bookingCode: i === 0 ? arrival.bookingCode : code,
      orderRoomNumber: i + 1,
      roomTypeName: i === 3 ? 'Family Double Queen' : 'Deluxe King',
      bookingStatus: checkedInLast && i === 3 ? 'CHECKED_IN' : 'CONFIRMED',
      expectedAt: null,
      receptionDeadline: preview.cutoff,
      departureDeadline: preview.departureDeadline,
      holdUntil: null,
      holdRequestedUntil: null,
      canManageArrival: !(checkedInLast && i === 3),
    })),
    progress: { ...preview.progress!, expectedAt: '2026-11-13T01:50:00Z' },
  };
}

describe('xử lý khách chưa đến', () => {
  it('allows a legacy booking to be cancelled while a reschedule request is pending', async () => {
    vi.mocked(getArrivalHandling).mockResolvedValue({
      ...preview,
      legacyPolicy: true,
      canMarkNoShow: false,
      progress: { ...preview.progress!, changeRequestId: 'change-1' },
    });
    vi.mocked(getBookingChanges).mockResolvedValue([
      {
        id: 'change-1',
        bookingCode: arrival.bookingCode,
        orderCode: null,
        type: 'RESCHEDULE',
        status: 'PENDING',
        reason: 'Khách xin đổi ngày',
        requestedBy: 'Lễ tân',
        requestedAt: '2026-11-12T22:00:00Z',
        checkInDate: '2026-11-25',
        checkOutDate: '2026-11-28',
        decidedBy: null,
        decidedAt: null,
        decisionNote: null,
        snapshot: null,
      },
    ]);

    await open(false);
    await screen.findByText('Có yêu cầu hủy / đổi lịch chờ quản lý');
    expect(screen.queryByRole('button', { name: 'Khách xin đổi lịch' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Xử lý không đến' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Khách báo hủy' }));
    expect(await screen.findByLabelText('Yêu cầu khách')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Khách xác nhận hủy, giải phóng phòng' }),
    ).toBeTruthy();
  });

  it('keeps the overview compact and hides financial inputs until the settlement action', async () => {
    await open(false);
    expect(screen.getByText('Chưa ghi nhận giờ hẹn')).toBeTruthy();
    expect(screen.queryByText('Phí chuẩn')).toBeNull();
    expect(screen.queryByRole('spinbutton')).toBeNull();
    expect(screen.queryByLabelText('Lý do xử lý')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Ghi liên hệ' }));
    expect(screen.getByRole('combobox', { name: 'Kết quả liên hệ' })).toBeTruthy();
    expect(screen.queryByLabelText('Lý do xử lý')).toBeNull();
  });

  it('records a selected contact outcome without requiring free-form notes or any financial action', async () => {
    vi.mocked(recordContactResult).mockResolvedValue(preview);
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Ghi liên hệ' }));
    await choose('Kết quả liên hệ', 'Không nghe máy / chưa phản hồi');
    fireEvent.click(screen.getByRole('button', { name: 'Lưu liên hệ' }));
    await waitFor(() =>
      expect(recordContactResult).toHaveBeenCalledExactlyOnceWith('BK-NO-SHOW', {
        channel: 'PHONE',
        outcome: 'NO_ANSWER',
        note: '',
      }),
    );
    expect(markNoShow).not.toHaveBeenCalled();
  });

  it.each([
    ['Khách báo hủy', 'hủy'],
    ['Khách xin đổi lịch', 'đổi lịch'],
  ])('routes contact outcome %s into the customer request form', async (label, action) => {
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Ghi liên hệ' }));
    await choose('Kết quả liên hệ', label);
    expect(screen.queryByRole('button', { name: 'Lưu liên hệ' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: `Tiếp tục yêu cầu ${action}` }));
    expect(await screen.findByLabelText('Yêu cầu khách')).toBeTruthy();
    expect((screen.getByLabelText('Yêu cầu khách') as HTMLTextAreaElement).value).toContain(label);
    expect(markNoShow).not.toHaveBeenCalled();
  });

  it('saves an ETA in hotel time without creating a hold or changing the deadline', async () => {
    vi.mocked(getArrivalHandling).mockResolvedValue({
      ...preview,
      progress: { ...preview.progress!, expectedAt: '2026-11-13T01:00:00Z' },
    });
    vi.mocked(updateArrivalEstimate).mockResolvedValue(preview);
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật giờ đến' }));
    expect((screen.getByLabelText('Giờ khách dự kiến đến') as HTMLInputElement).value).toBe(
      '13/11/2026 08:00',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Lưu giờ đến' }));
    await waitFor(() =>
      expect(updateArrivalEstimate).toHaveBeenCalledExactlyOnceWith(
        'BK-NO-SHOW',
        '2026-11-13T01:00:00.000Z',
        '',
      ),
    );
  });

  it('lets managers reject a pending late-reception request using the exact displayed deadline', async () => {
    auth.roles = ['MANAGER'];
    vi.mocked(getArrivalHandling).mockResolvedValue({
      ...preview,
      canMarkNoShow: false,
      holdRequestedUntil: '2026-11-13T03:00:00Z',
      progress: { ...preview.progress!, holdPending: true },
    });
    vi.mocked(decideArrivalHold).mockResolvedValue(preview);
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt / từ chối nhận muộn' }));
    fireEvent.change(screen.getByLabelText('Lý do quyết định nhận muộn'), {
      target: { value: 'Khách chưa thống nhất giờ đến' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối nhận sau hạn' }));
    await waitFor(() =>
      expect(decideArrivalHold).toHaveBeenCalledExactlyOnceWith('BK-NO-SHOW', {
        requestedUntil: '2026-11-13T03:00:00Z',
        approve: false,
        note: 'Khách chưa thống nhất giờ đến',
      }),
    );
    expect(markNoShow).not.toHaveBeenCalled();
  });

  it('only opens an amount input when a manager chooses reduced no-show fees', async () => {
    auth.roles = ['MANAGER'];
    await open();
    expect(screen.queryByRole('spinbutton')).toBeNull();
    fireEvent.click(screen.getByRole('radio', { name: 'Giảm phí' }));
    expect(screen.getByRole('spinbutton', { name: 'Phí no-show được duyệt' })).toBeTruthy();
    await fillDecision();
    expect(
      screen.getByRole('button', { name: 'Xác nhận không đến' }).hasAttribute('disabled'),
    ).toBe(true);
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '400000' } });
    await confirm();
    await waitFor(() =>
      expect(markNoShow).toHaveBeenCalledWith(
        'BK-NO-SHOW',
        expect.objectContaining({ feeAmount: 400_000 }),
      ),
    );
  });

  it('shows the legacy-policy warning only when settlement is opened', async () => {
    vi.mocked(getArrivalHandling).mockResolvedValue({ ...preview, legacyPolicy: true });
    await open(false);
    expect(screen.queryByText(/Booking chưa ghi nhận điều khoản no-show/)).toBeNull();
    expect(screen.getByRole('button', { name: 'Khách báo hủy' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Khách xin đổi lịch' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Xử lý không đến' }));
    expect(await screen.findByText(/Booking chưa ghi nhận điều khoản no-show/)).toBeTruthy();
  });

  it('requires contact and reason then sends the displayed first-night fee and preview token once', async () => {
    await open();
    expect(
      screen.getByRole('button', { name: 'Xác nhận không đến' }).hasAttribute('disabled'),
    ).toBe(true);
    expect(screen.queryByLabelText('Phí no-show được duyệt')).toBeNull();
    await fillDecision();
    await confirm();
    await screen.findByText('Đã xác nhận khách không đến; giữ chỗ đã được giải phóng');
    expect(markNoShow).toHaveBeenCalledExactlyOnceWith('BK-NO-SHOW', {
      previewToken: 'financial-snapshot',
      feeAmount: 800_000,
      contactNote: 'Gọi điện: khách xác nhận không đến',
      reason: 'Gọi điện: khách xác nhận không đến',
      channel: 'PHONE',
      outcome: 'NO_ANSWER',
    });
  });

  it('lets manager waive the fee and sends zero rather than replacing it with the standard fee', async () => {
    auth.roles = ['MANAGER'];
    await open();
    await fillDecision();
    fireEvent.click(screen.getByRole('radio', { name: 'Miễn phí' }));
    fireEvent.change(screen.getByLabelText('Lý do xử lý'), {
      target: { value: 'Quản lý miễn phí theo thỏa thuận' },
    });
    await confirm();
    await waitFor(() =>
      expect(markNoShow).toHaveBeenCalledWith(
        'BK-NO-SHOW',
        expect.objectContaining({ feeAmount: 0 }),
      ),
    );
  });

  it('blocks reception from closing a legacy booking', async () => {
    vi.mocked(getArrivalHandling).mockResolvedValue({ ...preview, legacyPolicy: true });
    await open();
    await fillDecision();
    expect(
      screen.getByRole('button', { name: 'Xác nhận không đến' }).hasAttribute('disabled'),
    ).toBe(true);
    expect(markNoShow).not.toHaveBeenCalled();
  });

  it('blocks closing while the approved hold is active', async () => {
    vi.mocked(getArrivalHandling).mockResolvedValue({
      ...preview,
      canMarkNoShow: false,
      holdUntil: '2026-11-13T03:00:00Z',
      blockReason: 'Thỏa thuận giữ phòng vẫn còn hiệu lực.',
    });
    await open(false);
    expect(screen.queryByRole('button', { name: 'Xử lý không đến' })).toBeNull();
    expect(screen.getByText('Thỏa thuận giữ phòng vẫn còn hiệu lực.')).toBeTruthy();
    expect(markNoShow).not.toHaveBeenCalled();
  });

  it('verifies a lost POST response before allowing another action and never repeats the financial POST', async () => {
    vi.mocked(getArrivalHandling)
      .mockResolvedValueOnce(preview)
      .mockRejectedValue(new Error('Network down'));
    vi.mocked(markNoShow).mockRejectedValueOnce(new Error('Lost response'));
    await open();
    await fillDecision();
    await confirm();
    await waitFor(() => expect(markNoShow).toHaveBeenCalledTimes(1));
    const retry = await screen.findByRole('button', { name: 'Tra cứu lại kết quả' });
    await waitFor(() => expect(retry.hasAttribute('disabled')).toBe(false));
    expect(
      screen.getByRole('button', { name: 'Xác nhận không đến' }).hasAttribute('disabled'),
    ).toBe(true);
    vi.mocked(getArrivalHandling).mockResolvedValue({
      ...preview,
      bookingStatus: 'NO_SHOW',
      canMarkNoShow: false,
    });
    fireEvent.click(retry);
    await screen.findByText('Đã xác nhận khách không đến; giữ chỗ đã được giải phóng');
    expect(markNoShow).toHaveBeenCalledTimes(1);
  });
  it('requires an explicit room scope and saves the shared ETA for all four selected rooms', async () => {
    const group = groupPreview();
    vi.mocked(getArrivalHandling).mockResolvedValue(group);
    vi.mocked(updateArrivalEstimate).mockResolvedValue(group);
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật giờ đến' }));
    expect(screen.getByRole('button', { name: 'Lưu giờ đến' }).hasAttribute('disabled')).toBe(true);
    fireEvent.click(screen.getByRole('radio', { name: 'Các phòng cùng đơn đã chọn' }));
    expect(screen.getByText('Sẽ áp dụng 4 phòng đã chọn trong đơn ORDER-FOUR')).toBeTruthy();
    expect(screen.getByText('Giờ hẹn vượt hạn tiếp nhận của phòng đã chọn')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Lưu giờ đến' }));
    await waitFor(() =>
      expect(updateArrivalEstimate).toHaveBeenCalledExactlyOnceWith(
        arrival.bookingCode,
        '2026-11-13T01:50:00.000Z',
        '',
        group.orderRooms!.map((r) => r.bookingCode),
      ),
    );
  });

  it('allows an explicit single-room ETA even when the booking belongs to a four-room order', async () => {
    const group = groupPreview();
    vi.mocked(getArrivalHandling).mockResolvedValue(group);
    vi.mocked(updateArrivalEstimate).mockResolvedValue(group);
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật giờ đến' }));
    fireEvent.click(screen.getByRole('radio', { name: `Chỉ phòng ${arrival.bookingCode}` }));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu giờ đến' }));
    await waitFor(() =>
      expect(updateArrivalEstimate).toHaveBeenCalledExactlyOnceWith(
        arrival.bookingCode,
        '2026-11-13T01:50:00.000Z',
        '',
        [arrival.bookingCode],
      ),
    );
  });

  it('excludes already checked-in rooms from a shared arrival contact and lets reception choose fewer rooms', async () => {
    const group = groupPreview(true);
    vi.mocked(getArrivalHandling).mockResolvedValue(group);
    vi.mocked(recordContactResult).mockResolvedValue(group);
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Ghi liên hệ' }));
    fireEvent.click(screen.getByRole('radio', { name: 'Các phòng cùng đơn đã chọn' }));
    const checkedIn = screen.getByRole('checkbox', { name: /BK20261113001404/ });
    expect(checkedIn.hasAttribute('disabled')).toBe(true);
    fireEvent.click(screen.getByRole('checkbox', { name: /BK20261113001403/ }));
    await choose('Kết quả liên hệ', 'Không nghe máy / chưa phản hồi');
    fireEvent.click(screen.getByRole('button', { name: 'Lưu liên hệ' }));
    await waitFor(() =>
      expect(recordContactResult).toHaveBeenCalledExactlyOnceWith(arrival.bookingCode, {
        channel: 'PHONE',
        outcome: 'NO_ANSWER',
        note: '',
        bookingCodes: [arrival.bookingCode, 'BK20261113001402'],
      }),
    );
  });

  it('moves a shared ETA after cutoff to a scoped hold request without silently granting late admission', async () => {
    vi.mocked(getArrivalHandling).mockResolvedValue(groupPreview());
    await open(false);
    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật giờ đến' }));
    fireEvent.click(screen.getByRole('radio', { name: 'Các phòng cùng đơn đã chọn' }));
    fireEvent.click(screen.getByRole('button', { name: 'Chuyển sang xin nhận sau hạn' }));
    expect(screen.getByText('Sẽ áp dụng 4 phòng đã chọn trong đơn ORDER-FOUR')).toBeTruthy();
    expect(screen.getByLabelText('Hạn tiếp nhận đề nghị')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Gửi đề nghị quản lý' }).hasAttribute('disabled'),
    ).toBe(true);
    expect(updateArrivalEstimate).not.toHaveBeenCalled();
  });
});
