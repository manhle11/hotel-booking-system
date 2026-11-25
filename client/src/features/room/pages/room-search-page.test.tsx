// @vitest-environment jsdom

import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, Form } from 'antd';
import type { ComponentProps } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { searchAvailableRoomTypes } from '../api/available-room-search-api';
import type { AvailableRoomType } from '../types/available-room-search';
import type { RoomAvailabilityForm } from './room-availability-form';
import { RoomSearchPage } from './room-search-page';

vi.mock('../api/available-room-search-api', () => ({ searchAvailableRoomTypes: vi.fn() }));
vi.mock('@components/layout/MainHeader', () => ({ MainHeader: () => null }));
vi.mock('@components/layout/MainFooter', () => ({ MainFooter: () => null }));
vi.mock('./room-search-hero', () => ({ RoomSearchHero: () => null }));
vi.mock('./room-availability-form', () => {
  const Field = () => null;
  return {
    RoomAvailabilityForm: ({
      form,
      initialValues,
      onSearch,
    }: ComponentProps<typeof RoomAvailabilityForm>) => (
      <Form form={form} initialValues={initialValues}>
        <Form.Item name="stayDates" hidden>
          <Field />
        </Form.Item>
        <Form.Item name="adults" hidden>
          <Field />
        </Form.Item>
        <Form.Item name="children" hidden>
          <Field />
        </Form.Item>
        <button type="button" onClick={() => onSearch(initialValues)}>
          Tìm kiếm
        </button>
        <button
          type="button"
          onClick={() =>
            form.setFieldsValue({
              stayDates: [initialValues.stayDates[0], initialValues.stayDates[1].add(2, 'day')],
            })
          }
        >
          Đổi ngày chưa tìm
        </button>
      </Form>
    ),
  };
});

const room: AvailableRoomType = {
  id: 'junior-suite-id',
  code: 'JUNIOR_SUITE',
  name: 'Junior Suite',
  description: null,
  basePrice: 3_600_000,
  maxGuests: 5,
  adultCapacity: 3,
  childCapacity: 2,
  bedType: null,
  areaM2: null,
  imageUrl: null,
  availableRooms: 12,
  readyRooms: 0,
  totalNights: 1,
  roomSubtotal: 3_600_000,
  roomDiscount: 0,
  roomTotal: 3_600_000,
  nightlyPrices: [],
};

let queryClient: QueryClient;

beforeEach(() => {
  vi.clearAllMocks();
  focusManager.setFocused(true);
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
  vi.spyOn(App, 'useApp').mockReturnValue({
    message: { success: vi.fn(), info: vi.fn(), error: vi.fn() },
  } as unknown as ReturnType<typeof App.useApp>);
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
});

afterEach(() => {
  cleanup();
  queryClient.clear();
  focusManager.setFocused(undefined);
  vi.restoreAllMocks();
});

function respondWithCount(availableRooms: number, totalNights = room.totalNights) {
  vi.mocked(searchAvailableRoomTypes).mockResolvedValue({
    content: [
      {
        ...room,
        availableRooms,
        totalNights,
        roomSubtotal: room.basePrice * totalNights,
        roomTotal: room.basePrice * totalNights,
      },
    ],
    page: 0,
    size: 6,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  });
}

function renderPage(
  initialEntry = '/rooms/search?checkIn=2026-11-04&checkOut=2026-11-05&adults=2&children=0',
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <RoomSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RoomSearchPage inventory synchronization', () => {
  it('keeps explicit false feature filters when submitting URL search criteria', async () => {
    respondWithCount(3);
    renderPage(
      '/rooms/search?checkIn=2026-11-05&checkOut=2026-11-08&adults=2&children=0&viewType=CITY&hasBalcony=false&highFloor=false',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
    await waitFor(() =>
      expect(searchAvailableRoomTypes).toHaveBeenLastCalledWith(
        expect.objectContaining({ viewType: 'CITY', hasBalcony: false, highFloor: false }),
      ),
    );
  });
  it('counts calendar nights when selected dates contain different times of day', async () => {
    respondWithCount(12, 3);
    renderPage(
      '/rooms/search?checkIn=2026-11-05T17:51:00&checkOut=2026-11-08T00:00:00&adults=2&children=0',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

    expect(await screen.findByText('Còn 12 phòng')).toBeTruthy();
    expect(screen.getByText(/05\/11\/2026 - 08\/11\/2026 · 3 đêm/)).toBeTruthy();
    expect(searchAvailableRoomTypes).toHaveBeenLastCalledWith({
      checkIn: '2026-11-05',
      checkOut: '2026-11-08',
      adults: 2,
      children: 0,
      includeSoldOut: true,
      page: 0,
      size: 6,
    });
  });

  it('shows bookable inventory for the selected dates even when no rooms are ready now', async () => {
    respondWithCount(12);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

    expect(await screen.findByText('Còn 12 phòng')).toBeTruthy();
    expect(screen.queryByText('Còn 0 phòng')).toBeNull();
  });

  it('keeps sold-out room types visible and enables booking again when inventory returns', async () => {
    respondWithCount(0);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

    expect(await screen.findByText('Hết phòng')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Junior Suite' })).toBeTruthy();
    expect(screen.queryByText('Còn 0 phòng')).toBeNull();
    expect(screen.getByRole('button', { name: /Chọn phòng/ })).toHaveProperty('disabled', true);
    expect(searchAvailableRoomTypes).toHaveBeenLastCalledWith(
      expect.objectContaining({ includeSoldOut: true }),
    );

    respondWithCount(2);
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    expect(await screen.findByText('Còn 2 phòng')).toBeTruthy();
    expect(screen.queryByText('Hết phòng')).toBeNull();
    expect(screen.getByRole('button', { name: /Chọn phòng/ })).toHaveProperty('disabled', false);
  });

  it('refreshes the displayed room count when returning to the tab', async () => {
    respondWithCount(12);
    renderPage();
    expect(searchAvailableRoomTypes).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
    expect(await screen.findByText('Còn 12 phòng')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Junior Suite' }).getAttribute('href')).toBe(
      '/room-types/junior-suite-id',
    );

    respondWithCount(11);
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    expect(await screen.findByText('Còn 11 phòng')).toBeTruthy();
    expect(screen.queryByText('Còn 12 phòng')).toBeNull();
  });

  it('refreshes invalidated inventory and allows repeating the same search', async () => {
    respondWithCount(12);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
    expect(await screen.findByText('Còn 12 phòng')).toBeTruthy();

    respondWithCount(9);
    await act(() => queryClient.invalidateQueries({ queryKey: ['available-room-types'] }));
    expect(await screen.findByText('Còn 9 phòng')).toBeTruthy();

    respondWithCount(8);
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
    expect(await screen.findByText('Còn 8 phòng')).toBeTruthy();
  });

  it('keeps refreshing the submitted dates while the form has unsaved changes', async () => {
    respondWithCount(12);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
    expect(await screen.findByText('Còn 12 phòng')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Đổi ngày chưa tìm' }));
    respondWithCount(10);
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    expect(await screen.findByText('Còn 10 phòng')).toBeTruthy();
    expect(searchAvailableRoomTypes).toHaveBeenLastCalledWith({
      checkIn: '2026-11-04',
      checkOut: '2026-11-05',
      adults: 2,
      children: 0,
      includeSoldOut: true,
      page: 0,
      size: 6,
    });
    expect(screen.getByText(/04\/11\/2026 - 05\/11\/2026 · 1 đêm/)).toBeTruthy();
  });
});
