// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, expect, it, vi } from 'vitest';
import { RoomInfo } from './RoomInfo';
import type { RoomTypeDetail } from '../../features/room/types/room-detail';

beforeAll(() => {
  window.matchMedia = vi.fn().mockImplementation((media: string) => ({
    matches: false,
    media,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
});
afterEach(cleanup);

const room: RoomTypeDetail = {
  id: 'room-type',
  name: 'Triple Mixed',
  shortDescription: null,
  description: 'Phòng ba người với giường cố định.',
  basePrice: 2800000,
  maxGuests: 3,
  capacityAdults: 3,
  capacityChildren: 0,
  images: [],
  category: { id: 'triple', code: 'TRIPLE', name: 'Triple' },
  beds: [
    { bedType: 'QUEEN', quantity: 1 },
    { bedType: 'SINGLE', quantity: 1 },
  ],
  areaM2: 38,
  viewType: 'CITY',
  hasBalcony: false,
  highFloor: true,
  amenities: [
    { id: 'wifi', name: 'Wi-Fi tốc độ cao', icon: 'wifi' },
    { id: 'tv', name: 'TV', icon: 'tv' },
  ],
};

it('shows beds, capacity, area and features separately from physical amenities', () => {
  render(<RoomInfo roomType={room} />);
  expect(screen.getByText('1 Giường Queen + 1 Giường đơn')).toBeInTheDocument();
  expect(screen.getByText('38 m²')).toBeInTheDocument();
  expect(screen.getByText('Hướng thành phố')).toBeInTheDocument();
  const amenities = within(
    screen.getByText('Tiện nghi nổi bật').closest('.ant-card')! as HTMLElement,
  );
  expect(amenities.getByText('Wi-Fi tốc độ cao')).toBeInTheDocument();
  expect(amenities.getByText('TV')).toBeInTheDocument();
  expect(amenities.queryByText('Hướng thành phố')).not.toBeInTheDocument();
  expect(amenities.queryByText('1 Giường Queen + 1 Giường đơn')).not.toBeInTheDocument();
});

it('reads legacy room information with unknown features and an absent description', () => {
  render(
    <RoomInfo
      roomType={{
        ...room,
        description: null,
        beds: [],
        bedType: null,
        viewType: null,
        hasBalcony: null,
        highFloor: null,
      }}
    />,
  );
  expect(screen.getByText('Chưa có mô tả chi tiết.')).toBeInTheDocument();
  expect(screen.queryByText('Đặc điểm phòng')).not.toBeInTheDocument();
});
