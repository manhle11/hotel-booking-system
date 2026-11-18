// @vitest-environment jsdom
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../../lib/axios';
import { getBooking, getCancellationEligibility } from '../api/booking-api';
import { CustomerReschedule } from './customer-reschedule';
import type { BookingChangeQuote } from '../../front-desk/api/booking-change-api';

vi.mock('../../../lib/axios', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));

const code = 'BK-TEST';
let approved = false;
let client: QueryClient;
let quoted: BookingChangeQuote;

function changedStay(nights = 3, feeRate = 1): BookingChangeQuote {
  const prices = [2_450_000, 2_500_000, 2_500_000];
  return {
    request: {
      id: 'change-1',
      type: 'RESCHEDULE',
      status: 'PENDING',
      bookingCode: code,
      orderCode: 'ORDER-TEST',
      reason: 'Đổi lịch',
      requestedBy: 'Khách',
      requestedAt: '2026-11-18T06:00:00Z',
      checkInDate: '2026-11-25',
      checkOutDate: `2026-11-${25 + nights}`,
      decidedBy: null,
      decidedAt: null,
      decisionNote: null,
      snapshot: null,
    },
    canApprove: false,
    pricingAvailable: true,
    blockReason:
      'Thay đổi số đêm cần quản lý xét ngoại lệ và khách chấp thuận báo giá trước khi áp dụng.',
    requiresFeeDecision: false,
    policyDescription: 'Phí theo giá đêm đầu gốc; khách chấp thuận báo giá trước khi áp dụng.',
    quoteToken: 'priced-change',
    oldTotalAmount: 14_900_000,
    newTotalAmount: 7_450_000 * nights,
    availableAmount: 14_900_000,
    feeAmount: 7_450_000 * feeRate,
    refundAmount: Math.max(14_900_000 - 7_450_000 * (nights + feeRate), 0),
    additionalAmount: Math.max(7_450_000 * (nights + feeRate) - 14_900_000, 0),
    rooms: prices.map((price, index) => ({
      bookingCode: `${code}-${index}`,
      roomTypeName: 'Deluxe',
      oldCheckInDate: '2026-11-18',
      oldCheckOutDate: '2026-11-20',
      newCheckInDate: '2026-11-25',
      newCheckOutDate: `2026-11-${25 + nights}`,
      oldTotalAmount: price * 2,
      newTotalAmount: price * nights,
      availableAmount: price * 2,
      standardFee: price * feeRate,
      originalFirstNightAmount: price,
      previousFee: 0,
      nights: Array.from({ length: nights }, (_, night) => ({
        stayDate: `2026-11-${25 + night}`,
        price,
      })),
      services: [],
      children: 0,
    })),
    zone: 'Asia/Ho_Chi_Minh',
    serverNow: '2026-11-18T06:00:00Z',
  };
}

beforeEach(() => {
  approved = false;
  quoted = changedStay();
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
  vi.mocked(apiClient.get).mockImplementation(async (url) => {
    if (url.endsWith('/customer-change-requests'))
      return {
        data: {
          data: [
            {
              ...quoted.request,
              status: approved ? 'APPROVED' : 'PENDING',
            },
          ],
        },
      };
    if (url.endsWith('/quote')) return { data: { data: quoted } };
    if (url.endsWith('/booking-policy')) return { data: { data: { legacy: false } } };
    if (url.endsWith('/cancellation'))
      return { data: { data: { canCancel: true, feeAmount: approved ? 400_000 : 0 } } };
    if (url === `/bookings/${code}`)
      return {
        data: {
          data: {
            checkInDate: approved ? '2026-11-25' : '2026-11-15',
            totalAmount: approved ? 2_800_000 : 3_000_000,
          },
        },
      };
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: {} } });
});

afterEach(() => {
  cleanup();
  client.clear();
  vi.resetAllMocks();
});

function BookingDetails() {
  const booking = useQuery({ queryKey: ['booking-detail', code], queryFn: () => getBooking(code) });
  const cancellation = useQuery({
    queryKey: ['booking-cancellation', code],
    queryFn: () => getCancellationEligibility(code),
  });
  return (
    <>
      <div>Ngày nhận: {booking.data?.checkInDate}</div>
      <div>Tổng tiền: {booking.data?.totalAmount}</div>
      <div>Phí hủy: {cancellation.data?.feeAmount}</div>
      <CustomerReschedule code={code} />
    </>
  );
}

describe('customer reschedule decisions', () => {
  it('refreshes the visible dates, total and cancellation fee when the pending request is approved', async () => {
    render(
      <QueryClientProvider client={client}>
        <App>
          <BookingDetails />
        </App>
      </QueryClientProvider>,
    );
    await screen.findByText('Ngày nhận: 2026-11-15');
    expect(screen.getByText('Tổng tiền: 3000000')).toBeTruthy();
    expect(screen.getByText('Phí hủy: 0')).toBeTruthy();
    await screen.findByText('25/11/2026 → 28/11/2026');

    approved = true;
    await act(async () => {
      await client.refetchQueries({ queryKey: ['customer-change-requests', code] });
    });

    await screen.findByText('Ngày nhận: 2026-11-25');
    await screen.findByText('Tổng tiền: 2800000');
    await screen.findByText('Phí hủy: 400000');
    expect(screen.queryByText('Ngày nhận: 2026-11-15')).toBeNull();
  });

  it.each([
    { nights: 3, rate: 1, label: 'Bạn cần thanh toán thêm', amount: '14.900.000' },
    { nights: 1, rate: 0, label: 'Bạn được hoàn', amount: '7.450.000' },
    { nights: 1, rate: 1, label: 'Không cần thu hoặc hoàn thêm', amount: '0' },
  ])(
    'automatically shows the complete amount for $nights proposed nights at fee rate $rate',
    async ({ nights, rate, label, amount }) => {
      quoted = changedStay(nights, rate);
      render(
        <QueryClientProvider client={client}>
          <App>
            <CustomerReschedule code={code} roomCount={3} />
          </App>
        </QueryClientProvider>,
      );
      const result = within(await screen.findByLabelText('Kết quả thu hoàn'));
      expect(result.getByText(label)).toBeTruthy();
      expect(result.getByText(new RegExp(amount))).toBeTruthy();
      expect(screen.getByText('Đã có báo giá dự kiến, chờ khách sạn xác nhận')).toBeTruthy();
      expect(
        screen.getByRole('button', { name: 'Tôi đồng ý báo giá này' }).hasAttribute('disabled'),
      ).toBe(true);
      expect(apiClient.post).not.toHaveBeenCalled();
      expect(apiClient.get).toHaveBeenCalledWith(
        `/bookings/${code}/customer-change-requests/change-1/quote`,
        expect.anything(),
      );
    },
  );

  it('does not show fabricated money from an unpriced legacy response even when it contains the old total', async () => {
    quoted = {
      ...changedStay(),
      pricingAvailable: false,
      newTotalAmount: 14_900_000,
      additionalAmount: 7_450_000,
      blockReason: 'Thiếu giá đêm đã chốt; cần đối soát.',
    };
    render(
      <QueryClientProvider client={client}>
        <App>
          <CustomerReschedule code={code} />
        </App>
      </QueryClientProvider>,
    );
    await screen.findByText('Chưa tính được báo giá');
    expect(screen.queryByLabelText('Kết quả thu hoàn')).toBeNull();
    expect(
      screen.getByRole('button', { name: 'Tôi đồng ý báo giá này' }).hasAttribute('disabled'),
    ).toBe(true);
  });

  it('submits consent for the exact priced quote after the hotel accepts the exception', async () => {
    quoted = { ...changedStay(), canApprove: true, blockReason: null };
    render(
      <QueryClientProvider client={client}>
        <App>
          <CustomerReschedule code={code} />
        </App>
      </QueryClientProvider>,
    );
    await screen.findByLabelText('Kết quả thu hoàn');
    fireEvent.click(screen.getByRole('button', { name: 'Tôi đồng ý báo giá này' }));
    await waitFor(() =>
      expect(apiClient.post).toHaveBeenCalledExactlyOnceWith(
        `/bookings/${code}/customer-change-requests/change-1/consent`,
        { quoteToken: 'priced-change' },
        expect.anything(),
      ),
    );
  });
});
