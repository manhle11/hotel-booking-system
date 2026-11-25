import type { RoomViewType } from './room-configuration';
import type { ApiResponse, PageResponse } from '../../../types/api';

export interface AvailableRoomType {
  id: string;
  code: string;
  name: string;
  description: string | null;
  basePrice: number;
  maxGuests: number;
  adultCapacity: number;
  childCapacity: number;
  bedType: string | null;
  areaM2: number | null;
  imageUrl: string | null;
  availableRooms: number;
  readyRooms: number;
  totalNights: number;
  roomSubtotal: number;
  roomDiscount: number;
  roomTotal: number;
  nightlyPrices: NightlyPrice[];
}

export interface NightlyPrice {
  stayDate: string;
  price: number;
  adjustments: NightlyPriceAdjustment[];
}

export interface NightlyPriceAdjustment {
  name: string;
  ruleType: 'BASE' | 'WEEKEND' | 'HOLIDAY' | 'SEASONAL' | 'PROMOTION';
  adjustmentType: 'FIXED' | 'PERCENTAGE';
  adjustmentDirection: 'INCREASE' | 'DISCOUNT';
  adjustmentValue: number;
  amount: number;
}

export interface AvailableRoomTypeSearchParams {
  checkIn: string;
  checkOut: string;
  adults: number;
  children: number;
  roomTypeId?: string;
  viewType?: RoomViewType;
  hasBalcony?: boolean;
  highFloor?: boolean;
  includeSoldOut?: boolean;
  page: number;
  size: number;
}

export type AvailableRoomTypeSearchResponse = ApiResponse<PageResponse<AvailableRoomType>>;
