// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BookingChangeRequestForm } from './booking-change-request-form';
import { BookingChangeReview } from './booking-change-review';
import {
  approveBookingChange,
  allowRescheduleException,
  cancellationSettlement,
  createBookingChange,
  getBookingChangeQuote,
  rejectBookingChange,
  type BookingChangeQuote,
} from '../api/booking-change-api';

const auth = vi.hoisted(() => ({ roles: ['MANAGER'] }));
vi.mock('../../authentication/providers/auth-context', () => ({ useAuth: () => auth }));
vi.mock('../api/booking-change-api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/booking-change-api')>()),
  createBookingChange: vi.fn(),
  getStaffCancellationQuote: vi.fn().mockResolvedValue(undefined),
  getBookingChanges: vi.fn(),
  getBookingChangeQuote: vi.fn(),
  approveBookingChange: vi.fn(),
  rejectBookingChange: vi.fn(),
  allowRescheduleException: vi.fn(),
}));
const quote: BookingChangeQuote = {
  request: {
    id: 'r1',
    bookingCode: 'BK-TEST',
    orderCode: null,
    type: 'CANCELLATION',
    status: 'PENDING',
    reason: 'Khách gọi báo hủy',
    requestedBy: 'Lễ tân A',
    requestedAt: '2026-11-12T11:00:00Z',
    checkInDate: null,
    checkOutDate: null,
    decidedBy: null,
    decidedAt: null,
    decisionNote: null,
    snapshot: null,
  },
  canApprove: true,
  blockReason: null,
  requiresFeeDecision: true,
  policyDescription: 'Hủy sát giờ cần quản lý quyết định phí ngoại lệ',
  quoteToken: 'fresh-snapshot',
  oldTotalAmount: 2_800_000,
  newTotalAmount: 0,
  availableAmount: 2_800_000,
  feeAmount: null,
  refundAmount: null,
  additionalAmount: 0,
  rooms: [
    {
      bookingCode: 'BK-TEST',
      roomTypeName: 'Deluxe',
      oldCheckInDate: '2026-11-12',
      oldCheckOutDate: '2026-11-15',
      newCheckInDate: null,
      newCheckOutDate: null,
      oldTotalAmount: 2_800_000,
      newTotalAmount: 0,
      availableAmount: 2_800_000,
      standardFee: 2_800_000,
      nights: [],
    },
  ],
  zone: 'Asia/Ho_Chi_Minh',
  serverNow: '2026-11-12T11:00:00Z',
};
let client: QueryClient;
beforeEach(() => {
  vi.resetAllMocks();
  auth.roles = ['MANAGER'];
  client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
  vi.mocked(getBookingChangeQuote).mockResolvedValue(quote);
  vi.mocked(createBookingChange).mockResolvedValue(quote.request);
  vi.mocked(approveBookingChange).mockResolvedValue({ ...quote.request, status: 'APPROVED' });
  vi.mocked(rejectBookingChange).mockResolvedValue({ ...quote.request, status: 'REJECTED' });
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
function mount(child: React.ReactNode) {
  render(
    <QueryClientProvider client={client}>
      <App>
        <MemoryRouter>{child}</MemoryRouter>
      </App>
    </QueryClientProvider>,
  );
}
async function review() {
  const onUpdated = vi.fn();
  mount(<BookingChangeReview id="r1" onClose={vi.fn()} onUpdated={onUpdated} />);
  await screen.findByText('Khách báo hủy');
  return onUpdated;
}

function rescheduleQuote(overrides: Partial<BookingChangeQuote> = {}): BookingChangeQuote {
  return {
    ...quote,
    requiresFeeDecision: false,
    feeAmount: 400_000,
    newTotalAmount: 2_400_000,
    refundAmount: 0,
    additionalAmount: 0,
    request: {
      ...quote.request,
      type: 'RESCHEDULE',
      checkInDate: '2026-11-22',
      checkOutDate: '2026-11-25',
    },
    rooms: [
      {
        ...quote.rooms[0],
        newCheckInDate: '2026-11-22',
        newCheckOutDate: '2026-11-25',
        newTotalAmount: 2_400_000,
        originalFirstNightAmount: 800_000,
        standardFee: 400_000,
        previousFee: 0,
        services: [],
        children: 0,
        nights: [
          { stayDate: '2026-11-22', price: 800_000 },
          { stayDate: '2026-11-23', price: 800_000 },
          { stayDate: '2026-11-24', price: 800_000 },
        ],
      },
    ],
    ...overrides,
  };
}

async function openReschedule(data = rescheduleQuote()) {
  vi.mocked(getBookingChangeQuote).mockResolvedValue(data);
  mount(<BookingChangeReview id="r1" onClose={vi.fn()} onUpdated={vi.fn()} />);
  await screen.findByText('Khách xin đổi lịch');
}

function acceptQuote() {
  fireEvent.change(screen.getByLabelText('Lý do quyết định'), {
    target: { value: 'Khách đồng ý báo giá qua điện thoại' },
  });
  fireEvent.click(screen.getByRole('checkbox', { name: /Đã đối chiếu khách chấp thuận/ }));
}

describe('yêu cầu hủy / đổi lịch', () => {
  it('confirms cancellation and releases rooms while settlement may need review', async () => {
    const onSaved = vi.fn();
    mount(
      <BookingChangeRequestForm
        code="BK-TEST"
        type="CANCELLATION"
        roomCount={2}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );
    expect(screen.getByText('Yêu cầu áp dụng toàn bộ 2 phòng trong đơn')).toBeTruthy();
    expect(
      screen
        .getByRole('button', { name: 'Khách xác nhận hủy, giải phóng phòng' })
        .hasAttribute('disabled'),
    ).toBe(true);
    fireEvent.change(screen.getByLabelText('Yêu cầu khách'), {
      target: { value: '  Khách gọi báo hủy  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Khách xác nhận hủy, giải phóng phòng' }));
    await screen.findByRole('link', { name: 'Xem yêu cầu hủy / đổi lịch' });
    expect(createBookingChange).toHaveBeenCalledExactlyOnceWith('BK-TEST', {
      type: 'CANCELLATION',
      reason: 'Khách gọi báo hủy',
    });
    expect(approveBookingChange).not.toHaveBeenCalled();
    expect(onSaved).toHaveBeenCalledTimes(1);
  });

  it('requires an explicit fee for late cancellation and preserves a manager waiver of zero', async () => {
    const onUpdated = await review();
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), {
      target: { value: 'Miễn phí theo thỏa thuận với khách' },
    });
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Tổng phí được duyệt' }), {
      target: { value: '0' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt và áp dụng' }));
    await waitFor(() => expect(onUpdated).toHaveBeenCalledTimes(1));
    expect(approveBookingChange).toHaveBeenCalledExactlyOnceWith('r1', {
      quoteToken: 'fresh-snapshot',
      feeAmount: 0,
      note: 'Miễn phí theo thỏa thuận với khách',
      customerAccepted: false,
      servicesConfirmed: false,
      childAges: {},
    });
  });

  it('lets reception inspect the request while reserving the decision for a manager', async () => {
    auth.roles = ['RECEPTIONIST'];
    await review();
    expect(screen.getByText(/Chờ quản lý xem xét/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Duyệt và áp dụng' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Từ chối yêu cầu' })).toBeNull();
  });

  it('shows repriced dates and the remaining balance for rescheduling with a separate fee and consent fields', async () => {
    vi.mocked(getBookingChangeQuote).mockResolvedValue({
      ...quote,
      requiresFeeDecision: false,
      feeAmount: 0,
      refundAmount: 0,
      additionalAmount: 1_200_000,
      newTotalAmount: 4_000_000,
      request: {
        ...quote.request,
        type: 'RESCHEDULE',
        checkInDate: '2026-11-22',
        checkOutDate: '2026-11-27',
      },
      rooms: [
        {
          ...quote.rooms[0],
          newCheckInDate: '2026-11-22',
          newCheckOutDate: '2026-11-27',
          newTotalAmount: 4_000_000,
          standardFee: 0,
        },
      ],
    });
    const onUpdated = vi.fn();
    mount(<BookingChangeReview id="r1" onClose={vi.fn()} onUpdated={onUpdated} />);
    await screen.findByText('Khách xin đổi lịch');
    expect(screen.getByText('22/11/2026 → 27/11/2026')).toBeTruthy();
    expect(screen.getByText('Tăng 2 đêm')).toBeTruthy();
    expect(screen.queryByRole('spinbutton')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Điều chỉnh hoặc miễn phí/ }));
    expect(screen.getByRole('spinbutton', { name: 'Tổng phí được duyệt' })).toBeTruthy();
    expect(within(screen.getByLabelText('Kết quả thu hoàn')).getByText(/1.200.000/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), {
      target: { value: 'Khách đồng ý giá mới' },
    });
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('checkbox', { name: /Đã đối chiếu khách chấp thuận/ }));
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    const deadline = screen.getByLabelText('Hạn thu chênh lệch');
    fireEvent.change(deadline, { target: { value: '13/11/2026 12:00' } });
    fireEvent.keyDown(deadline, { key: 'Enter', code: 'Enter' });
    fireEvent.blur(deadline);
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled'),
      ).toBe(false),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt và áp dụng' }));
    await waitFor(() =>
      expect(approveBookingChange).toHaveBeenCalledExactlyOnceWith('r1', {
        quoteToken: 'fresh-snapshot',
        note: 'Khách đồng ý giá mới',
        feeAmount: 0,
        customerAccepted: true,
        balanceDueAt: '2026-11-13T05:00:00.000Z',
        servicesConfirmed: false,
        childAges: {},
      }),
    );
  });

  it('allows rejecting a blocked request with a reason', async () => {
    vi.mocked(getBookingChangeQuote).mockResolvedValue({
      ...quote,
      canApprove: false,
      blockReason: 'Kỳ mới không còn phòng',
    });
    await review();
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), {
      target: { value: 'Không đủ phòng, đã trao đổi khách' },
    });
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối yêu cầu' }));
    await waitFor(() =>
      expect(rejectBookingChange).toHaveBeenCalledExactlyOnceWith(
        'r1',
        'Không đủ phòng, đã trao đổi khách',
      ),
    );
  });

  it('looks up an uncertain approval before permitting another financial action', async () => {
    vi.mocked(getBookingChangeQuote)
      .mockResolvedValueOnce({ ...quote, requiresFeeDecision: false, feeAmount: 0 })
      .mockRejectedValue(new Error('Mất mạng'));
    vi.mocked(approveBookingChange).mockRejectedValue(new Error('Mất phản hồi'));
    await review();
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), {
      target: { value: 'Duyệt miễn phí' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt và áp dụng' }));
    await screen.findByRole('button', { name: 'Tra cứu lại kết quả' });
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled'),
      ).toBe(true),
    );
    expect(approveBookingChange).toHaveBeenCalledTimes(1);
    vi.mocked(getBookingChangeQuote).mockResolvedValue({
      ...quote,
      request: { ...quote.request, status: 'APPROVED' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Tra cứu lại kết quả' }));
    await screen.findByText('Yêu cầu đã được duyệt');
    expect(approveBookingChange).toHaveBeenCalledTimes(1);
  });

  it('allocates whole-order cancellation fees by room and calculates refunds from remaining credit', () => {
    const grouped = {
      ...quote,
      rooms: [
        { ...quote.rooms[0], standardFee: 1, availableAmount: 1 },
        { ...quote.rooms[0], bookingCode: 'BK-2', standardFee: 2, availableAmount: 0 },
      ],
    };
    expect(cancellationSettlement(grouped, 2)).toEqual({ refund: 0, additional: 1 });
    expect(cancellationSettlement(grouped, 0)).toEqual({ refund: 1, additional: 0 });
  });

  it('separates previously booked fees from the incremental fee instead of charging them twice', async () => {
    const data = rescheduleQuote();
    await openReschedule({
      ...data,
      feeAmount: 800_000,
      rooms: [{ ...data.rooms[0], previousFee: 400_000, standardFee: 800_000 }],
    });
    const amounts = screen.getByLabelText('Chi tiết báo giá');
    const amountFor = (label: string) =>
      within(amounts).getByText(label).parentElement?.querySelector('dd')?.textContent;
    expect(amountFor('Phí đã ghi trước đây')).toMatch(/400.000/);
    expect(amountFor('Phí phát sinh lần này')).toMatch(/400.000/);
    expect(amountFor('Tổng phí còn hiệu lực sau xử lý')).toMatch(/800.000/);
    expect(amountFor('Tổng nghĩa vụ sau đổi')).toMatch(/3.200.000/);
    expect(amountFor('Chênh lệch giá kỳ ở, chưa gồm phí')).toMatch(/^0/);
    expect(screen.getByLabelText('Kết quả thu hoàn').textContent).toMatch(/Còn phải thu400.000/);
  });

  it('does not present blocked changes of night count as an applicable price or a 90-day exception', async () => {
    const data = rescheduleQuote();
    await openReschedule({
      ...data,
      canApprove: false,
      blockReason:
        'Đợt này chỉ đổi ngày cùng hạng và cùng số đêm; rút ngắn cần xử lý hủy một phần riêng.',
      request: { ...data.request, checkOutDate: '2026-11-24' },
      rooms: [{ ...data.rooms[0], newCheckOutDate: '2026-11-24' }],
    });
    expect(screen.getByText('Giảm 1 đêm')).toBeTruthy();
    expect(screen.getByText('Chưa thể áp dụng yêu cầu')).toBeTruthy();
    expect(screen.queryByLabelText('Kết quả thu hoàn')).toBeNull();
    expect(screen.queryByRole('button', { name: /Quản lý xét ngoại lệ/ })).toBeNull();
    expect(screen.queryByLabelText('Hạn thu chênh lệch')).toBeNull();
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
  });

  it('allows a refund quote without irrelevant collection or service fields', async () => {
    const data = rescheduleQuote();
    await openReschedule({ ...data, feeAmount: 0, rooms: [{ ...data.rooms[0], standardFee: 0 }] });
    expect(screen.getByLabelText('Kết quả thu hoàn').textContent).toMatch(
      /Cần hoàn cho khách400.000/,
    );
    expect(screen.queryByLabelText('Hạn thu chênh lệch')).toBeNull();
    expect(screen.queryByRole('checkbox', { name: /chuyển được các dịch vụ/ })).toBeNull();
    acceptQuote();
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt và áp dụng' }));
    await waitFor(() => expect(approveBookingChange).toHaveBeenCalledOnce());
    expect(approveBookingChange).toHaveBeenCalledWith(
      'r1',
      expect.objectContaining({
        customerAccepted: true,
        feeAmount: 0,
      }),
    );
    expect(vi.mocked(approveBookingChange).mock.calls[0][1]).not.toHaveProperty('balanceDueAt');
  });

  it('requires service confirmation and an age for each child, preserving an age of zero', async () => {
    const data = rescheduleQuote();
    await openReschedule({
      ...data,
      rooms: [{ ...data.rooms[0], children: 2, services: ['Ăn sáng × 2'] }],
    });
    acceptQuote();
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('checkbox', { name: /chuyển được các dịch vụ/ }));
    fireEvent.change(screen.getByLabelText('Tuổi trẻ em BK-TEST'), { target: { value: '0,' } });
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.change(screen.getByLabelText('Tuổi trẻ em BK-TEST'), { target: { value: '0, 5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt và áp dụng' }));
    await waitFor(() =>
      expect(approveBookingChange).toHaveBeenCalledWith(
        'r1',
        expect.objectContaining({
          servicesConfirmed: true,
          childAges: { 'BK-TEST': [0, 5] },
        }),
      ),
    );
  });

  it('requires consent again after a fee override or a refreshed quote', async () => {
    const data = rescheduleQuote();
    await openReschedule(data);
    acceptQuote();
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      false,
    );
    fireEvent.click(screen.getByRole('button', { name: /Điều chỉnh hoặc miễn phí/ }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Tổng phí được duyệt' }), {
      target: { value: '0' },
    });
    expect(
      (screen.getByRole('checkbox', { name: /Đã đối chiếu khách chấp thuận/ }) as HTMLInputElement)
        .checked,
    ).toBe(false);
    fireEvent.click(screen.getByRole('checkbox', { name: /Đã đối chiếu khách chấp thuận/ }));
    vi.mocked(getBookingChangeQuote).mockResolvedValue({ ...data, quoteToken: 'updated-price' });
    fireEvent.click(screen.getByRole('button', { name: 'Tra cứu lại yêu cầu' }));
    await waitFor(() =>
      expect(
        (screen.getByRole('spinbutton', { name: 'Tổng phí được duyệt' }) as HTMLInputElement).value,
      ).toBe('400000'),
    );
    expect(
      (screen.getByRole('checkbox', { name: /Đã đối chiếu khách chấp thuận/ }) as HTMLInputElement)
        .checked,
    ).toBe(false);
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    expect(approveBookingChange).not.toHaveBeenCalled();
  });

  it('shows simultaneous room-level refunds and debts rather than silently netting the order', async () => {
    const data = rescheduleQuote();
    await openReschedule({
      ...data,
      feeAmount: 0,
      rooms: [
        { ...data.rooms[0], newTotalAmount: 100_000, availableAmount: 200_000, standardFee: 0 },
        {
          ...data.rooms[0],
          bookingCode: 'BK-2',
          newTotalAmount: 100_000,
          availableAmount: 0,
          standardFee: 0,
        },
      ],
    });
    const result = within(screen.getByLabelText('Kết quả thu hoàn'));
    expect(result.getByText('Cần hoàn cho khách')).toBeTruthy();
    expect(result.getByText('Còn phải thu')).toBeTruthy();
    expect(result.getAllByText(/100.000/)).toHaveLength(2);
  });

  it('shows a fully priced shorter period while the manager exception is still pending', async () => {
    const data = rescheduleQuote();
    await openReschedule({
      ...data,
      canApprove: false,
      pricingAvailable: true,
      blockReason:
        'Thay đổi số đêm cần quản lý xét ngoại lệ và khách chấp thuận báo giá trước khi áp dụng.',
      newTotalAmount: 1_600_000,
      rooms: [
        {
          ...data.rooms[0],
          newCheckOutDate: '2026-11-24',
          newTotalAmount: 1_600_000,
          nights: data.rooms[0].nights.slice(0, 2),
        },
      ],
    });
    expect(screen.getByText('Giảm 1 đêm')).toBeTruthy();
    expect(within(screen.getByLabelText('Kết quả thu hoàn')).getByText(/800.000/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
    expect(approveBookingChange).not.toHaveBeenCalled();
  });

  it('allows a manager to request the night-count exception and then requires fresh customer consent', async () => {
    const priced = {
      ...rescheduleQuote(),
      canApprove: false,
      pricingAvailable: true,
      blockReason:
        'Thay đổi số đêm cần quản lý xét ngoại lệ và khách chấp thuận báo giá trước khi áp dụng.',
    };
    await openReschedule(priced);
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), {
      target: { value: 'Chấp thuận xét thay số đêm' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Xử lý ngoại lệ và dịch vụ/ }));
    const allowed = {
      ...priced,
      canApprove: true,
      blockReason: null,
      quoteToken: 'after-night-exception',
    };
    vi.mocked(allowRescheduleException).mockResolvedValue(allowed);
    vi.mocked(getBookingChangeQuote).mockResolvedValue(allowed);
    fireEvent.click(
      screen.getByRole('button', { name: 'Xét ngoại lệ thay đổi số đêm và báo giá lại' }),
    );
    await waitFor(() =>
      expect(allowRescheduleException).toHaveBeenCalledWith('r1', 'Chấp thuận xét thay số đêm'),
    );
    const consent = await screen.findByRole('checkbox', { name: /Đã đối chiếu khách chấp thuận/ });
    expect((consent as HTMLInputElement).checked).toBe(false);
    expect(screen.getByRole('button', { name: 'Duyệt và áp dụng' }).hasAttribute('disabled')).toBe(
      true,
    );
  });
});
