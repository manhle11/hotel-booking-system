import type { AvailableRoomType } from '../../room/types/available-room-search';

export type BedType = 'SINGLE' | 'DOUBLE' | 'QUEEN' | 'KING';
export type ViewType = 'SEA' | 'CITY' | 'COURTYARD';
export interface RecommendationCriteria {
  checkIn?: string;
  checkOut?: string;
  adults?: number;
  children?: number;
  childAges?: number[];
  budget?: {
    amount: number;
    period: 'TOTAL_STAY' | 'PER_NIGHT' | 'AVERAGE_PER_NIGHT';
    required: boolean;
  };
  requiredBeds?: { bedType: BedType; quantity: number }[];
  bedMatch?: 'AT_LEAST' | 'EXACT';
  preferredBeds?: { bedType: BedType; quantity: number }[];
  requiredAmenities?: string[];
  preferredAmenities?: string[];
  minAreaM2?: number;
  preferredAreaM2?: number;
  requiredView?: ViewType;
  preferredView?: ViewType;
  requiredBalcony?: boolean;
  preferredBalcony?: boolean;
  requiredHighFloor?: boolean;
  preferredHighFloor?: boolean;
  economy?: boolean;
  spacious?: boolean;
  preferredCategories?: string[];
  unsupportedRequirements?: string[];
  roomCount?: number;
}

export interface RecommendedRoom {
  room: AvailableRoomType & {
    beds: { bedType: BedType; quantity: number }[];
    viewType: ViewType | null;
    hasBalcony: boolean | null;
    highFloor: boolean | null;
  };
  amenities: { id: string; name: string; icon: string }[];
  score: number | null;
  scoreBreakdown: { code: string; weight: number; value: number; explanation: string }[];
  reasons: string[];
  unmetPreferences: string[];
}

export interface RoomRecommendationResult {
  status: 'NEEDS_CLARIFICATION' | 'RECOMMENDED' | 'NO_MATCH' | 'TEMPORARILY_UNAVAILABLE';
  criteria: RecommendationCriteria;
  evaluatedAt: string;
  currency: 'VND';
  priceScope: 'ROOM_ONLY';
  scoringVersion: string;
  recommendations: RecommendedRoom[];
  questions: string[];
  rejections: { code: string; count: number; message: string }[];
  suggestions: string[];
  warnings: string[];
}
