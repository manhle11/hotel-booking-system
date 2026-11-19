import type { PageResponse } from '../../../types/api';
import type { BookingStatus } from '../../booking/types/booking';
import type { ArrivalProgress, ArrivalReason } from './arrival-handling';

export type ArrivalView = 'today' | 'overdue' | 'waiting' | 'action' | 'resolved';

export interface PrimaryGuestSummary {
  fullName: string;
  phone: string | null;
  email: string | null;
}

export interface ArrivalRoomType {
  id: string;
  code: string;
  name: string;
}

export interface TodayArrival {
  orderCode?: string | null;
  roomCount?: number;
  orderRoomNumber?: number | null;
  bookingId: string;
  bookingCode: string;
  primaryGuest: PrimaryGuestSummary | null;
  roomType: ArrivalRoomType;
  checkInDate: string;
  checkOutDate: string;
  adultCount: number;
  childCount: number;
  bookingStatus: BookingStatus;
  createdAt: string;
  arrivalHoldUntil?: string | null;
  arrivalHoldRequestedUntil?: string | null;
  progress?: ArrivalProgress;
  zone?: string;
  serverNow?: string;
  resolvedAt?: string | null;
}

export interface TodayArrivalsParams {
  page: number;
  size: number;
  reason?: ArrivalReason;
  resolvedFrom?: string;
  resolvedTo?: string;
}

export type TodayArrivalsPage = PageResponse<TodayArrival>;
