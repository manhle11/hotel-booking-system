import type { RoomViewType } from '../types/room-configuration';
import dayjs, { type Dayjs } from 'dayjs';
import { hotelToday } from '../../../utils/hotel-time';

export interface SearchFormValues {
  stayDates: [Dayjs, Dayjs];
  adults: number;
  children: number;
  viewType?: RoomViewType;
  hasBalcony?: boolean;
  highFloor?: boolean;
}

export const fallbackRoomImageUrl =
  'https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=900&q=80';

export function getInitialSearchValues(searchParams: URLSearchParams): SearchFormValues {
  const checkIn = parseDate(searchParams.get('checkIn'), hotelToday().add(1, 'day'));
  const checkOut = parseDate(searchParams.get('checkOut'), checkIn.add(1, 'day'));

  return {
    stayDates: [checkIn, checkOut.isAfter(checkIn) ? checkOut : checkIn.add(1, 'day')],
    adults: parseInteger(searchParams.get('adults'), 2, 1, 12),
    children: parseInteger(searchParams.get('children'), 0, 0, 8),
    ...(['CITY', 'SEA', 'COURTYARD'].includes(searchParams.get('viewType') ?? '')
      ? { viewType: searchParams.get('viewType') as RoomViewType }
      : {}),
    ...parseBooleanFilter(searchParams, 'hasBalcony'),
    ...parseBooleanFilter(searchParams, 'highFloor'),
  };
}

function parseBooleanFilter(params: URLSearchParams, key: 'hasBalcony' | 'highFloor') {
  const value = params.get(key);
  return value === 'true' || value === 'false' ? { [key]: value === 'true' } : {};
}

export function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

export function formatDisplayDate(date: Dayjs) {
  return date.format('DD/MM/YYYY');
}

function parseDate(value: string | null, fallback: Dayjs) {
  const parsed = value ? dayjs(value) : fallback;
  return parsed.isValid() ? parsed.startOf('day') : fallback.startOf('day');
}

function parseInteger(value: string | null, fallback: number, min: number, max: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= min && parsed <= max ? parsed : fallback;
}
