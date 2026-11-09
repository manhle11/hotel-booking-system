export type BedType = 'SINGLE' | 'DOUBLE' | 'QUEEN' | 'KING';
export type RoomViewType = 'COURTYARD' | 'CITY' | 'SEA';

export interface RoomTypeBed {
  bedType: BedType;
  quantity: number;
}

export interface RoomCategory {
  id: string;
  code: string;
  name: string;
}

export const BED_TYPE_LABELS: Record<BedType, string> = {
  SINGLE: 'Giường đơn',
  DOUBLE: 'Giường đôi',
  QUEEN: 'Giường Queen',
  KING: 'Giường King',
};

export const ROOM_VIEW_LABELS: Record<RoomViewType, string> = {
  COURTYARD: 'Hướng sân trong',
  CITY: 'Hướng thành phố',
  SEA: 'Hướng biển',
};
