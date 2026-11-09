import type { RoomCategory, RoomTypeBed, RoomViewType } from './room-configuration';

export interface Amenity {
  id: string;
  name: string;
  icon: string;
}

export interface RoomTypeDetail {
  id: string;
  name: string;
  shortDescription: string | null;
  description: string | null;
  basePrice: number;
  maxGuests: number;
  capacityAdults: number;
  capacityChildren: number;
  images: string[];
  amenities: Amenity[];
  category?: RoomCategory | null;
  beds?: RoomTypeBed[];
  bedType?: string | null;
  areaM2?: number | null;
  viewType?: RoomViewType | null;
  hasBalcony?: boolean | null;
  highFloor?: boolean | null;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T;
}
