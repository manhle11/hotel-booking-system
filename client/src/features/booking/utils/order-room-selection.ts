import type { OrderRoomSelection } from '../types/booking-order';

export const MAX_ORDER_ROOMS = 10;

export function changeRoomQuantity(
  rooms: OrderRoomSelection[],
  roomTypeId: string,
  quantity: number,
): OrderRoomSelection[] {
  const existing = rooms.filter((room) => room.roomTypeId === roomTypeId);
  const other = rooms.filter((room) => room.roomTypeId !== roomTypeId);
  const count = Math.max(0, Math.min(Math.trunc(quantity), MAX_ORDER_ROOMS - other.length));
  return [
    ...other,
    ...Array.from(
      { length: count },
      (_, index) => existing[index] ?? { roomTypeId, adults: 1, children: 0, addOns: [] },
    ),
  ];
}
