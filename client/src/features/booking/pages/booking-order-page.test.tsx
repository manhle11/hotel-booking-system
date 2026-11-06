// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { getBookingOrder, getMyBookingOrders } from '../api/booking-order-api';
import { BookingOrderHistorySection } from '../components/booking-order-history';
import type { BookingOrder } from '../types/booking-order';
import { BookingOrderPage } from './booking-order-page';
import { BookingPaymentPage } from './booking-payment-page';
import { createPaymentRequest, getPaymentStatus } from '../api/booking-api';

vi.mock('@components/layout/MainHeader', () => ({ MainHeader: () => null }));
vi.mock('@components/layout/MainFooter', () => ({ MainFooter: () => null }));
vi.mock('@components/booking/BookingSteps', () => ({ BookingSteps: () => null }));
vi.mock('../api/booking-api', () => ({ createPaymentRequest: vi.fn(), getPaymentStatus: vi.fn() }));
vi.mock('../api/booking-order-api', () => ({
  getBookingOrder: vi.fn(),
  getMyBookingOrders: vi.fn(),
  getOrderCancellationEligibility: vi.fn(),
  createOrderPayment: vi.fn(),
  cancelBookingOrder: vi.fn(),
  supplyOrderRefundInformation: vi.fn(),
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
beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

function mount(page = <BookingOrderPage />) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <App>
          <MemoryRouter initialEntries={['/booking-orders/order-1']}>
            <Routes>
              <Route path="/booking-orders/:orderId" element={page} />
            </Routes>
          </MemoryRouter>
        </App>
      </ConfigProvider>
    </QueryClientProvider>,
  );
  return client;
}

describe('BookingOrderPage regression flows', () => {
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
      client.setQueryData(['booking-order', 'order-1'], {
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
