// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { createBookingOrder } from '../api/booking-order-api';
import { getAddOns } from '../api/catalog-api';
import { MultiRoomBookingPage } from './multi-room-booking-page';

vi.mock('@components/layout/MainHeader', () => ({ MainHeader: () => null }));
vi.mock('@components/layout/MainFooter', () => ({ MainFooter: () => null }));
vi.mock('@components/booking/BookingSteps', () => ({ BookingSteps: () => null }));
vi.mock('@features/authentication/providers/auth-context', () => ({
  useAuth: () => ({ session: null }),
}));
vi.mock('../api/catalog-api', () => ({ getAddOns: vi.fn().mockResolvedValue([]) }));
vi.mock('../api/booking-order-api', () => ({ createBookingOrder: vi.fn() }));
vi.mock('@features/room', () => ({
  searchAvailableRoomTypes: vi.fn().mockResolvedValue({
    content: [
      {
        id: 'deluxe',
        name: 'Deluxe',
        roomTotal: 2000,
        totalNights: 2,
        adultCapacity: 2,
        childCapacity: 1,
        maxGuests: 3,
        availableRooms: 2,
      },
      {
        id: 'suite',
        name: 'Suite',
        roomTotal: 3000,
        totalNights: 2,
        adultCapacity: 3,
        childCapacity: 1,
        maxGuests: 3,
        availableRooms: 1,
      },
    ],
  }),
}));

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

function mount(entry = '/bookings/new?checkIn=2026-11-10&checkOut=2026-11-12') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <App>
          <MemoryRouter initialEntries={[entry]}>
            <Routes>
              <Route path="/bookings/new" element={<MultiRoomBookingPage />} />
              <Route path="/booking-orders/:orderId" element={<div>Chi tiết đơn đã tạo</div>} />
            </Routes>
          </MemoryRouter>
        </App>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe('multi-room checkout', () => {
  it('prefills the selected recommendation including child ages', async () => {
    mount(
      '/bookings/new?checkIn=2026-12-01&checkOut=2026-12-03&roomTypeId=deluxe&adults=2&children=1&childAges=5',
    );
    expect(await screen.findByRole('spinbutton', { name: 'Tuổi trẻ em 1 phòng 1' })).toHaveValue(
      '5',
    );
  });
  it('automatically prices meal quantities, blocks invalid days and sends only booking data', async () => {
    vi.mocked(getAddOns).mockResolvedValueOnce([
      {
        id: 'breakfast',
        code: 'BREAKFAST_BUFFET',
        name: 'Buffet sáng',
        price: 250000,
        quantityUnit: 'SERVING',
      },
    ]);
    vi.mocked(createBookingOrder).mockResolvedValue({ orderId: 'meal-order' } as Awaited<
      ReturnType<typeof createBookingOrder>
    >);
    mount();
    fireEvent.change(await screen.findByRole('spinbutton', { name: 'Số phòng Deluxe' }), {
      target: { value: '1' },
    });
    await screen.findByText('Dịch vụ bổ sung cho phòng 1');
    fireEvent.click(screen.getByText('Dịch vụ bổ sung cho phòng 1'));
    const total = screen.getByRole('spinbutton', { name: 'Buffet sáng phòng 1' });
    expect(total).toHaveValue('0');
    expect(total).toHaveAttribute('readonly');
    expect(screen.queryByRole('button', { name: 'Áp dụng số suất' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Số người dùng bữa' }), {
      target: { value: '2' },
    });
    expect(total).toHaveValue('4');
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Số ngày dùng bữa' }), {
      target: { value: '4' },
    });
    expect(total).toHaveValue('0');
    expect(screen.getByRole('button', { name: 'Giữ 1 phòng và tiếp tục' })).toBeDisabled();
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Số ngày dùng bữa' }), {
      target: { value: '3' },
    });
    expect(total).toHaveValue('6');
    expect(screen.getByRole('heading', { name: /1\.502\.000/ })).toBeInTheDocument();
    // Reordering room groups must preserve each room's meal inputs and quantity.
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Số phòng Suite' }), {
      target: { value: '1' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Số phòng Deluxe' }), {
      target: { value: '2' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Số phòng Deluxe' }), {
      target: { value: '1' },
    });
    fireEvent.click(screen.getByText('Dịch vụ bổ sung cho phòng 1'));
    fireEvent.click(screen.getByText('Dịch vụ bổ sung cho phòng 2'));
    expect(screen.getByRole('spinbutton', { name: 'Buffet sáng phòng 1' })).toHaveValue('0');
    expect(screen.getByRole('spinbutton', { name: 'Buffet sáng phòng 2' })).toHaveValue('6');
    expect(screen.getAllByRole('spinbutton', { name: 'Số người dùng bữa' })[1]).toHaveValue('2');
    fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Khách UAT' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'uat@example.test' } });
    fireEvent.change(screen.getByLabelText('CCCD / giấy tờ tùy thân'), {
      target: { value: 'UAT-01' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Giữ 2 phòng và tiếp tục' }));
    await waitFor(() => expect(createBookingOrder).toHaveBeenCalledTimes(1));
    expect(vi.mocked(createBookingOrder).mock.calls[0][0].rooms).toEqual([
      { roomTypeId: 'suite', adults: 1, children: 0, addOns: [] },
      {
        roomTypeId: 'deluxe',
        adults: 1,
        children: 0,
        addOns: [{ addOnServiceId: 'breakfast', quantity: 6 }],
      },
    ]);
  });

  it('submits two Deluxe rooms and one Suite with one contact and individual occupancies', async () => {
    vi.mocked(createBookingOrder).mockResolvedValue({ orderId: 'created-order' } as Awaited<
      ReturnType<typeof createBookingOrder>
    >);
    mount();
    const deluxe = await screen.findByRole('spinbutton', { name: 'Số phòng Deluxe' });
    fireEvent.change(deluxe, { target: { value: '2' } });
    fireEvent.blur(deluxe);
    const suite = screen.getByRole('spinbutton', { name: 'Số phòng Suite' });
    fireEvent.change(suite, { target: { value: '1' } });
    fireEvent.blur(suite);
    await screen.findByText('Phòng 3 · Suite');
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Người lớn phòng 2' }), {
      target: { value: '2' },
    });
    fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Nguyễn Văn A' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'guest@example.test' } });
    fireEvent.change(screen.getByLabelText('CCCD / giấy tờ tùy thân'), {
      target: { value: 'TEST-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Giữ 3 phòng và tiếp tục' }));
    await waitFor(() => expect(createBookingOrder).toHaveBeenCalledTimes(1));
    expect(vi.mocked(createBookingOrder).mock.calls[0][0]).toEqual({
      checkInDate: '2026-11-10',
      checkOutDate: '2026-11-12',
      guestInfo: {
        fullName: 'Nguyễn Văn A',
        email: 'guest@example.test',
        identityNumber: 'TEST-123',
        phone: undefined,
      },
      rooms: [
        { roomTypeId: 'deluxe', adults: 1, children: 0, addOns: [] },
        { roomTypeId: 'deluxe', adults: 2, children: 0, addOns: [] },
        { roomTypeId: 'suite', adults: 1, children: 0, addOns: [] },
      ],
    });
    expect(await screen.findByText('Chi tiết đơn đã tạo')).toBeInTheDocument();
  });

  it('requires an age per child and accepts age zero before allowing checkout', async () => {
    mount();
    fireEvent.change(await screen.findByRole('spinbutton', { name: 'Số phòng Deluxe' }), {
      target: { value: '1' },
    });
    const childCount = screen.getByRole('spinbutton', { name: 'Trẻ em phòng 1' });
    fireEvent.change(childCount, { target: { value: '1' } });
    expect(screen.getByRole('button', { name: /Giữ 1 phòng/ })).toBeDisabled();
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Tuổi trẻ em 1 phòng 1' }), {
      target: { value: '0' },
    });
    expect(screen.getByRole('button', { name: /Giữ 1 phòng/ })).toBeEnabled();
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Tuổi trẻ em 1 phòng 1' }), {
      target: { value: '12' },
    });
    expect(screen.getByRole('button', { name: /Giữ 1 phòng/ })).toBeDisabled();
  });

  it('does not allow an order without any selected room', async () => {
    mount();
    await screen.findByRole('spinbutton', { name: 'Số phòng Deluxe' });
    expect(screen.getByRole('button', { name: /Giữ.*phòng và tiếp tục/ })).toBeDisabled();
    expect(createBookingOrder).not.toHaveBeenCalled();
  });
});
