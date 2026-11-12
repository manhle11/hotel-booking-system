// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useArrivals } from '../api/front-desk-query';
import type { TodayArrival } from '../types/today-arrivals';
import { TodayArrivalsPage } from './today-arrivals-page';

vi.mock('../api/front-desk-query', () => ({ useArrivals: vi.fn() }));

const refetch = vi.fn();
vi.mock('../../authentication/providers/auth-context', () => ({
  useAuth: () => ({ roles: ['RECEPTIONIST'] }),
}));

const arrival: TodayArrival = {
  bookingId: '10000000-0000-0000-0000-000000000001',
  bookingCode: 'HAN-20260819-001',
  primaryGuest: {
    fullName: 'Nguyễn Văn A',
    phone: '0901234567',
    email: 'guest@example.com',
  },
  roomType: {
    id: '20000000-0000-0000-0000-000000000001',
    code: 'DLX',
    name: 'Deluxe',
  },
  checkInDate: '2026-08-19',
  checkOutDate: '2026-08-21',
  adultCount: 2,
  childCount: 1,
  bookingStatus: 'CONFIRMED',
  createdAt: '2026-08-10T08:30:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
});

afterEach(cleanup);

const queryResult = (overrides: Record<string, unknown> = {}) => ({
  data: undefined,
  error: null,
  isSuccess: true,
  isPlaceholderData: false,
  isLoading: false,
  isFetching: false,
  isError: false,
  refetch,
  ...overrides,
});

describe('TodayArrivalsPage', () => {
  it('hiển thị danh sách arrivals và format dữ liệu vận hành', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({
        data: {
          content: [arrival],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        },
      }) as never,
    );

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );

    expect(screen.getByText('HAN-20260819-001')).not.toBeNull();
    expect(screen.getByText('Nguyễn Văn A')).not.toBeNull();
    expect(screen.getByText('Deluxe')).not.toBeNull();
    expect(screen.getByText('Nhận: 19/08/2026')).not.toBeNull();
    expect(screen.getByText('Trả: 21/08/2026')).not.toBeNull();
    expect(screen.getByText('2 người lớn')).not.toBeNull();
    expect(screen.getByText('Đã xác nhận')).not.toBeNull();
    expect(useArrivals).toHaveBeenCalledWith({ page: 0, size: 20 }, 'today');
  });

  it('chuyển page UI một-based thành page API zero-based', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({
        data: {
          content: [arrival],
          page: 0,
          size: 20,
          totalElements: 21,
          totalPages: 2,
          first: true,
          last: false,
        },
      }) as never,
    );

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );
    const nextButton = document.querySelector<HTMLButtonElement>('.ant-pagination-next button');
    expect(nextButton).not.toBeNull();
    fireEvent.click(nextButton!);

    expect(useArrivals).toHaveBeenLastCalledWith({ page: 1, size: 20 }, 'today');
  });

  it('tự chuyển về trang còn dữ liệu khi trang hiện tại rỗng', () => {
    vi.mocked(useArrivals).mockImplementation(
      ({ page }) =>
        queryResult({
          data:
            page === 0
              ? {
                  content: [arrival],
                  page: 0,
                  size: 20,
                  totalElements: 21,
                  totalPages: 2,
                  first: true,
                  last: false,
                }
              : {
                  content: [],
                  page: 1,
                  size: 20,
                  totalElements: 1,
                  totalPages: 1,
                  first: false,
                  last: true,
                },
        }) as never,
    );

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );
    const nextButton = document.querySelector<HTMLButtonElement>('.ant-pagination-next button');
    fireEvent.click(nextButton!);

    expect(screen.queryByText('Hôm nay không có khách dự kiến nhận phòng')).toBeNull();
    expect(document.querySelector('.ant-table-wrapper')).not.toBeNull();
    expect(useArrivals).toHaveBeenLastCalledWith({ page: 0, size: 20 }, 'today');
  });

  it('mở nhận phòng với đúng mã booking', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({
        data: {
          content: [arrival],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        },
      }) as never,
    );
    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Nhận phòng' }).getAttribute('href')).toBe(
      '/front-desk/check-in?bookingCode=HAN-20260819-001',
    );
  });

  it('chuyển sang danh sách quá giờ và chỉ mở tra cứu booking', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({
        data: {
          content: [arrival],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        },
      }) as never,
    );
    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole('tab', { name: 'Chưa đến / quá giờ' }));
    expect(useArrivals).toHaveBeenLastCalledWith({ page: 0, size: 20 }, 'overdue');
    expect(screen.getByRole('link', { name: 'Xem booking' }).getAttribute('href')).toContain(
      'HAN-20260819-001',
    );
    expect(screen.queryByRole('link', { name: 'Nhận phòng' })).toBeNull();
  });

  it('hiển thị cảnh báo khi booking thiếu khách chính', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({
        data: {
          content: [{ ...arrival, primaryGuest: null }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        },
      }) as never,
    );

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );

    expect(screen.getByText('Thiếu thông tin khách')).not.toBeNull();
  });

  it('hiển thị empty state và cho phép làm mới', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({
        data: {
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        },
      }) as never,
    );

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );
    expect(screen.getByText('Hôm nay không có khách dự kiến nhận phòng')).not.toBeNull();

    fireEvent.click(screen.getAllByText('Làm mới')[0]);
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('hiển thị lỗi và retry request', () => {
    vi.mocked(useArrivals).mockReturnValue(
      queryResult({ isError: true, error: new Error('Máy chủ không phản hồi') }) as never,
    );

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );

    expect(screen.getByText('Không thể tải danh sách khách đến hôm nay')).not.toBeNull();
    expect(screen.getByText('Máy chủ không phản hồi')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('hiển thị loading state mà không hiện empty state', () => {
    vi.mocked(useArrivals).mockReturnValue(queryResult({ isLoading: true }) as never);

    render(
      <MemoryRouter>
        <TodayArrivalsPage />
      </MemoryRouter>,
    );

    expect(screen.queryByText('Hôm nay không có khách dự kiến nhận phòng')).toBeNull();
    expect(document.querySelector('.ant-table-wrapper')).not.toBeNull();
  });
});
