import type { RoomTypeCatalogParams } from '../types/room-type';

export const roomTypeDetailKeys = {
  all: ['room-types', 'detail'] as const,
  detail: (roomTypeId: string) => [...roomTypeDetailKeys.all, roomTypeId] as const,
};

export const roomTypeCatalogKeys = {
  all: ['room-type-catalog'] as const,
  list: (params: RoomTypeCatalogParams) => [...roomTypeCatalogKeys.all, params] as const,
};

export const roomTypeCheckoutKeys = {
  all: ['order-room-types'] as const,
  available: (checkIn: string, checkOut: string) =>
    [...roomTypeCheckoutKeys.all, checkIn, checkOut] as const,
};
