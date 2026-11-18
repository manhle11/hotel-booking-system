// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  claimBookingOrder,
  createOrderPayment,
  getBookingOrder,
  getMyBookingOrders,
  getOrderAccessToken,
} from '../api/booking-order-api';
import { BookingOrderHistorySection } from '../components/booking-order-history';
import type { BookingOrder } from '../types/booking-order';
import { BookingOrderPage } from './booking-order-page';
import { BookingPaymentPage } from './booking-payment-page';
import { createPaymentRequest, getPaymentStatus } from '../api/booking-api';
import { getPaymentResultToken } from '../utils/payment-result-storage';

let accountId: string | null = null;
vi.mock('../../authentication/providers/auth-context', () => ({
  useAuth: () => ({
    session: accountId ? { user: { id: accountId } } : null,
    isAuthenticated: accountId !== null,
  }),
}));

vi.mock('@components/layout/MainHeader', () => ({ MainHeader: () => null }));
vi.mock('@components/layout/MainFooter', () => ({ MainFooter: () => null }));
vi.mock('@components/booking/BookingSteps', () => ({ BookingSteps: () => null }));
vi.mock('../api/booking-api', () => ({ createPaymentRequest: vi.fn(), getPaymentStatus: vi.fn() }));
vi.mock('../api/booking-order-api', () => ({
  claimBookingOrder: vi.fn(),
  getBookingOrder: vi.fn(),
  getMyBookingOrders: vi.fn(),
  getOrderCancellationEligibility: vi.fn(),
  createOrderPayment: vi.fn(),
  cancelBookingOrder: vi.fn(),
  supplyOrderRefundInformation: vi.fn(),
  getOrderAccessToken: vi.fn(),
}));

const order: BookingOrder = {
  orderId: 'order-1',
  orderCode: 'ORD-TEST',
  status: 'PENDING',
  checkInDate: '2040-11-10',
  checkOutDate: '2040-11-12',
  contactName: 'Test Guest',
  contactEmail: 'test@example.test',
  bookedAmount: 4_000_000,
  additionalChargeAmount: 0,
  totalAmount: 4_000_000,
  paidAmount: 100_000,
  currency: 'VND',
  expiresAt: '2040-11-09T08:30:00Z',
  createdAt: '2040-11-09T08:15:00Z',
  rooms: [1, 2].map((index) => ({
    bookingId: `booking-${index}`,
    bookingCode: `BOOK-${index}`,
    roomTypeId: 'deluxe',
    roomTypeName: 'Deluxe',
    adults: 1,
    children: 0,
    totalAmount: 2_000_000,
    paidAmount: index === 1 ? 100_000 : 0,
    status: 'PENDING',
    roomNumber: null,
  })),
  payment: {
    paymentId: 'payment-1',
    bookingId: 'order-1',
    bookingCode: 'ORD-TEST',
    transactionRef: 'PAY-TEST',
    amount: 4_000_000,
    remainingAmount: 3_900_000,
    currency: 'VND',
    status: 'PENDING',
    paymentUrl: 'https://qr.example/qr?amount=3900000',
    expiredAt: '2040-11-09T08:30:00Z',
    bankCode: 'TEST',
    accountNumber: '12345678',
    accountName: 'TEST HOTEL',
    transferContent: 'PAY-TEST',
  },
  paymentStatus: 'PENDING',
  refundAmount: 0,
  refundStatus: null,
  refundInformationRequired: false,
};

beforeAll(() => {
  globalThis.ResizeObserver = class {
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
  accountId = null;
});
afterEach(cleanup);

function mount(
  page = <BookingOrderPage />,
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <App>
          <MemoryRouter initialEntries={['/booking-orders/order-1']}>
            <Routes>
              <Route path="/booking-orders/:orderId" element={page} />
              <Route
                path="/payments/:paymentId/result"
                element={<div>Kết quả thanh toán chênh lệch</div>}
              />
            </Routes>
          </MemoryRouter>
        </App>
      </ConfigProvider>
    </QueryClientProvider>,
  );
  return client;
}

describe('BookingOrderPage regression flows', () => {
  it('pays the whole confirmed order balance with one QR and the order access token', async () => {
    const changed: BookingOrder = {
      ...order,
      status: 'CONFIRMED',
      totalAmount: 6_000_000,
      paidAmount: 4_000_000,
      additionalChargeAmount: 2_000_000,
      paymentStatus: 'SUCCESS',
      rooms: order.rooms.map((room) => ({
        ...room,
        status: 'CONFIRMED',
        totalAmount: 3_000_000,
        paidAmount: 2_000_000,
      })),
    };
    vi.mocked(getBookingOrder).mockResolvedValue(changed);
    vi.mocked(getOrderAccessToken).mockReturnValue('group-access-token');
    vi.mocked(createOrderPayment).mockResolvedValue({
      ...changed,
      paymentStatus: 'PENDING',
      payment: {
        ...order.payment!,
        paymentId: 'whole-balance-payment',
        amount: 2_000_000,
        remainingAmount: 2_000_000,
      },
    });
    const client = mount();
    fireEvent.click(await screen.findByRole('button', { name: /Thanh toán toàn bộ 2.000.000/ }));
    await screen.findByText('Kết quả thanh toán chênh lệch');
    expect(createOrderPayment).toHaveBeenCalledExactlyOnceWith('order-1');
    expect(createPaymentRequest).not.toHaveBeenCalled();
    expect(getPaymentResultToken('whole-balance-payment')).toBe('group-access-token');
    client.clear();
  });

  it('shows only the unpaid room balances in the whole-order payment action', async () => {
    vi.mocked(getBookingOrder).mockResolvedValue({
      ...order,
      status: 'CONFIRMED',
      totalAmount: 6_000_000,
      paidAmount: 5_000_000,
      paymentStatus: 'SUCCESS',
      rooms: order.rooms.map((room, index) => ({
        ...room,
        status: 'CONFIRMED',
        totalAmount: 3_000_000,
        paidAmount: index === 0 ? 3_000_000 : 2_000_000,
      })),
    });
    const client = mount();
    await screen.findByText('ORD-TEST');
    expect(screen.getByText(/Thanh toán toàn bộ 1.000.000/).closest('button')).toBeEnabled();
    expect(screen.getAllByText('Thanh toán chênh lệch')).toHaveLength(1);
    cleanup();
    client.clear();
  });

  it('does not offer a whole-order top-up after all rooms have been paid', async () => {
    vi.mocked(getBookingOrder).mockResolvedValue({
      ...order,
      status: 'CONFIRMED',
      totalAmount: 6_000_000,
      paidAmount: 6_000_000,
      paymentStatus: 'SUCCESS',
      rooms: order.rooms.map((room) => ({
        ...room,
        status: 'CONFIRMED',
        totalAmount: 3_000_000,
        paidAmount: 3_000_000,
      })),
    });
    const client = mount();
    await screen.findByText('ORD-TEST');
    expect(screen.queryByRole('button', { name: /Thanh toán toàn bộ/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Thanh toán chênh lệch' })).toBeNull();
    client.clear();
  });

  it('saves a guest order to the signed-in account and refreshes history', async () => {
    accountId = 'customer';
    vi.mocked(getBookingOrder).mockResolvedValue({ ...order, claimable: true });
    vi.mocked(claimBookingOrder).mockResolvedValue({
      ...order,
      claimable: false,
      reviewOwner: true,
    });
    const client = mount();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    fireEvent.click(await screen.findByRole('button', { name: 'Lưu đơn vào lịch sử' }));
    await waitFor(() => expect(claimBookingOrder).toHaveBeenCalledExactlyOnceWith('order-1'));
    await waitFor(() =>
      expect(screen.queryByText('Đơn này chưa được lưu vào tài khoản')).not.toBeInTheDocument(),
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['booking-order-history'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['booking-history'] });
    client.clear();
  });

  it('keeps the save action available when claiming fails', async () => {
    accountId = 'customer';
    vi.mocked(getBookingOrder).mockResolvedValue({ ...order, claimable: true });
    vi.mocked(claimBookingOrder).mockRejectedValue(new Error('Không thể lưu đơn'));
    const client = mount();
    fireEvent.click(await screen.findByRole('button', { name: 'Lưu đơn vào lịch sử' }));
    expect(await screen.findByText('Không thể lưu đơn')).toBeInTheDocument();
    expect(screen.getByText('Đơn này chưa được lưu vào tài khoản')).toBeInTheDocument();
    client.clear();
  });

  it('asks a guest to log in before saving the order', async () => {
    vi.mocked(getBookingOrder).mockResolvedValue({ ...order, claimable: true });
    const client = mount();
    expect(await screen.findByRole('link', { name: 'Đăng nhập để lưu đơn' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(screen.queryByRole('button', { name: 'Lưu đơn vào lịch sử' })).not.toBeInTheDocument();
    expect(claimBookingOrder).not.toHaveBeenCalled();
    client.clear();
  });

  it('pays a confirmed room balance after rescheduling using the order access token', async () => {
    vi.mocked(getBookingOrder).mockResolvedValue({
      ...order,
      status: 'CONFIRMED',
      paymentStatus: 'SUCCESS',
      totalAmount: 4_800_000,
      paidAmount: 4_000_000,
      rooms: order.rooms.map((room, i) => ({
        ...room,
        status: 'CONFIRMED',
        totalAmount: i === 0 ? 2_800_000 : 2_000_000,
        paidAmount: 2_000_000,
      })),
    });
    vi.mocked(getOrderAccessToken).mockReturnValue('group-access-token');
    vi.mocked(createPaymentRequest).mockResolvedValue({
      ...order.payment!,
      paymentId: 'balance-payment',
      bookingId: 'booking-1',
      amount: 800_000,
      remainingAmount: 800_000,
    });
    mount();
    fireEvent.click(await screen.findByRole('button', { name: 'Thanh toán chênh lệch' }));
    await screen.findByText('Kết quả thanh toán chênh lệch');
    expect(createPaymentRequest).toHaveBeenCalledExactlyOnceWith('booking-1', 'group-access-token');
    expect(getPaymentResultToken('balance-payment')).toBe('group-access-token');
  });

  it('never reuses another account or guest review ownership from a fresh order cache', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { staleTime: 60_000, retry: false } },
    });
    const completed = {
      ...order,
      status: 'CONFIRMED' as const,
      payment: null,
      reviewOwner: true,
      rooms: order.rooms.map((room) => ({ ...room, status: 'CHECKED_OUT' as const })),
    };
    accountId = 'owner-account';
    vi.mocked(getBookingOrder).mockResolvedValue(completed);
    mount(<BookingOrderPage />, client);
    expect(await screen.findByText('Đánh giá sau lưu trú')).toBeInTheDocument();
    cleanup();

    accountId = 'other-account';
    vi.mocked(getBookingOrder).mockRejectedValue(new Error('Không có quyền xem đơn'));
    mount(<BookingOrderPage />, client);
    expect(await screen.findByText('Không thể xem đơn đặt phòng')).toBeInTheDocument();
    expect(screen.queryByText('Đánh giá sau lưu trú')).not.toBeInTheDocument();
    cleanup();

    accountId = null;
    vi.mocked(getBookingOrder).mockResolvedValue({ ...completed, reviewOwner: false });
    mount(<BookingOrderPage />, client);
    await screen.findByText('Đã xác nhận 2 phòng');
    expect(screen.queryByText('Đánh giá sau lưu trú')).not.toBeInTheDocument();
    expect(getBookingOrder).toHaveBeenCalledTimes(3);
    client.clear();
  });

  it('also refreshes the legacy booking payment screen from the polled remaining amount', async () => {
    vi.mocked(createPaymentRequest).mockResolvedValue({
      ...order.payment!,
      remainingAmount: 4_000_000,
      paymentUrl: 'https://qr.example/qr?amount=4000000',
    });
    vi.mocked(getPaymentStatus).mockResolvedValue({
      ...order.payment!,
      paymentStatus: 'PENDING',
      bookingStatus: 'PENDING',
      roomTypeName: 'Deluxe',
      checkInDate: order.checkInDate,
      checkOutDate: order.checkOutDate,
      adultCount: 1,
      childCount: 0,
      totalNights: 2,
      paidAmount: 100_000,
      refundStatus: null,
      refundAmount: null,
      refundReason: null,
      paidAt: null,
    });
    mount(
      <BookingPaymentPage
        booking={{
          bookingId: 'single',
          bookingCode: 'SINGLE',
          status: 'PENDING',
          totalAmount: 4_000_000,
          expiredAt: order.expiresAt,
          bookingAccessToken: 'single-token',
        }}
        request={{
          roomTypeId: 'deluxe',
          checkInDate: order.checkInDate,
          checkOutDate: order.checkOutDate,
          adults: 1,
          children: 0,
          guestInfo: {
            fullName: order.contactName,
            email: order.contactEmail,
            identityNumber: 'TEST',
          },
          addOns: [],
        }}
        addOns={[]}
        onEdit={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(
      screen
        .getAllByRole('button', { name: 'Tạo mã thanh toán' })
        .find((button) => !(button as HTMLButtonElement).disabled)!,
    );
    await waitFor(() =>
      expect(screen.getByAltText('Mã QR thanh toán PAY-TEST')).toHaveAttribute(
        'src',
        'https://qr.example/qr?amount=3900000',
      ),
    );
    expect(screen.getByText('Số tiền').closest('tr')).toHaveTextContent('3.900.000');
  });

  it('updates the QR and transfer amount after each partial payment, including a previously failed image', async () => {
    vi.mocked(getBookingOrder).mockResolvedValue(order);
    const client = mount();
    const image = await screen.findByAltText('Mã QR thanh toán PAY-TEST');
    expect(image).toHaveAttribute('src', 'https://qr.example/qr?amount=3900000');
    expect(screen.getByText('Số tiền').closest('tr')).toHaveTextContent('3.900.000');
    fireEvent.error(image);
    expect(screen.getByText('Không tải được mã QR')).toBeInTheDocument();
    await act(async () => {
      client.setQueryData(['booking-order', 'order-1', null], {
        ...order,
        paidAmount: 200_000,
        payment: {
          ...order.payment!,
          remainingAmount: 3_800_000,
          paymentUrl: 'https://qr.example/qr?amount=3800000',
        },
      });
    });
    expect(await screen.findByAltText('Mã QR thanh toán PAY-TEST')).toHaveAttribute(
      'src',
      'https://qr.example/qr?amount=3800000',
    );
    expect(screen.getByText('Số tiền').closest('tr')).toHaveTextContent('3.800.000');
  });

  it('shows the original reservation, checkout charges, actual total and payments consistently', async () => {
    vi.mocked(getBookingOrder).mockResolvedValue({
      ...order,
      status: 'CONFIRMED',
      additionalChargeAmount: 200_000,
      totalAmount: 4_200_000,
      paidAmount: 4_200_000,
      paymentStatus: 'SUCCESS',
      rooms: order.rooms.map((room, index) => ({
        ...room,
        totalAmount: index === 0 ? 2_200_000 : 2_000_000,
        paidAmount: index === 0 ? 2_200_000 : 2_000_000,
        status: index === 0 ? 'CHECKED_OUT' : 'CHECKED_IN',
      })),
    });
    mount();
    expect(await screen.findByText('Tiền đặt phòng ban đầu')).toBeInTheDocument();
    expect(screen.getByText('Tiền đặt phòng ban đầu').closest('tr')).toHaveTextContent('4.000.000');
    expect(screen.getByText('Phí phát sinh').closest('tr')).toHaveTextContent('200.000');
    expect(screen.getByText('Đã thanh toán').closest('tr')).toHaveTextContent('4.200.000');
    expect(screen.getByText('Tổng tiền thực tế').closest('.ant-statistic')).toHaveTextContent(
      '4.200.000',
    );
    expect(screen.queryByAltText('Mã QR thanh toán PAY-TEST')).toBeNull();
    expect(screen.getByText('Đã trả phòng')).toBeInTheDocument();
    expect(screen.getByText('Đã nhận phòng')).toBeInTheDocument();
  });

  it('uses the actual updated total in order history', async () => {
    vi.mocked(getMyBookingOrders).mockResolvedValue({
      content: [
        { ...order, status: 'CONFIRMED', totalAmount: 4_200_000, additionalChargeAmount: 200_000 },
      ],
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      page: 0,
      size: 6,
    });
    mount(<BookingOrderHistorySection />);
    expect(await screen.findByText(/4\.200\.000/)).toBeInTheDocument();
  });

  it.each(['EXPIRED', 'CANCELLED'] as const)(
    'hides the payment QR when the order is %s',
    async (status) => {
      vi.mocked(getBookingOrder).mockResolvedValue({ ...order, status });
      mount();
      expect(await screen.findByText('ORD-TEST')).toBeInTheDocument();
      expect(screen.queryByAltText('Mã QR thanh toán PAY-TEST')).toBeNull();
      expect(screen.queryByRole('button', { name: /Tạo mã QR/ })).toBeNull();
    },
  );
});
