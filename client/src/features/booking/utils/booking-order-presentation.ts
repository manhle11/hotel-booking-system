import type { BookingOrderStatus } from '../types/booking-order';

export const ORDER_STATUS: Record<BookingOrderStatus, { label: string; color: string }> = {
  PENDING: { label: 'Chờ thanh toán', color: 'orange' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'green' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
  EXPIRED: { label: 'Đã hết hạn', color: 'default' },
};
