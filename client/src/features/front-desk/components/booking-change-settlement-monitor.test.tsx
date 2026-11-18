// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BookingChangeSettlementMonitor } from './booking-change-settlement-monitor';
import { BookingChangeRequestsPage } from '../pages/booking-change-requests-page';
import {
  getBookingChangeSettlement,
  listBookingChangeSettlements,
  listBookingChanges,
  type BookingChangeSettlement,
} from '../api/booking-change-api';

const auth = vi.hoisted(() => ({ roles: ['MANAGER'] }));
vi.mock('../../authentication/providers/auth-context', () => ({ useAuth: () => auth }));
vi.mock('./booking-change-review', () => ({ BookingChangeReview: () => null }));
vi.mock('../api/booking-change-api', () => ({
  getBookingChangeSettlement: vi.fn(),
  listBookingChangeSettlements: vi.fn(),
  listBookingChanges: vi.fn(),
}));
const item: BookingChangeSettlement = {
  requestId: 'change-1',
  bookingCode: 'BK-MONITOR',
  orderCode: 'ORD-MONITOR',
  roomCount: 2,
  appliedAt: '2026-11-18T06:00:00Z',
  stayStatus: 'CONFIRMED',
  checkInDate: '2026-11-25',
  checkOutDate: '2026-11-28',
  obligationAmount: 6_000_000,
  receivedAmount: 4_000_000,
  availableAmount: 4_000_000,
  outstandingAmount: 2_000_000,
  collectionStatus: 'OVERDUE',
  balanceDueAt: '2026-11-18T07:00:00Z',
  overdue: true,
  refundedAmount: 0,
  pendingRefundAmount: 0,
  unrequestedRefundAmount: 0,
  refundStatus: 'NOT_REQUIRED',
  needsReview: false,
  additionalAtApproval: 2_000_000,
  refundAtApproval: 0,
  zone: 'Asia/Ho_Chi_Minh',
  checkedAt: '2026-11-18T08:00:00Z',
  rooms: [
    {
      bookingCode: 'BK-MONITOR',
      roomTypeName: 'Deluxe',
      bookingStatus: 'CONFIRMED',
      obligationAmount: 6_000_000,
      availableAmount: 4_000_000,
      outstandingAmount: 2_000_000,
      balanceDueAt: '2026-11-18T07:00:00Z',
      needsReview: false,
    },
  ],
  refunds: [],
};
let client: QueryClient;
beforeEach(() => {
  vi.resetAllMocks();
  auth.roles = ['MANAGER'];
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
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
  vi.mocked(listBookingChangeSettlements).mockResolvedValue({
    content: [item],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  });
  vi.mocked(getBookingChangeSettlement).mockResolvedValue(item);
  vi.mocked(listBookingChanges).mockResolvedValue({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
});
afterEach(() => {
  cleanup();
  client.clear();
});
function mount(page = <BookingChangeSettlementMonitor />) {
  render(
    <QueryClientProvider client={client}>
      <App>
        <MemoryRouter>{page}</MemoryRouter>
      </App>
    </QueryClientProvider>,
  );
}

describe('giám sát thu hoàn sau đổi lịch', () => {
  it('opens the monitoring section from the cancellation/rescheduling page', async () => {
    mount(<BookingChangeRequestsPage />);
    fireEvent.click(screen.getByRole('tab', { name: 'Giám sát thu / hoàn' }));
    await screen.findByText('ORD-MONITOR');
    expect(listBookingChangeSettlements).toHaveBeenCalledWith('', 0);
    expect(screen.getByText('Quá hạn thu')).toBeTruthy();
  });

  it('shows live debt and only marks payment complete after the refreshed receipt data settles it', async () => {
    mount();
    await screen.findByText('Quá hạn thu');
    expect(screen.getByText(/Còn thiếu: 2.000.000/)).toBeTruthy();
    vi.mocked(listBookingChangeSettlements).mockResolvedValue({
      content: [
        {
          ...item,
          availableAmount: 6_000_000,
          outstandingAmount: 0,
          collectionStatus: 'PAID',
          overdue: false,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới thu / hoàn' }));
    await screen.findByText('Đã thanh toán đủ');
    expect(screen.queryByText('Quá hạn thu')).toBeNull();
  });

  it('separates pending refunds from completed refunds and exposes the banking reference in details', async () => {
    const data: BookingChangeSettlement = {
      ...item,
      collectionStatus: 'PAID',
      outstandingAmount: 0,
      overdue: false,
      refundStatus: 'PARTIALLY_REFUNDED',
      refundedAmount: 500_000,
      pendingRefundAmount: 300_000,
      refunds: [
        {
          refundId: 'refund-1',
          bookingCode: 'BK-MONITOR',
          paymentCode: 'PAY-1',
          amount: 500_000,
          status: 'SUCCESS',
          requestedAt: item.appliedAt,
          refundedAt: item.checkedAt,
          bankReference: 'BANK-REAL-REF',
          destinationProvided: true,
        },
        {
          refundId: 'refund-2',
          bookingCode: 'BK-MONITOR',
          paymentCode: 'PAY-2',
          amount: 300_000,
          status: 'PENDING',
          requestedAt: item.appliedAt,
          refundedAt: null,
          bankReference: null,
          destinationProvided: false,
        },
      ],
    };
    vi.mocked(listBookingChangeSettlements).mockResolvedValue({
      content: [data],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    vi.mocked(getBookingChangeSettlement).mockResolvedValue(data);
    mount();
    await screen.findByText('Đã hoàn một phần');
    expect(screen.getByText(/Đã hoàn: 500.000/)).toBeTruthy();
    expect(screen.getByText(/Chờ hoàn: 300.000/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Chi tiết thu / hoàn' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(await dialog.findByText('BANK-REAL-REF')).toBeTruthy();
    expect(dialog.getByText('Chưa có tài khoản nhận')).toBeTruthy();
    expect(dialog.getByRole('link', { name: 'Mở quản lý hoàn tiền' }).getAttribute('href')).toBe(
      '/payment-monitoring/refunds',
    );
  });

  it('keeps a cancelled stay distinct from its historical approved change', async () => {
    vi.mocked(getBookingChangeSettlement).mockResolvedValue({ ...item, stayStatus: 'CANCELLED' });
    mount();
    fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết thu / hoàn' }));
    await screen.findByText('Booking / đơn đã hủy sau khi đổi lịch');
    expect(screen.getByText(/Các khoản dưới đây theo quyết toán hiện tại sau hủy/)).toBeTruthy();
  });

  it('lets reception inspect but hides manager-only payment and refund actions', async () => {
    auth.roles = ['RECEPTIONIST'];
    mount();
    fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết thu / hoàn' }));
    await screen.findByText('Công nợ từng phòng');
    expect(screen.queryByRole('link', { name: 'Mở quản lý hoàn tiền' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Đối soát thanh toán' })).toBeNull();
  });

  it('searches all server pages by booking or order code', async () => {
    mount();
    const search = screen.getByLabelText('Tìm booking hoặc đơn đổi lịch');
    fireEvent.change(search, { target: { value: ' ORD-MONITOR ' } });
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
    await waitFor(() =>
      expect(listBookingChangeSettlements).toHaveBeenCalledWith('ORD-MONITOR', 0),
    );
  });

  it('does not claim payment is complete when reconciliation is required', async () => {
    vi.mocked(listBookingChangeSettlements).mockResolvedValue({
      content: [
        { ...item, needsReview: true, collectionStatus: 'REVIEW_REQUIRED', outstandingAmount: 0 },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    mount();
    await screen.findByText('Cần đối soát');
    expect(screen.queryByText('Đã thanh toán đủ')).toBeNull();
    expect(screen.getByText('Chưa xác định khoản thiếu')).toBeTruthy();
  });
});
